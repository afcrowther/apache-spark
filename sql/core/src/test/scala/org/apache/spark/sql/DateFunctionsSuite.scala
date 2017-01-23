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

package org.apache.spark.sql

import java.sql.{Date, Timestamp}
import java.text.SimpleDateFormat
import java.util.Locale

import org.apache.spark.sql.catalyst.util.DateTimeUtils
import org.apache.spark.sql.functions._
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.test.SharedSQLContext
import org.apache.spark.unsafe.types.CalendarInterval

class DateFunctionsSuite extends QueryTest with SharedSQLContext {
  import testImplicits._

  test("function current_date") {
    val df1 = Seq((1, 2), (3, 1)).toDF("a", "b")
    val d0 = DateTimeUtils.millisToDays(System.currentTimeMillis())
    val d1 = DateTimeUtils.fromJavaDate(df1.select(current_date()).collect().head.getDate(0))
    val d2 = DateTimeUtils.fromJavaDate(
      sql("""SELECT CURRENT_DATE()""").collect().head.getDate(0))
    val d3 = DateTimeUtils.millisToDays(System.currentTimeMillis())
    assert(d0 <= d1 && d1 <= d2 && d2 <= d3 && d3 - d0 <= 1)
  }

  test("function current_timestamp and now") {
    val df1 = Seq((1, 2), (3, 1)).toDF("a", "b")
    checkAnswer(df1.select(countDistinct(current_timestamp())), Row(1))

    // Execution in one query should return the same value
    checkAnswer(sql("""SELECT CURRENT_TIMESTAMP() = CURRENT_TIMESTAMP()"""), Row(true))

    // Current timestamp should return the current timestamp ...
    val before = System.currentTimeMillis
    val got = sql("SELECT CURRENT_TIMESTAMP()").collect().head.getTimestamp(0).getTime
    val after = System.currentTimeMillis
    assert(got >= before && got <= after)

    // Now alias
    checkAnswer(sql("""SELECT CURRENT_TIMESTAMP() = NOW()"""), Row(true))
  }

  val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
  val sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
  val d = new Date(sdf.parse("2015-04-08 13:10:15").getTime)
  val ts = new Timestamp(sdf.parse("2013-04-08 13:10:15").getTime)

  test("timestamp comparison with date strings") {
    val df = Seq(
      (1, Timestamp.valueOf("2015-01-01 00:00:00")),
      (2, Timestamp.valueOf("2014-01-01 00:00:00"))).toDF("i", "t")

    checkAnswer(
      df.select("t").filter($"t" <= "2014-06-01"),
      Row(Timestamp.valueOf("2014-01-01 00:00:00")) :: Nil)
  }

  test("timestamp comparison with timestamp strings with session local timezone") {
    val df = Seq(
      (1, Timestamp.valueOf("2015-12-31 16:00:00")),
      (2, Timestamp.valueOf("2016-01-01 00:00:00"))).toDF("i", "t")

    checkAnswer(
      df.select("t").filter($"t" <= "2016-01-01 00:00:00"),
      Seq(
        Row(Timestamp.valueOf("2015-12-31 16:00:00")),
        Row(Timestamp.valueOf("2016-01-01 00:00:00"))))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // $"t" string in GMT would be as follows respectively:
      // "2016-01-01 00:00:00"
      // "2016-01-01 08:00:00"
      checkAnswer(
        df.select("t").filter($"t" <= "2016-01-01 00:00:00"),
        Row(Timestamp.valueOf("2015-12-31 16:00:00")))
    }
  }

  test("date comparison with date strings") {
    val df = Seq(
      (1, Date.valueOf("2015-01-01")),
      (2, Date.valueOf("2014-01-01"))).toDF("i", "t")

    checkAnswer(
      df.select("t").filter($"t" <= "2014-06-01"),
      Row(Date.valueOf("2014-01-01")) :: Nil)


    checkAnswer(
      df.select("t").filter($"t" >= "2015"),
      Row(Date.valueOf("2015-01-01")) :: Nil)
  }

  test("date comparison with date strings with session local timezone") {
    val df = Seq(
      (1, Date.valueOf("2015-12-31")),
      (2, Date.valueOf("2016-01-01"))).toDF("i", "t")

    checkAnswer(
      df.select("t").filter($"t" <= "2016-01-01"),
      Seq(
        Row(Date.valueOf("2015-12-31")),
        Row(Date.valueOf("2016-01-01"))))

    checkAnswer(
      df.select("t").filter($"t" >= "2016"),
      Row(Date.valueOf("2016-01-01")))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // $"t" would be as follows respectively:
      // "2015-12-31"
      // "2016-01-01"
      checkAnswer(
        df.select("t").filter($"t" <= "2016-01-01"),
        Seq(
          Row(Date.valueOf("2015-12-31")),
          Row(Date.valueOf("2016-01-01"))))

      checkAnswer(
        df.select("t").filter($"t" >= "2016"),
        Row(Date.valueOf("2016-01-01")))
    }
  }

  test("date format") {
    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(date_format($"a", "y"), date_format($"b", "y"), date_format($"c", "y")),
      Row("2015", "2015", "2013"))

    checkAnswer(
      df.selectExpr("date_format(a, 'y')", "date_format(b, 'y')", "date_format(c, 'y')"),
      Row("2015", "2015", "2013"))
  }

  test("date format with session local timezone") {
    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    // The child of date_format is implicitly casted to TimestampType with session local timezone.
    //
    // +---+---------------------+-------------+---------------------+
    // |   | df                  | timestamp   | date_format         |
    // +---+---------------------+-------------+---------------------+
    // | a |                16533|1428476400000|"2015-04-08 00:00:00"|
    // | b |"2015-04-08 13:10:15"|1428523815000|"2015-04-08 13:10:15"|
    // | c |        1365451815000|1365451815000|"2013-04-08 13:10:15"|
    // +---+---------------------+-------------+---------------------+
    // Notice:
    // - a: casted timestamp 1428476400000 is 2015-04-08 00:00:00 in America/Los_Angeles
    // - b: parsed timestamp 1428523815000 is 2015-04-08 13:10:15 in America/Los_Angeles
    // - c: timestamp 1428523815000 is 2015-04-08 13:10:15 in America/Los_Angeles
    checkAnswer(
      df.select(
        date_format($"a", "yyyy-MM-dd HH:mm:ss"),
        date_format($"b", "yyyy-MM-dd HH:mm:ss"),
        date_format($"c", "yyyy-MM-dd HH:mm:ss")),
      Row("2015-04-08 00:00:00", "2015-04-08 13:10:15", "2013-04-08 13:10:15"))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+---------------------+-------------+---------------------+
      // |   | df                  | timestamp   | date_format         |
      // +---+---------------------+-------------+---------------------+
      // | a |                16533|1428451200000|"2015-04-08 00:00:00"|
      // | b |"2015-04-08 13:10:15"|1428498615000|"2015-04-08 13:10:15"|
      // | c |        1365451815000|1365451815000|"2013-04-08 20:10:15"|
      // +---+---------------------+-------------+---------------------+
      // Notice:
      // - a: casted timestamp 1428451200000 is 2015-04-08 00:00:00 in GMT
      // - b: parsed timestamp 1428498615000 is 2015-04-08 13:10:15 in GMT
      // - c: timestamp 1365451815000 is 2013-04-08 20:10:15 in GMT
      checkAnswer(
        df.select(
          date_format($"a", "yyyy-MM-dd HH:mm:ss"),
          date_format($"b", "yyyy-MM-dd HH:mm:ss"),
          date_format($"c", "yyyy-MM-dd HH:mm:ss")),
        Row("2015-04-08 00:00:00", "2015-04-08 13:10:15", "2013-04-08 20:10:15"))
    }
  }

  test("year") {
    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(year($"a"), year($"b"), year($"c")),
      Row(2015, 2015, 2013))

    checkAnswer(
      df.selectExpr("year(a)", "year(b)", "year(c)"),
      Row(2015, 2015, 2013))
  }

  test("year with session local timezone") {
    val d = new Date(sdf.parse("2015-12-31 16:00:00").getTime)
    val ts = new Timestamp(sdf.parse("2015-12-31 16:00:00").getTime)

    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    // The child of year is implicitly casted to DateType with session local timezone.
    //
    // +---+-------------+------+------+
    // |   | df          | date | year |
    // +---+-------------+------+------+
    // | a |        16800| 16800|  2015|
    // | b | "2015-12-31"| 16800|  2015|
    // | c |1451606400000| 16800|  2015|
    // +---+-------------+------+------+
    // Notice:
    // - c: timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(year($"a"), year($"b"), year($"c")),
      Row(2015, 2015, 2015))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+-------------+------+------+
      // |   | df          | date | year |
      // +---+-------------+------+------+
      // | a |         6800| 16800|  2015|
      // | b | "2015-12-31"| 16800|  2015|
      // | c |1451606400000| 16801|  2016|
      // +---+-------------+------+------+
      // Notice:
      // - c: timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(year($"a"), year($"b"), year($"c")),
        Row(2015, 2015, 2016))
    }
  }

  test("quarter") {
    val ts = new Timestamp(sdf.parse("2013-11-08 13:10:15").getTime)

    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(quarter($"a"), quarter($"b"), quarter($"c")),
      Row(2, 2, 4))

    checkAnswer(
      df.selectExpr("quarter(a)", "quarter(b)", "quarter(c)"),
      Row(2, 2, 4))
  }

  test("quarter with session local timezone") {
    val d = new Date(sdf.parse("2015-12-31 16:00:00").getTime)
    val ts = new Timestamp(sdf.parse("2015-12-31 16:00:00").getTime)

    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    // The child of quarter is implicitly casted to DateType with session local timezone.
    //
    // +---+-------------+------+---------+
    // |   | df          | date | quarter |
    // +---+-------------+------+---------+
    // | a |        16800| 16800|        4|
    // | b | "2015-12-31"| 16800|        4|
    // | c |1451606400000| 16800|        4|
    // +---+-------------+------+---------+
    // Notice:
    // - c: timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(quarter($"a"), quarter($"b"), quarter($"c")),
      Row(4, 4, 4))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+-------------+------+---------+
      // |   | df          | date | quarter |
      // +---+-------------+------+---------+
      // | a |        16800| 16800|        4|
      // | b | "2015-12-31"| 16800|        4|
      // | c |1451606400000| 16801|        1|
      // +---+-------------+------+---------+
      // Notice:
      // - c: timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(quarter($"a"), quarter($"b"), quarter($"c")),
        Row(4, 4, 1))
    }
  }

  test("month") {
    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(month($"a"), month($"b"), month($"c")),
      Row(4, 4, 4))

    checkAnswer(
      df.selectExpr("month(a)", "month(b)", "month(c)"),
      Row(4, 4, 4))
  }

  test("month with session local timezone") {
    val d = new Date(sdf.parse("2015-12-31 16:00:00").getTime)
    val ts = new Timestamp(sdf.parse("2015-12-31 16:00:00").getTime)

    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    // The child of month is implicitly casted to DateType with session local timezone.
    //
    // +---+-------------+------+-------+
    // |   | df          | date | month |
    // +---+-------------+------+-------+
    // | a |        16800| 16800|     12|
    // | b | "2015-12-31"| 16800|     12|
    // | c |1451606400000| 16800|     12|
    // +---+-------------+------+-------+
    // Notice:
    // - c: timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(month($"a"), month($"b"), month($"c")),
      Row(12, 12, 12))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+-------------+------+-------+
      // |   | df          | date | month |
      // +---+-------------+------+-------+
      // | a |        16800| 16800|     12|
      // | b | "2015-12-31"| 16800|     12|
      // | c |1451606400000| 16801|      1|
      // +---+-------------+------+-------+
      // Notice:
      // - c: timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(month($"a"), month($"b"), month($"c")),
        Row(12, 12, 1))
    }
  }

  test("dayofmonth") {
    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(dayofmonth($"a"), dayofmonth($"b"), dayofmonth($"c")),
      Row(8, 8, 8))

    checkAnswer(
      df.selectExpr("day(a)", "day(b)", "dayofmonth(c)"),
      Row(8, 8, 8))
  }

  test("dayofmonth with session local timezone") {
    val d = new Date(sdf.parse("2015-12-31 16:00:00").getTime)
    val ts = new Timestamp(sdf.parse("2015-12-31 16:00:00").getTime)

    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    // The child of datyofmonth is implicitly casted to DateType with session local timezone.
    //
    // +---+-------------+------+------------+
    // |   | df          | date | dayofmonth |
    // +---+-------------+------+------------+
    // | a |        16800| 16800|          31|
    // | b | "2015-12-31"| 16800|          31|
    // | c |1451606400000| 16800|          31|
    // +---+-------------+------+------------+
    // Notice:
    // - c: timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(dayofmonth($"a"), dayofmonth($"b"), dayofmonth($"c")),
      Row(31, 31, 31))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+-------------+------+------------+
      // |   | df          | date | dayofmonth |
      // +---+-------------+------+------------+
      // | a |        16800| 16800|          31|
      // | b | "2015-12-31"| 16800|          31|
      // | c |1451606400000| 16801|           1|
      // +---+-------------+------+------------+
      // Notice:
      // - c: timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(dayofmonth($"a"), dayofmonth($"b"), dayofmonth($"c")),
        Row(31, 31, 1))
    }
  }

  test("dayofyear") {
    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(dayofyear($"a"), dayofyear($"b"), dayofyear($"c")),
      Row(98, 98, 98))

    checkAnswer(
      df.selectExpr("dayofyear(a)", "dayofyear(b)", "dayofyear(c)"),
      Row(98, 98, 98))
  }

  test("dayofyear with session local timezone") {
    val d = new Date(sdf.parse("2015-12-31 16:00:00").getTime)
    val ts = new Timestamp(sdf.parse("2015-12-31 16:00:00").getTime)

    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    // The child of dayofyear is implicitly casted to DateType with session local timezone.
    //
    // +---+-------------+------+-----------+
    // |   | df          | date | dayofyear |
    // +---+-------------+------+-----------+
    // | a |        16800| 16800|        365|
    // | b | "2015-12-31"| 16800|        365|
    // | c |1451606400000| 16800|        365|
    // +---+-------------+------+-----------+
    // Notice:
    // - c: timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(dayofyear($"a"), dayofyear($"b"), dayofyear($"c")),
      Row(365, 365, 365))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+-------------+------+-----------+
      // |   | df          | date | dayofyear |
      // +---+-------------+------+-----------+
      // | a |        16800| 16800|        365|
      // | b | "2015-12-31"| 16800|        365|
      // | c |1451606400000| 16801|          1|
      // +---+-------------+------+-----------+
      // Notice:
      // - c: timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(dayofyear($"a"), dayofyear($"b"), dayofyear($"c")),
        Row(365, 365, 1))
    }
  }

  test("hour") {
    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(hour($"a"), hour($"b"), hour($"c")),
      Row(0, 13, 13))

    checkAnswer(
      df.selectExpr("hour(a)", "hour(b)", "hour(c)"),
      Row(0, 13, 13))
  }

  test("hour with session local timezone") {
    val d = new Date(sdf.parse("2015-12-31 16:00:00").getTime)
    val ts = new Timestamp(sdf.parse("2015-12-31 16:00:00").getTime)

    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    // The child of hour is implicitly casted to TimestampType with session local timezone.
    //
    // +---+---------------------+-------------+------+
    // |   | df                  | timestamp   | hour |
    // +---+---------------------+-------------+------+
    // | a |                16800|1451548800000|     0|
    // | b |"2015-12-31 16:00:00"|1451606400000|    16|
    // | c |        1451606400000|1451606400000|    16|
    // +---+---------------------+-------------+------+
    // Notice:
    // - a: casted timestamp 1451548800000 is 2015-12-31 00:00:00 in America/Los_Angeles
    // - b: parsed timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    // - c: timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(hour($"a"), hour($"b"), hour($"c")),
      Row(0, 16, 16))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+---------------------+-------------+------+
      // |   | df                  | timestamp   | hour |
      // +---+---------------------+-------------+------+
      // | a |                16800|1451520000000|     0|
      // | b |"2015-12-31 16:00:00"|1451577600000|    16|
      // | c |        1451606400000|1451606400000|     0|
      // +---+---------------------+-------------+------+
      // Notice:
      // - a: casted timestamp 1428451200000 is 2015-12-31 00:00:00 in GMT
      // - b: parsed timestamp 1451577600000 is 2015-12-31 16:00:00 in GMT
      // - c: timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(hour($"a"), hour($"b"), hour($"c")),
        Row(0, 16, 0))
    }
  }

  test("minute") {
    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(minute($"a"), minute($"b"), minute($"c")),
      Row(0, 10, 10))

    checkAnswer(
      df.selectExpr("minute(a)", "minute(b)", "minute(c)"),
      Row(0, 10, 10))
  }

  test("minute with session local timezone") {
    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    // The child of minute is implicitly casted to TimestampType with session local timezone.
    //
    // +---+---------------------+-------------+--------+
    // |   | df                  | timestamp   | minute |
    // +---+---------------------+-------------+--------+
    // | a |                16533|1428476400000|       0|
    // | b |"2015-04-08 13:10:15"|1428523815000|      10|
    // | c |        1365451815000|1365451815000|      10|
    // +---+---------------------+-------------+--------+
    // Notice:
    // - a: casted timestamp 1428476400000 is 2015-04-08 00:00:00 in America/Los_Angeles
    // - b: parsed timestamp 1428523815000 is 2015-04-08 13:10:15 in America/Los_Angeles
    // - c: timestamp 1365451815000 is 2015-04-08 13:10:15 in America/Los_Angeles
    checkAnswer(
      df.select(minute($"a"), minute($"b"), minute($"c")),
      Row(0, 10, 10))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+---------------------+-------------+--------+
      // |   | df                  | timestamp   | minute |
      // +---+---------------------+-------------+--------+
      // | a |                16533|1428451200000|       0|
      // | b |"2015-04-08 13:10:15"|1428498615000|      10|
      // | c |        1365451815000|1365451815000|      10|
      // +---+---------------------+-------------+--------+
      // Notice:
      // - a: casted timestamp 1428451200000 is 2015-04-08 00:00:00 in GMT
      // - b: parsed timestamp 1428498615000 is 2015-04-08 13:10:15 in GMT
      // - c: timestamp 1365451815000 is 2013-04-08 20:10:15 in GMT
      checkAnswer(
        df.select(minute($"a"), minute($"b"), minute($"c")),
        Row(0, 10, 10))
    }

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "ACT") {

      // +---+---------------------+-------------+--------+
      // |   | df                  | timestamp   | minute |
      // +---+---------------------+-------------+--------+
      // | a |                16533|1428417000000|       0|
      // | b |"2015-04-08 13:10:15"|1428464415000|      10|
      // | c |        1365451815000|1365451815000|      40|
      // +---+---------------------+-------------+--------+
      // Notice:
      // - a: casted timestamp 1428451200000 is 2015-04-08 00:00:00 in ACT
      // - b: parsed timestamp 1428498615000 is 2015-04-08 13:10:15 in ACT
      // - c: timestamp 1365451815000 is 2013-04-09 05:40:15 in ACT
      checkAnswer(
        df.select(minute($"a"), minute($"b"), minute($"c")),
        Row(0, 10, 40))

      checkAnswer(
        df.selectExpr("minute(a)", "minute(b)", "minute(c)"),
        Row(0, 10, 40))
    }
  }

  test("second") {
    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(second($"a"), second($"b"), second($"c")),
      Row(0, 15, 15))

    checkAnswer(
      df.selectExpr("second(a)", "second(b)", "second(c)"),
      Row(0, 15, 15))
  }

  test("second with session local timezone") {
    val df = Seq((d, sdf.format(d), ts)).toDF("a", "b", "c")

    // The child of second is implicitly casted to TimestampType with session local timezone.
    //
    // +---+---------------------+-------------+--------+
    // |   | df                  | timestamp   | second |
    // +---+---------------------+-------------+--------+
    // | a |                16533|1428476400000|       0|
    // | b |"2015-04-08 13:10:15"|1428523815000|      15|
    // | c |        1365451815000|1365451815000|      15|
    // +---+---------------------+-------------+--------+
    // Notice:
    // - a: casted timestamp 1428476400000 is 2015-04-08 00:00:00 in America/Los_Angeles
    // - b: parsed timestamp 1428523815000 is 2015-04-08 13:10:15 in America/Los_Angeles
    // - c: timestamp 1365451815000 is 2015-04-08 13:10:15 in America/Los_Angeles
    checkAnswer(
      df.select(second($"a"), second($"b"), second($"c")),
      Row(0, 15, 15))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+---------------------+-------------+--------+
      // |   | df                  | timestamp   | second |
      // +---+---------------------+-------------+--------+
      // | a |                16533|1428451200000|       0|
      // | b |"2015-04-08 13:10:15"|1428498615000|      15|
      // | c |        1365451815000|1365451815000|      15|
      // +---+---------------------+-------------+--------+
      // Notice:
      // - a: casted timestamp 1428451200000 is 2015-04-08 00:00:00 in GMT
      // - b: parsed timestamp 1428498615000 is 2015-04-08 13:10:15 in GMT
      // - c: timestamp 1365451815000 is 2013-04-08 20:10:15 in GMT
      checkAnswer(
        df.select(second($"a"), second($"b"), second($"c")),
        Row(0, 15, 15))
    }
  }

  test("weekofyear") {
    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    checkAnswer(
      df.select(weekofyear($"a"), weekofyear($"b"), weekofyear($"c")),
      Row(15, 15, 15))

    checkAnswer(
      df.selectExpr("weekofyear(a)", "weekofyear(b)", "weekofyear(c)"),
      Row(15, 15, 15))
  }

  test("weekofyear with session local timezone") {
    val d = new Date(sdf.parse("2016-01-03 16:00:00").getTime)
    val ts = new Timestamp(sdf.parse("2016-01-03 16:00:00").getTime)

    val df = Seq((d, sdfDate.format(d), ts)).toDF("a", "b", "c")

    // The child of weekofyear is implicitly casted to DateType with session local timezone.
    //
    // +---+-------------+------+------------+
    // |   | df          | date | weekofyear |
    // +---+-------------+------+------------+
    // | a |        16803| 16803|          53|
    // | b | "2016-01-03"| 16803|          53|
    // | c |1451865600000| 16803|          53|
    // +---+-------------+------+------------+
    // Notice:
    // - c: timestamp 1451865600000 is 2016-01-03 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(weekofyear($"a"), weekofyear($"b"), weekofyear($"c")),
      Row(53, 53, 53))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+-------------+------+------------+
      // |   | df          | date | weekofyear |
      // +---+-------------+------+------------+
      // | a |        16803| 16803|          53|
      // | b | "2016-01-03"| 16803|          53|
      // | c |1451865600000| 16804|           1|
      // +---+-------------+------+------------+
      // Notice:
      // - c: timestamp 1451865600000 is 2016-01-04 00:00:00 in GMT
      checkAnswer(
        df.select(weekofyear($"a"), weekofyear($"b"), weekofyear($"c")),
        Row(53, 53, 1))
    }
  }

  test("function date_add") {
    val st1 = "2015-06-01 12:34:56"
    val st2 = "2015-06-02 12:34:56"
    val t1 = Timestamp.valueOf(st1)
    val t2 = Timestamp.valueOf(st2)
    val s1 = "2015-06-01"
    val s2 = "2015-06-02"
    val d1 = Date.valueOf(s1)
    val d2 = Date.valueOf(s2)
    val df = Seq((t1, d1, s1, st1), (t2, d2, s2, st2)).toDF("t", "d", "s", "ss")
    checkAnswer(
      df.select(date_add(col("d"), 1)),
      Seq(Row(Date.valueOf("2015-06-02")), Row(Date.valueOf("2015-06-03"))))
    checkAnswer(
      df.select(date_add(col("t"), 3)),
      Seq(Row(Date.valueOf("2015-06-04")), Row(Date.valueOf("2015-06-05"))))
    checkAnswer(
      df.select(date_add(col("s"), 5)),
      Seq(Row(Date.valueOf("2015-06-06")), Row(Date.valueOf("2015-06-07"))))
    checkAnswer(
      df.select(date_add(col("ss"), 7)),
      Seq(Row(Date.valueOf("2015-06-08")), Row(Date.valueOf("2015-06-09"))))

    checkAnswer(df.selectExpr("DATE_ADD(null, 1)"), Seq(Row(null), Row(null)))
    checkAnswer(
      df.selectExpr("""DATE_ADD(d, 1)"""),
      Seq(Row(Date.valueOf("2015-06-02")), Row(Date.valueOf("2015-06-03"))))
  }

  test("function date_sub") {
    val st1 = "2015-06-01 12:34:56"
    val st2 = "2015-06-02 12:34:56"
    val t1 = Timestamp.valueOf(st1)
    val t2 = Timestamp.valueOf(st2)
    val s1 = "2015-06-01"
    val s2 = "2015-06-02"
    val d1 = Date.valueOf(s1)
    val d2 = Date.valueOf(s2)
    val df = Seq((t1, d1, s1, st1), (t2, d2, s2, st2)).toDF("t", "d", "s", "ss")
    checkAnswer(
      df.select(date_sub(col("d"), 1)),
      Seq(Row(Date.valueOf("2015-05-31")), Row(Date.valueOf("2015-06-01"))))
    checkAnswer(
      df.select(date_sub(col("t"), 1)),
      Seq(Row(Date.valueOf("2015-05-31")), Row(Date.valueOf("2015-06-01"))))
    checkAnswer(
      df.select(date_sub(col("s"), 1)),
      Seq(Row(Date.valueOf("2015-05-31")), Row(Date.valueOf("2015-06-01"))))
    checkAnswer(
      df.select(date_sub(col("ss"), 1)),
      Seq(Row(Date.valueOf("2015-05-31")), Row(Date.valueOf("2015-06-01"))))
    checkAnswer(
      df.select(date_sub(lit(null), 1)).limit(1), Row(null))

    checkAnswer(df.selectExpr("""DATE_SUB(d, null)"""), Seq(Row(null), Row(null)))
    checkAnswer(
      df.selectExpr("""DATE_SUB(d, 1)"""),
      Seq(Row(Date.valueOf("2015-05-31")), Row(Date.valueOf("2015-06-01"))))
  }

  test("time_add") {
    val t1 = Timestamp.valueOf("2015-07-31 23:59:59")
    val t2 = Timestamp.valueOf("2015-12-31 00:00:00")
    val d1 = Date.valueOf("2015-07-31")
    val d2 = Date.valueOf("2015-12-31")
    val i = new CalendarInterval(2, 2000000L)
    val df = Seq((1, t1, d1), (3, t2, d2)).toDF("n", "t", "d")
    checkAnswer(
      df.selectExpr(s"d + $i"),
      Seq(Row(Date.valueOf("2015-09-30")), Row(Date.valueOf("2016-02-29"))))
    checkAnswer(
      df.selectExpr(s"t + $i"),
      Seq(Row(Timestamp.valueOf("2015-10-01 00:00:01")),
        Row(Timestamp.valueOf("2016-02-29 00:00:02"))))
  }

  test("time_sub") {
    val t1 = Timestamp.valueOf("2015-10-01 00:00:01")
    val t2 = Timestamp.valueOf("2016-02-29 00:00:02")
    val d1 = Date.valueOf("2015-09-30")
    val d2 = Date.valueOf("2016-02-29")
    val i = new CalendarInterval(2, 2000000L)
    val df = Seq((1, t1, d1), (3, t2, d2)).toDF("n", "t", "d")
    checkAnswer(
      df.selectExpr(s"d - $i"),
      Seq(Row(Date.valueOf("2015-07-30")), Row(Date.valueOf("2015-12-30"))))
    checkAnswer(
      df.selectExpr(s"t - $i"),
      Seq(Row(Timestamp.valueOf("2015-07-31 23:59:59")),
        Row(Timestamp.valueOf("2015-12-31 00:00:00"))))
  }

  test("function add_months") {
    val d1 = Date.valueOf("2015-08-31")
    val d2 = Date.valueOf("2015-02-28")
    val df = Seq((1, d1), (2, d2)).toDF("n", "d")
    checkAnswer(
      df.select(add_months(col("d"), 1)),
      Seq(Row(Date.valueOf("2015-09-30")), Row(Date.valueOf("2015-03-31"))))
    checkAnswer(
      df.selectExpr("add_months(d, -1)"),
      Seq(Row(Date.valueOf("2015-07-31")), Row(Date.valueOf("2015-01-31"))))
  }

  test("function months_between") {
    val d1 = Date.valueOf("2015-07-31")
    val d2 = Date.valueOf("2015-02-16")
    val t1 = Timestamp.valueOf("2014-09-30 23:30:00")
    val t2 = Timestamp.valueOf("2015-09-16 12:00:00")
    val s1 = "2014-09-15 11:30:00"
    val s2 = "2015-10-01 00:00:00"
    val df = Seq((t1, d1, s1), (t2, d2, s2)).toDF("t", "d", "s")
    checkAnswer(df.select(months_between(col("t"), col("d"))), Seq(Row(-10.0), Row(7.0)))
    checkAnswer(df.selectExpr("months_between(t, s)"), Seq(Row(0.5), Row(-0.5)))
  }

  test("function last_day") {
    val df1 = Seq((1, "2015-07-23"), (2, "2015-07-24")).toDF("i", "d")
    val df2 = Seq((1, "2015-07-23 00:11:22"), (2, "2015-07-24 11:22:33")).toDF("i", "t")
    checkAnswer(
      df1.select(last_day(col("d"))),
      Seq(Row(Date.valueOf("2015-07-31")), Row(Date.valueOf("2015-07-31"))))
    checkAnswer(
      df2.select(last_day(col("t"))),
      Seq(Row(Date.valueOf("2015-07-31")), Row(Date.valueOf("2015-07-31"))))
  }

  test("function last_day with session local timezone") {
    val ts1 = new Timestamp(sdf.parse("2015-12-30 16:00:00").getTime)
    val ts2 = new Timestamp(sdf.parse("2015-12-31 16:00:00").getTime)

    val df = Seq((1, ts1), (2, ts2)).toDF("i", "t")

    // The child of last_day is implicitly casted to DateType with session local timezone.
    //
    // +-------------+------+----------+
    // | t           | date | last_day |
    // +-------------+------+----------+
    // |1451520000000| 16799|     16800|
    // |1451606400000| 16800|     16800|
    // +-------------+------+----------+
    // Notice:
    // - timestamp 1451520000000 is 2015-12-30 16:00:00 in America/Los_Angeles
    // - timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(last_day(col("t"))),
      Seq(Row(Date.valueOf("2015-12-31")), Row(Date.valueOf("2015-12-31"))))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +-------------+------+----------+
      // | t           | date | last_day |
      // +-------------+------+----------+
      // |1451520000000| 16800|     16800|
      // |1451606400000| 16801|     16831|
      // +-------------+------+----------+
      // Notice:
      // - timestamp 1451520000000 is 2015-12-31 00:00:00 in GMT
      // - timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(last_day(col("t"))),
        Seq(Row(Date.valueOf("2015-12-31")), Row(Date.valueOf("2016-01-31"))))
    }
  }

  test("function next_day") {
    val df1 = Seq(("mon", "2015-07-23"), ("tuesday", "2015-07-20")).toDF("dow", "d")
    val df2 = Seq(("th", "2015-07-23 00:11:22"), ("xx", "2015-07-24 11:22:33")).toDF("dow", "t")
    checkAnswer(
      df1.select(next_day(col("d"), "MONDAY")),
      Seq(Row(Date.valueOf("2015-07-27")), Row(Date.valueOf("2015-07-27"))))
    checkAnswer(
      df2.select(next_day(col("t"), "th")),
      Seq(Row(Date.valueOf("2015-07-30")), Row(Date.valueOf("2015-07-30"))))
  }

  test("function next_day with session local timezone") {
    val ts1 = new Timestamp(sdf.parse("2016-12-11 16:00:00").getTime)
    val ts2 = new Timestamp(sdf.parse("2016-12-12 16:00:00").getTime)

    val df = Seq(("mon", ts1), ("tuesday", ts2)).toDF("dow", "t")

    // The child of next_day is implicitly casted to DateType with session local timezone.
    //
    // +-------------+------+----------+
    // | t           | date | next_day |
    // +-------------+------+----------+
    // |1481500800000| 17146|     17147|
    // |1481587200000| 17147|     17154|
    // +-------------+------+----------+
    // Notice:
    // - timestamp 1481500800000 is 2016-12-11 16:00:00 in America/Los_Angeles
    // - timestamp 1481587200000 is 2016-12-12 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(next_day(col("t"), "MONDAY")),
      Seq(Row(Date.valueOf("2016-12-12")), Row(Date.valueOf("2016-12-19"))))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +-------------+------+----------+
      // | t           | date | next_day |
      // +-------------+------+----------+
      // |1481500800000| 17147|     17154|
      // |1481587200000| 17148|     17154|
      // +-------------+------+----------+
      // Notice:
      // - timestamp 1481500800000 is 2016-12-12 00:00:00 in GMT
      // - timestamp 1481587200000 is 2016-12-13 00:00:00 in GMT
      checkAnswer(
        df.select(next_day(col("t"), "MONDAY")),
        Seq(Row(Date.valueOf("2016-12-19")), Row(Date.valueOf("2016-12-19"))))
    }
  }

  test("function to_date") {
    val d1 = Date.valueOf("2015-07-22")
    val d2 = Date.valueOf("2015-07-01")
    val t1 = Timestamp.valueOf("2015-07-22 10:00:00")
    val t2 = Timestamp.valueOf("2014-12-31 23:59:59")
    val s1 = "2015-07-22 10:00:00"
    val s2 = "2014-12-31"
    val df = Seq((d1, t1, s1), (d2, t2, s2)).toDF("d", "t", "s")

    checkAnswer(
      df.select(to_date(col("t"))),
      Seq(Row(Date.valueOf("2015-07-22")), Row(Date.valueOf("2014-12-31"))))
    checkAnswer(
      df.select(to_date(col("d"))),
      Seq(Row(Date.valueOf("2015-07-22")), Row(Date.valueOf("2015-07-01"))))
    checkAnswer(
      df.select(to_date(col("s"))),
      Seq(Row(Date.valueOf("2015-07-22")), Row(Date.valueOf("2014-12-31"))))

    checkAnswer(
      df.selectExpr("to_date(t)"),
      Seq(Row(Date.valueOf("2015-07-22")), Row(Date.valueOf("2014-12-31"))))
    checkAnswer(
      df.selectExpr("to_date(d)"),
      Seq(Row(Date.valueOf("2015-07-22")), Row(Date.valueOf("2015-07-01"))))
    checkAnswer(
      df.selectExpr("to_date(s)"),
      Seq(Row(Date.valueOf("2015-07-22")), Row(Date.valueOf("2014-12-31"))))
  }

  test("function to_date with session local timezone") {
    val d = Date.valueOf("2015-12-31")
    val t = Timestamp.valueOf("2015-12-31 16:00:00")
    val s = "2015-12-31 16:00:00"

    val df = Seq((d, t, s)).toDF("d", "t", "s")

    // The child of to_date is implicitly casted to DateType with session local timezone.
    //
    // +---+---------------------+------+---------+
    // |   | df                  | date | to_date |
    // +---+---------------------+------+---------+
    // | d |                16800| 16800|    16800|
    // | t |        1451606400000| 16800|    16800|
    // | s |"2015-12-31 16:00:00"| 16800|    16800|
    // +---+---------------------+------+---------+
    // Notice:
    // - t: timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(to_date(col("d")), to_date(col("t")), to_date(col("s"))),
      Seq(Row(Date.valueOf("2015-12-31"), Date.valueOf("2015-12-31"), Date.valueOf("2015-12-31"))))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +---+---------------------+------+---------+
      // |   | df                  | date | to_date |
      // +---+---------------------+------+---------+
      // | d |                16800| 16800|    16800|
      // | t |        1451606400000| 16801|    16801|
      // | s |"2015-12-31 16:00:00"| 16800|    16800|
      // +---+---------------------+------+---------+
      // Notice:
      // - t: timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(to_date(col("d")), to_date(col("t")), to_date(col("s"))),
        Seq(
          Row(Date.valueOf("2015-12-31"), Date.valueOf("2016-01-01"), Date.valueOf("2015-12-31"))))
    }
  }

  test("function trunc") {
    val df = Seq(
      (1, Timestamp.valueOf("2015-07-22 10:00:00")),
      (2, Timestamp.valueOf("2014-12-31 00:00:00"))).toDF("i", "t")

    checkAnswer(
      df.select(trunc(col("t"), "YY")),
      Seq(Row(Date.valueOf("2015-01-01")), Row(Date.valueOf("2014-01-01"))))

    checkAnswer(
      df.selectExpr("trunc(t, 'Month')"),
      Seq(Row(Date.valueOf("2015-07-01")), Row(Date.valueOf("2014-12-01"))))
  }

  test("function trunc with session local timezone") {
    val df = Seq(
      (1, Timestamp.valueOf("2015-12-30 16:00:00")),
      (2, Timestamp.valueOf("2015-12-31 16:00:00"))).toDF("i", "t")

    // The child of trunc is implicitly casted to DateType with session local timezone.
    //
    // +-------------+------+-------+
    // | t           | date | trunc |
    // +-------------+------+-------+
    // |1451520000000| 16799|  16436|
    // |1451606400000| 16800|  16436|
    // +-------------+------+-------+
    // Notice:
    // - timestamp 1451520000000 is 2015-12-30 16:00:00 in America/Los_Angeles
    // - timestamp 1451606400000 is 2015-12-31 16:00:00 in America/Los_Angeles
    checkAnswer(
      df.select(trunc(col("t"), "YY")),
      Seq(Row(Date.valueOf("2015-01-01")), Row(Date.valueOf("2015-01-01"))))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {

      // +-------------+------+-------+
      // | t           | date | trunc |
      // +-------------+------+-------+
      // |1451520000000| 16800|  16436|
      // |1451606400000| 16801|  16801|
      // +-------------+------+-------+
      // Notice:
      // - timestamp 1451520000000 is 2015-12-31 00:00:00 in GMT
      // - timestamp 1451606400000 is 2016-01-01 00:00:00 in GMT
      checkAnswer(
        df.select(trunc(col("t"), "YY")),
        Seq(Row(Date.valueOf("2015-01-01")), Row(Date.valueOf("2016-01-01"))))
    }
  }

  test("from_unixtime") {
    val sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val fmt2 = "yyyy-MM-dd HH:mm:ss.SSS"
    val sdf2 = new SimpleDateFormat(fmt2, Locale.US)
    val fmt3 = "yy-MM-dd HH-mm-ss"
    val sdf3 = new SimpleDateFormat(fmt3, Locale.US)
    val df = Seq((1000, "yyyy-MM-dd HH:mm:ss.SSS"), (-1000, "yy-MM-dd HH-mm-ss")).toDF("a", "b")
    checkAnswer(
      df.select(from_unixtime(col("a"))),
      Seq(Row(sdf1.format(new Timestamp(1000000))), Row(sdf1.format(new Timestamp(-1000000)))))
    checkAnswer(
      df.select(from_unixtime(col("a"), fmt2)),
      Seq(Row(sdf2.format(new Timestamp(1000000))), Row(sdf2.format(new Timestamp(-1000000)))))
    checkAnswer(
      df.select(from_unixtime(col("a"), fmt3)),
      Seq(Row(sdf3.format(new Timestamp(1000000))), Row(sdf3.format(new Timestamp(-1000000)))))
    checkAnswer(
      df.selectExpr("from_unixtime(a)"),
      Seq(Row(sdf1.format(new Timestamp(1000000))), Row(sdf1.format(new Timestamp(-1000000)))))
    checkAnswer(
      df.selectExpr(s"from_unixtime(a, '$fmt2')"),
      Seq(Row(sdf2.format(new Timestamp(1000000))), Row(sdf2.format(new Timestamp(-1000000)))))
    checkAnswer(
      df.selectExpr(s"from_unixtime(a, '$fmt3')"),
      Seq(Row(sdf3.format(new Timestamp(1000000))), Row(sdf3.format(new Timestamp(-1000000)))))
  }

  test("from_unixtime with session local timezone") {
    val sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val fmt2 = "yyyy-MM-dd HH:mm:ss.SSS"
    val sdf2 = new SimpleDateFormat(fmt2, Locale.US)
    val fmt3 = "yy-MM-dd HH-mm-ss"
    val sdf3 = new SimpleDateFormat(fmt3, Locale.US)

    val df = Seq((1000, "yyyy-MM-dd HH:mm:ss.SSS"), (-1000, "yy-MM-dd HH-mm-ss")).toDF("a", "b")

    checkAnswer(
      df.selectExpr("from_unixtime(a)"),
      Seq(Row(sdf1.format(new Timestamp(1000000))), Row(sdf1.format(new Timestamp(-1000000)))))
    checkAnswer(
      df.selectExpr("from_unixtime(a, b)"),
      Seq(Row(sdf2.format(new Timestamp(1000000))), Row(sdf3.format(new Timestamp(-1000000)))))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {
      sdf1.setTimeZone(DateTimeUtils.TimeZoneGMT)
      sdf2.setTimeZone(DateTimeUtils.TimeZoneGMT)
      sdf3.setTimeZone(DateTimeUtils.TimeZoneGMT)

      checkAnswer(
        df.selectExpr("from_unixtime(a)"),
        Seq(Row(sdf1.format(new Timestamp(1000000))), Row(sdf1.format(new Timestamp(-1000000)))))
      checkAnswer(
        df.selectExpr("from_unixtime(a, b)"),
        Seq(Row(sdf2.format(new Timestamp(1000000))), Row(sdf3.format(new Timestamp(-1000000)))))
    }
  }

  test("unix_timestamp") {
    val date1 = Date.valueOf("2015-07-24")
    val date2 = Date.valueOf("2015-07-25")
    val ts1 = Timestamp.valueOf("2015-07-24 10:00:00.3")
    val ts2 = Timestamp.valueOf("2015-07-25 02:02:02.2")
    val s1 = "2015/07/24 10:00:00.5"
    val s2 = "2015/07/25 02:02:02.6"
    val ss1 = "2015-07-24 10:00:00"
    val ss2 = "2015-07-25 02:02:02"
    val fmt = "yyyy/MM/dd HH:mm:ss.S"
    val df = Seq((date1, ts1, s1, ss1), (date2, ts2, s2, ss2)).toDF("d", "ts", "s", "ss")
    checkAnswer(df.select(unix_timestamp(col("ts"))), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
    checkAnswer(df.select(unix_timestamp(col("ss"))), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
    checkAnswer(df.select(unix_timestamp(col("d"), fmt)), Seq(
      Row(date1.getTime / 1000L), Row(date2.getTime / 1000L)))
    checkAnswer(df.select(unix_timestamp(col("s"), fmt)), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
    checkAnswer(df.selectExpr("unix_timestamp(ts)"), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
    checkAnswer(df.selectExpr("unix_timestamp(ss)"), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
    checkAnswer(df.selectExpr(s"unix_timestamp(d, '$fmt')"), Seq(
      Row(date1.getTime / 1000L), Row(date2.getTime / 1000L)))
    checkAnswer(df.selectExpr(s"unix_timestamp(s, '$fmt')"), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))

    val now = sql("select unix_timestamp()").collect().head.getLong(0)
    checkAnswer(sql(s"select cast ($now as timestamp)"), Row(new java.util.Date(now * 1000)))
  }

  test("unix_timestamp with session local timezone") {
    val date = Date.valueOf("2015-12-31")
    val ts = Timestamp.valueOf("2015-12-31 16:00:00.0")
    val s = "2015/12/31 16:00:00.0"
    val ss = "2015-12-31 16:00:00"
    val fmt = "yyyy/MM/dd HH:mm:ss.S"

    val df = Seq((date, ts, s, ss)).toDF("d", "ts", "s", "ss")

    checkAnswer(
      df.select(
        unix_timestamp(col("d")),
        unix_timestamp(col("ts")),
        unix_timestamp(col("s"), fmt),
        unix_timestamp(col("ss"))),
      Row(date.getTime / 1000L, ts.getTime / 1000L, ts.getTime / 1000L, ts.getTime / 1000L))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {
      val sdf1 = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
      sdf1.setTimeZone(DateTimeUtils.TimeZoneGMT)
      val sdf2 = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S", Locale.US)
      sdf2.setTimeZone(DateTimeUtils.TimeZoneGMT)
      val sdf3 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
      sdf3.setTimeZone(DateTimeUtils.TimeZoneGMT)

      checkAnswer(
        df.select(
          unix_timestamp(col("d")),
          unix_timestamp(col("ts")),
          unix_timestamp(col("s"), fmt),
          unix_timestamp(col("ss"))),
        Row(
          sdf1.parse("2015-12-31").getTime / 1000L,
          ts.getTime / 1000L,
          sdf2.parse(s).getTime / 1000L,
          sdf3.parse(ss).getTime / 1000L))
    }
  }

  test("to_unix_timestamp") {
    val date1 = Date.valueOf("2015-07-24")
    val date2 = Date.valueOf("2015-07-25")
    val ts1 = Timestamp.valueOf("2015-07-24 10:00:00.3")
    val ts2 = Timestamp.valueOf("2015-07-25 02:02:02.2")
    val s1 = "2015/07/24 10:00:00.5"
    val s2 = "2015/07/25 02:02:02.6"
    val ss1 = "2015-07-24 10:00:00"
    val ss2 = "2015-07-25 02:02:02"
    val fmt = "yyyy/MM/dd HH:mm:ss.S"
    val df = Seq((date1, ts1, s1, ss1), (date2, ts2, s2, ss2)).toDF("d", "ts", "s", "ss")
    checkAnswer(df.selectExpr("to_unix_timestamp(ts)"), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
    checkAnswer(df.selectExpr("to_unix_timestamp(ss)"), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
    checkAnswer(df.selectExpr(s"to_unix_timestamp(d, '$fmt')"), Seq(
      Row(date1.getTime / 1000L), Row(date2.getTime / 1000L)))
    checkAnswer(df.selectExpr(s"to_unix_timestamp(s, '$fmt')"), Seq(
      Row(ts1.getTime / 1000L), Row(ts2.getTime / 1000L)))
  }

  test("to_unix_timestamp with session local timezone") {
    val date = Date.valueOf("2015-12-31")
    val ts = Timestamp.valueOf("2015-12-31 16:00:00.0")
    val s = "2015/12/31 16:00:00.0"
    val ss = "2015-12-31 16:00:00"
    val fmt = "yyyy/MM/dd HH:mm:ss.S"

    val df = Seq((date, ts, s, ss)).toDF("d", "ts", "s", "ss")

    checkAnswer(
      df.selectExpr(
        "to_unix_timestamp(d)",
        "to_unix_timestamp(ts)",
        s"to_unix_timestamp(s, '$fmt')",
        "to_unix_timestamp(ss)"),
      Row(date.getTime / 1000L, ts.getTime / 1000L, ts.getTime / 1000L, ts.getTime / 1000L))

    withSQLConf(SQLConf.SESSION_LOCAL_TIMEZONE.key -> "GMT") {
      val sdf1 = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
      sdf1.setTimeZone(DateTimeUtils.TimeZoneGMT)
      val sdf2 = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S", Locale.US)
      sdf2.setTimeZone(DateTimeUtils.TimeZoneGMT)
      val sdf3 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
      sdf3.setTimeZone(DateTimeUtils.TimeZoneGMT)

      checkAnswer(
        df.selectExpr(
          "to_unix_timestamp(d)",
          "to_unix_timestamp(ts)",
          s"to_unix_timestamp(s, '$fmt')",
          "to_unix_timestamp(ss)"),
        Row(
          sdf1.parse("2015-12-31").getTime / 1000L,
          ts.getTime / 1000L,
          sdf2.parse(s).getTime / 1000L,
          sdf3.parse(ss).getTime / 1000L))
    }
  }

  test("datediff") {
    val df = Seq(
      (Date.valueOf("2015-07-24"), Timestamp.valueOf("2015-07-24 01:00:00"),
        "2015-07-23", "2015-07-23 03:00:00"),
      (Date.valueOf("2015-07-25"), Timestamp.valueOf("2015-07-25 02:00:00"),
        "2015-07-24", "2015-07-24 04:00:00")
    ).toDF("a", "b", "c", "d")
    checkAnswer(df.select(datediff(col("a"), col("b"))), Seq(Row(0), Row(0)))
    checkAnswer(df.select(datediff(col("a"), col("c"))), Seq(Row(1), Row(1)))
    checkAnswer(df.select(datediff(col("d"), col("b"))), Seq(Row(-1), Row(-1)))
    checkAnswer(df.selectExpr("datediff(a, d)"), Seq(Row(1), Row(1)))
  }

  test("from_utc_timestamp") {
    val df = Seq(
      (Timestamp.valueOf("2015-07-24 00:00:00"), "2015-07-24 00:00:00"),
      (Timestamp.valueOf("2015-07-25 00:00:00"), "2015-07-25 00:00:00")
    ).toDF("a", "b")
    checkAnswer(
      df.select(from_utc_timestamp(col("a"), "PST")),
      Seq(
        Row(Timestamp.valueOf("2015-07-23 17:00:00")),
        Row(Timestamp.valueOf("2015-07-24 17:00:00"))))
    checkAnswer(
      df.select(from_utc_timestamp(col("b"), "PST")),
      Seq(
        Row(Timestamp.valueOf("2015-07-23 17:00:00")),
        Row(Timestamp.valueOf("2015-07-24 17:00:00"))))
  }

  test("to_utc_timestamp") {
    val df = Seq(
      (Timestamp.valueOf("2015-07-24 00:00:00"), "2015-07-24 00:00:00"),
      (Timestamp.valueOf("2015-07-25 00:00:00"), "2015-07-25 00:00:00")
    ).toDF("a", "b")
    checkAnswer(
      df.select(to_utc_timestamp(col("a"), "PST")),
      Seq(
        Row(Timestamp.valueOf("2015-07-24 07:00:00")),
        Row(Timestamp.valueOf("2015-07-25 07:00:00"))))
    checkAnswer(
      df.select(to_utc_timestamp(col("b"), "PST")),
      Seq(
        Row(Timestamp.valueOf("2015-07-24 07:00:00")),
        Row(Timestamp.valueOf("2015-07-25 07:00:00"))))
  }

}
