package org.example.kafkaquery

import org.apache.spark.sql.SparkSession
import org.example.streaming.Data.LoginLog

object KafkaSparkSQL {
  def main(args: Array[String]): Unit ={
    /*
    val kafkaHost = args(0)
    val topic = args(1)
    val offset = if(args(2).length==0){"earliest"}else{args(2)}
    val kafkaQuery = args(3)
    val schema = args(4)
    val query = args(5)
    val limit = args(6).toInt
    val truncate = args(7).toBoolean
    */
   runQuery(
     Array(
       "localhost:29092",
       "credential",
       """{"credential":{"0":0,"1":0,"2":2}}""",
       "select * from kafka",
       "LoginLog",
       "select count(*) from LoginLog where password = '434365041118172' ",
       "100",
       "true")
   )
    /*
    runQuery(args)
    */
  }

  def runQuery(args: Array[String]): Unit ={
    val spark = SparkSession.builder().appName("testapp")
      .master("local[*]")
      .getOrCreate()

    val kafkaHost = args(0)
    val topic = args(1)
    val offset = if(args(2).length==0){"earliest"}else{args(2)}
    val kafkaQuery = args(3)
    val schema = args(4)
    val query = args(5)
    val limit = args(6).toInt
    val truncate = args(7).toBoolean

    spark.read.format("kafka")
      .option("kafka.bootstrap.servers",kafkaHost)
      .option("subscribe",topic)
      .option("startingOffsets",offset)
      .option("endingOffsets","latest")
      .load()
      .createTempView("kafka")
    val kafkaDf = spark.sql(kafkaQuery)

    import spark.implicits._
    val dfValue = kafkaDf.map(row=>{
      row.getAs[Array[Byte]]("value")
    })
    var kafkaRecord = false
    schema match {
      case loginLogSchema if LoginLog.getClass.getSimpleName.dropRight(1).equals(loginLogSchema) =>{
        dfValue.map(LoginLog.parseFrom).createTempView(schema)
      }
      case _ =>  {
        kafkaRecord = true
        spark.sql(kafkaQuery).show(limit, truncate = truncate)
      }
    }
    if(!kafkaRecord){
      spark.sql(query).show(limit, truncate = truncate)
    }
  }
}
