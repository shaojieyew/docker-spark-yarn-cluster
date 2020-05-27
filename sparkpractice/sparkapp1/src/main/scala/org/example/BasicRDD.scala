package org.example

import org.apache.spark.{HashPartitioner, SparkContext}

/**
 * A Main to run Camel with MyRouteBuilder
 */
object BasicRDD {

  def main(args: Array[String]): Unit = {
    val sc = new SparkContext("local[*]", "MySparkApp1")
    var lines = sc.textFile("/home/ysj/Desktop/Workspace/Spark-Training/practice/sparkapp1/src/main/resources/sales.csv")
    val header = lines.first
    lines = lines.filter( x => !x.equals(header))

    println("countByValue & toSeq")
    lines.map(x=>x.toString().split(",")(1))
      .countByValue()
      .toSeq.sortBy(_._1)
      .foreach(println)

    println("mapValues & reduceByKey")
    val res = lines.map(x => (x.toString().split(",")(1) , 0))
        .mapValues(x=> (1, 2) )
        .reduceByKey((x,y)=> (x._1+y._1,x._2+y._2) )
    res.foreach(println)

    println("Collect")
    res.collect().sortBy(_._1).foreach(println)


    println("Filter & Reduce Max")
    lines.map(x=>(x.toString().split(",")(0), x.toString().split(",")(6).toInt))
      .filter(x=>x._1.equals('Europe))
      .reduceByKey((x,y)=> Math.min(x,y))
      .foreach(println)


    println("FlatMap & Word Count")
    lines.filter(x=>x.contains("Europe"))
      .map(x=>(x.split(",")(0),x))
      .flatMap((x)=>(x._2.toString.split(",")))
      .countByValue()
      .foreach(println)



    println("Group by, group by, count")
    lines.map(x=>(x.split(",")(0),x))
      .partitionBy(new HashPartitioner(10))
      .groupBy(x=>x._2.split(",")(2))
      .mapValues(x => x.map(x=>(x._2.split(",")(0)))
        .groupBy(x=>x).mapValues(_.size)
      )
      .foreach(println)
  }
}

