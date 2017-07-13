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

package org.apache.spark.sql.execution.vectorized;

import org.apache.arrow.vector.*;
import org.apache.arrow.vector.complex.*;
import org.apache.arrow.vector.holders.NullableVarCharHolder;

import org.apache.spark.memory.MemoryMode;
import org.apache.spark.sql.types.*;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * A column backed by Apache Arrow.
 */
public final class ArrowColumnVector extends ColumnVector {

  private ValueVector vector;
  private ValueVector.Accessor nulls;

  private NullableBitVector boolData;
  private NullableTinyIntVector byteData;
  private NullableSmallIntVector shortData;
  private NullableIntVector intData;
  private NullableBigIntVector longData;

  private NullableFloat4Vector floatData;
  private NullableFloat8Vector doubleData;
  private NullableDecimalVector decimalData;

  private NullableVarCharVector stringData;

  private NullableVarBinaryVector binaryData;

  private UInt4Vector listOffsetData;

  public ArrowColumnVector(ValueVector vector) {
    super(vector.getValueCapacity(), DataTypes.NullType, MemoryMode.OFF_HEAP);
    initialize(vector);
  }

  @Override
  public long nullsNativeAddress() {
    throw new RuntimeException("Cannot get native address for arrow column");
  }

  @Override
  public long valuesNativeAddress() {
    throw new RuntimeException("Cannot get native address for arrow column");
  }

  @Override
  public void close() {
    if (childColumns != null) {
      for (int i = 0; i < childColumns.length; i++) {
        childColumns[i].close();
      }
    }
    vector.close();
  }

  //
  // APIs dealing with nulls
  //

  @Override
  public void putNotNull(int rowId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putNull(int rowId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putNulls(int rowId, int count) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putNotNulls(int rowId, int count) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isNullAt(int rowId) {
    return nulls.isNull(rowId);
  }

  //
  // APIs dealing with Booleans
  //

  @Override
  public void putBoolean(int rowId, boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putBooleans(int rowId, int count, boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getBoolean(int rowId) {
    return boolData.getAccessor().get(rowId) == 1;
  }

  @Override
  public boolean[] getBooleans(int rowId, int count) {
    assert(dictionary == null);
    boolean[] array = new boolean[count];
    for (int i = 0; i < count; ++i) {
      array[i] = (boolData.getAccessor().get(rowId + i) == 1);
    }
    return array;
  }

  //
  // APIs dealing with Bytes
  //

  @Override
  public void putByte(int rowId, byte value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putBytes(int rowId, int count, byte value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putBytes(int rowId, int count, byte[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte getByte(int rowId) {
    return byteData.getAccessor().get(rowId);
  }

  @Override
  public byte[] getBytes(int rowId, int count) {
    assert(dictionary == null);
    byte[] array = new byte[count];
    for (int i = 0; i < count; ++i) {
      array[i] = byteData.getAccessor().get(rowId + i);
    }
    return array;
  }

  //
  // APIs dealing with Shorts
  //

  @Override
  public void putShort(int rowId, short value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putShorts(int rowId, int count, short value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putShorts(int rowId, int count, short[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public short getShort(int rowId) {
    return shortData.getAccessor().get(rowId);
  }

  @Override
  public short[] getShorts(int rowId, int count) {
    assert(dictionary == null);
    short[] array = new short[count];
    for (int i = 0; i < count; ++i) {
      array[i] = shortData.getAccessor().get(rowId + i);
    }
    return array;
  }

  //
  // APIs dealing with Ints
  //

  @Override
  public void putInt(int rowId, int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putInts(int rowId, int count, int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putInts(int rowId, int count, int[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putIntsLittleEndian(int rowId, int count, byte[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getInt(int rowId) {
    return intData.getAccessor().get(rowId);
  }

  @Override
  public int[] getInts(int rowId, int count) {
    assert(dictionary == null);
    int[] array = new int[count];
    for (int i = 0; i < count; ++i) {
      array[i] = intData.getAccessor().get(rowId + i);
    }
    return array;
  }

  @Override
  public int getDictId(int rowId) {
    throw new UnsupportedOperationException();
  }

  //
  // APIs dealing with Longs
  //

  @Override
  public void putLong(int rowId, long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putLongs(int rowId, int count, long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putLongs(int rowId, int count, long[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putLongsLittleEndian(int rowId, int count, byte[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLong(int rowId) {
    return longData.getAccessor().get(rowId);
  }

  @Override
  public long[] getLongs(int rowId, int count) {
    assert(dictionary == null);
    long[] array = new long[count];
    for (int i = 0; i < count; ++i) {
      array[i] = longData.getAccessor().get(rowId + i);
    }
    return array;
  }

  //
  // APIs dealing with floats
  //

  @Override
  public void putFloat(int rowId, float value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putFloats(int rowId, int count, float value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putFloats(int rowId, int count, float[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putFloats(int rowId, int count, byte[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public float getFloat(int rowId) {
    return floatData.getAccessor().get(rowId);
  }

  @Override
  public float[] getFloats(int rowId, int count) {
    assert(dictionary == null);
    float[] array = new float[count];
    for (int i = 0; i < count; ++i) {
      array[i] = floatData.getAccessor().get(rowId + i);
    }
    return array;
  }

  //
  // APIs dealing with doubles
  //

  @Override
  public void putDouble(int rowId, double value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putDoubles(int rowId, int count, double value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putDoubles(int rowId, int count, double[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putDoubles(int rowId, int count, byte[] src, int srcIndex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getDouble(int rowId) {
    return doubleData.getAccessor().get(rowId);
  }

  @Override
  public double[] getDoubles(int rowId, int count) {
    assert(dictionary == null);
    double[] array = new double[count];
    for (int i = 0; i < count; ++i) {
      array[i] = doubleData.getAccessor().get(rowId + i);
    }
    return array;
  }

  //
  // APIs dealing with Arrays
  //

  @Override
  public int getArrayLength(int rowId) {
    return listOffsetData.getAccessor().get(rowId + 1) - listOffsetData.getAccessor().get(rowId);
  }

  @Override
  public int getArrayOffset(int rowId) {
    return listOffsetData.getAccessor().get(rowId);
  }

  @Override
  public void putArray(int rowId, int offset, int length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void loadBytes(Array array) {
    throw new UnsupportedOperationException();
  }

  //
  // APIs dealing with Byte Arrays
  //

  @Override
  public int putByteArray(int rowId, byte[] value, int offset, int count) {
    throw new UnsupportedOperationException();
  }

  //
  // APIs dealing with Decimals
  //

  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    if (isNullAt(rowId)) return null;
    return Decimal.apply(decimalData.getAccessor().getObject(rowId), precision, scale);
  }

  @Override
  public final void putDecimal(int rowId, Decimal value, int precision) {
    throw new UnsupportedOperationException();
  }

  //
  // APIs dealing with UTF8Strings
  //

  private NullableVarCharHolder stringResult = new NullableVarCharHolder();

  @Override
  public UTF8String getUTF8String(int rowId) {
    stringData.getAccessor().get(rowId, stringResult);
    if (stringResult.isSet == 0) {
      return null;
    } else {
      return UTF8String.fromAddress(null,
        stringResult.buffer.memoryAddress() + stringResult.start,
        stringResult.end - stringResult.start);
    }
  }

  //
  // APIs dealing with Binaries
  //

  @Override
  public byte[] getBinary(int rowId) {
    return binaryData.getAccessor().getObject(rowId);
  }

  @Override
  protected void reserveInternal(int newCapacity) {
    while (vector.getValueCapacity() <= newCapacity) {
      vector.reAlloc();
    }
    capacity = vector.getValueCapacity();
  }

  private void initialize(ValueVector vector) {
    this.vector = vector;
    this.type = ArrowUtils.fromArrowField(vector.getField());
    if (vector instanceof NullableBitVector) {
      boolData = (NullableBitVector) vector;
      nulls = boolData.getAccessor();
    } else if (vector instanceof NullableTinyIntVector) {
      byteData = (NullableTinyIntVector) vector;
      nulls = byteData.getAccessor();
    } else if (vector instanceof NullableSmallIntVector) {
      shortData = (NullableSmallIntVector) vector;
      nulls = shortData.getAccessor();
    } else if (vector instanceof NullableIntVector) {
      intData = (NullableIntVector) vector;
      nulls = intData.getAccessor();
    } else if (vector instanceof NullableBigIntVector) {
      longData = (NullableBigIntVector) vector;
      nulls = longData.getAccessor();
    } else if (vector instanceof NullableFloat4Vector) {
      floatData = (NullableFloat4Vector) vector;
      nulls = floatData.getAccessor();
    } else if (vector instanceof NullableFloat8Vector) {
      doubleData = (NullableFloat8Vector) vector;
      nulls = doubleData.getAccessor();
    } else if (vector instanceof NullableDecimalVector) {
      decimalData = (NullableDecimalVector) vector;
      nulls = decimalData.getAccessor();
    } else if (vector instanceof NullableVarCharVector) {
      stringData = (NullableVarCharVector) vector;
      nulls = stringData.getAccessor();
    } else if (vector instanceof NullableVarBinaryVector) {
      binaryData = (NullableVarBinaryVector) vector;
      nulls = binaryData.getAccessor();
    } else if (vector instanceof ListVector) {
      ListVector listVector = (ListVector) vector;
      listOffsetData = listVector.getOffsetVector();
      nulls = listVector.getAccessor();

      childColumns = new ColumnVector[1];
      childColumns[0] = new ArrowColumnVector(listVector.getDataVector());
      resultArray = new Array(childColumns[0]);
    } else if (vector instanceof MapVector) {
      MapVector mapVector = (MapVector) vector;
      nulls = mapVector.getAccessor();

      childColumns = new ArrowColumnVector[mapVector.size()];
      for (int i = 0; i < childColumns.length; ++i) {
        childColumns[i] = new ArrowColumnVector(mapVector.getVectorById(i));
      }
      resultStruct = new ColumnarBatch.Row(childColumns);
    }
    numNulls = nulls.getNullCount();
    anyNullsSet = numNulls > 0;
    isConstant = true;
  }
}
