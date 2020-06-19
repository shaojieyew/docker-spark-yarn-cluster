package org.example.streamJoinBatch

import java.sql.Timestamp
import java.time.Instant

import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{OutputMode, StreamingQueryListener, Trigger}
import org.apache.spark.sql.{Dataset, Encoders, SparkSession}
import org.example.streaming.Data.LoginLog

object StreamJoinBatch {
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
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val mainDf = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:29092")
      .option("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      .option("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")
      .option("subscribe", "login")
      .option("startingOffsets", "earliest")
      .load().select("value").as[Array[Byte]].map(LoginLog.parseFrom)
      .as[LoginLog](Encoders.product[LoginLog])
      .map(wrapData).as[LoginAttempt](Encoders.product[LoginAttempt])


    val refreshRate = 60*1000L
    var lastUpdate = 0L

    val loginDs = spark
      .read.format("csv")
      .option("delimiter",";")
      .option("header","true")
      .load("src/main/resources/login.csv")
    val loginBatch = loginDs.select(loginDs.columns(0), loginDs.columns(1)).na.drop()
      .persist()

    spark.streams.addListener(new StreamingQueryListener {
      override def onQueryStarted(event: StreamingQueryListener.QueryStartedEvent): Unit = {}

      override def onQueryProgress(event: StreamingQueryListener.QueryProgressEvent): Unit = {
        if(System.currentTimeMillis()-lastUpdate>refreshRate){
          loginBatch.unpersist()
          lastUpdate = System.currentTimeMillis()
          spark.streams.active.foreach(_.stop())
        }
      }

      override def onQueryTerminated(event: StreamingQueryListener.QueryTerminatedEvent): Unit = {}
    })

    while(true) {
      mainDf.writeStream.foreachBatch((batchDS: Dataset[LoginAttempt], batchId: Long)  => {
          loginBatch.persist()
          val loginAttempts = batchDS.map(login=>login.loginLog)
          val joinedData = loginAttempts.join(loginBatch, loginAttempts.col("username") === loginBatch.col(loginBatch.columns(0)), "leftouter")
          joinedData.show(10000, truncate = false)
        })
        .trigger(Trigger.ProcessingTime(15*1000L))
        .outputMode(OutputMode.Append())
        .option("checkpointLocation", "src/main/resources/checkpoint")
        .start().awaitTermination()
    }
  }
}
