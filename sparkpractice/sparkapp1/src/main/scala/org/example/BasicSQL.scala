package org.example

import org.apache.spark.SparkContext._
import org.apache.spark.sql._
import org.apache.spark._
import org.apache.spark.util.AccumulatorV2

import scala.collection.mutable.ArrayBuffer


object BasicSQL {

  case class Country(
                      region: String,
                      country: String
                    )

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("SparkSQLApp")
      .master("local[*]")
      .getOrCreate()

    var lines = spark.sparkContext
      .textFile("/home/ysj/Desktop/Workspace/Spark-Training/practice/sparkapp1/src/main/resources/sales.csv")
    val header = lines.first
    lines = lines.filter( x => !x.equals(header))
    val countriesRdd = lines.map(line=>Country(line.split(",")(0),line.split(",")(1)));

    //need to specify implicits to convert rdd to DS
    import spark.implicits._
    val countriesDS = countriesRdd.toDS()
    countriesDS.show(1)


    val ds= spark.read
        .format("csv")
        .option("header", true)
        .load("/home/ysj/Desktop/Workspace/Spark-Training/practice/sparkapp1/src/main/resources/sales.csv")
      .as[Country]
    ds.show(1)


    // sql query
    ds.printSchema()
    ds.createTempView("Table1")
    val countries = spark.sql("Select distinct Country from Table1")
    countries.show()

    // function query
    ds.printSchema()
    ds.select("Region", "Country")
      .filter(x=>x.getAs[String]("Region").equals("Australia and Oceania"))
      .distinct()
      .show()

    spark.close()
  }

}

