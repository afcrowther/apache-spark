/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.spark.sql.execution.python

import java.io.File

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.{SparkEnv, TaskContext}
import org.apache.spark.api.python.{ChainedPythonFunctions, PythonEvalType, PythonRunner}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.execution.arrow.{ArrowConverters, ArrowPayload}
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.types.{DataType, StructField, StructType}
import org.apache.spark.util.Utils


/**
 * A physical plan that evaluates a [[PythonUDF]],
 */
case class ArrowEvalPythonExec(udfs: Seq[PythonUDF], output: Seq[Attribute], child: SparkPlan)
  extends SparkPlan {

  def children: Seq[SparkPlan] = child :: Nil

  override def producedAttributes: AttributeSet = AttributeSet(output.drop(child.output.length))

  private def collectFunctions(udf: PythonUDF): (ChainedPythonFunctions, Seq[Expression]) = {
    udf.children match {
      case Seq(u: PythonUDF) =>
        val (chained, children) = collectFunctions(u)
        (ChainedPythonFunctions(chained.funcs ++ Seq(udf.func)), children)
      case children =>
        // There should not be any other UDFs, or the children can't be evaluated directly.
        assert(children.forall(_.find(_.isInstanceOf[PythonUDF]).isEmpty))
        (ChainedPythonFunctions(Seq(udf.func)), udf.children)
    }
  }

  protected override def doExecute(): RDD[InternalRow] = {
    val inputRDD = child.execute().map(_.copy())
    val bufferSize = inputRDD.conf.getInt("spark.buffer.size", 65536)
    val reuseWorker = inputRDD.conf.getBoolean("spark.python.worker.reuse", defaultValue = true)

    inputRDD.mapPartitions { iter =>

      // The queue used to buffer input rows so we can drain it to
      // combine input with output from Python.
      val queue = HybridRowQueue(TaskContext.get().taskMemoryManager(),
        new File(Utils.getLocalDir(SparkEnv.get.conf)), child.output.length)
      TaskContext.get().addTaskCompletionListener({ ctx =>
        queue.close()
      })

      val (pyFuncs, inputs) = udfs.map(collectFunctions).unzip

      // flatten all the arguments
      val allInputs = new ArrayBuffer[Expression]
      val dataTypes = new ArrayBuffer[DataType]
      val argOffsets = inputs.map { input =>
        input.map { e =>
          if (allInputs.exists(_.semanticEquals(e))) {
            allInputs.indexWhere(_.semanticEquals(e))
          } else {
            allInputs += e
            dataTypes += e.dataType
            allInputs.length - 1
          }
        }.toArray
      }.toArray
      val projection = newMutableProjection(allInputs, child.output)
      val schema = StructType(dataTypes.zipWithIndex.map { case (dt, i) =>
        StructField(s"_$i", dt)
      })

      // Input iterator to Python: input rows are grouped so we send them in batches to Python.
      // For each row, add it to the queue.
      val projectedRowIter = iter.map { inputRow =>
        queue.add(inputRow.asInstanceOf[UnsafeRow])
        projection(inputRow)
      }

      val inputIterator = ArrowConverters.toPayloadIterator(
          projectedRowIter, schema, conf.arrowMaxRecordsPerBatch).
        map(_.asPythonSerializable)

      val context = TaskContext.get()

      // Output iterator for results from Python.
      val outputIterator = new PythonRunner(
          pyFuncs, bufferSize, reuseWorker, PythonEvalType.SQL_ARROW_UDF, argOffsets).
        compute(inputIterator, context.partitionId(), context)

      val joined = new JoinedRow
      val resultProj = UnsafeProjection.create(output, output)

      val outputRowIterator = ArrowConverters.fromPayloadIterator(
        outputIterator.map(ArrowPayload(_)))

      outputRowIterator.map { outputRow =>
        resultProj(joined(queue.remove(), outputRow))
      }
    }
  }
}
