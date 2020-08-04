package org.example.streaming

import java.sql.Timestamp
import java.time.Instant

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.apache.spark.sql.{Dataset, Encoders, SparkSession}
import org.example.streaming.Data.LoginLog

import scala.util.control.Exception

object StreamingAccumulative {
  case class LoginAttempt(loginLog: LoginLog, srcDstIp:String, credentialPair: String, loginTime: Timestamp, recordedTime: Timestamp)

  val wrapData = (loginLog: LoginLog) => {
    LoginAttempt(
      loginLog,
      loginLog.srcIp+loginLog.dstIp,
      loginLog.username+loginLog.password,
      Timestamp.from(Instant
        .ofEpochSecond( loginLog.loginTimestamp.get.seconds , loginLog.loginTimestamp.get.nanos )),
      Timestamp.from(Instant
        .ofEpochSecond( loginLog.recordedTimestamp.get.seconds , loginLog.recordedTimestamp.get.nanos ))
    )
  }: LoginAttempt


  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("SparkSQLApp")
      .master("local[1]")
      .getOrCreate()
    import spark.implicits._

    val mainDf = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      .option("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")
      .option("subscribe", "credential")
      .option("startingOffsets", "earliest")
      .load().select("value").as[Array[Byte]].map(LoginLog.parseFrom)
      .as[LoginLog](Encoders.product[LoginLog])
      .map(wrapData).as[LoginAttempt](Encoders.product[LoginAttempt])

    val checkpointDir = "src/main/resources/loginAttempt.checkpoint"
    val kafkaCheckpoint = "src/main/resources/checkpoint"
    val loginParquet = "src/main/resources/loginAttempt.parquet"

    spark.sparkContext.setCheckpointDir(checkpointDir)
    var holdingDf = spark.createDataFrame(Seq[LoginAttempt]()).as[LoginAttempt]
    val fs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
    if(fs.exists(new Path(checkpointDir))){
      fs.delete(new Path(checkpointDir), true)
    }
    if(fs.exists(new Path(loginParquet))){
      holdingDf = spark.read.parquet(loginParquet).as[LoginAttempt]
    }
    println("TOTAL COUNT :"+holdingDf.count())

    mainDf.writeStream.foreachBatch((batchDS: Dataset[LoginAttempt], batchId: Long)  => {
      batchDS.show()
      holdingDf = holdingDf.union(batchDS).checkpoint(true)
      holdingDf.write.mode("overwrite").parquet(loginParquet)
      println("TOTAL COUNT :"+holdingDf.count())
    })
      .trigger(Trigger.ProcessingTime(10*1000L))
      .outputMode(OutputMode.Append())
      .option("checkpointLocation", kafkaCheckpoint)
      .start().awaitTermination()
  }
}
