package org.example.streaming

import java.sql.Timestamp
import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
import java.util.Properties

import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.apache.spark.sql.{DataFrame, Dataset, Encoders, ForeachWriter, SparkSession}
import org.example.streaming.Data.LoginLog
import org.example.streaming.Streaming.LoginAttempt
import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkContext

object Streaming {
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


    mainDf.writeStream.foreachBatch((batchDS: Dataset[LoginAttempt], batchId: Long)  => {

      val logParquet = "src/main/resources/loginLog.parquet"
      val lastUpdateParquet = "src/main/resources/loginLog_lastUpdate.parquet"
      val updateInterval = 24*60*60*1000L


      val fs = org.apache.hadoop.fs.FileSystem.get(spark.sparkContext.hadoopConfiguration)
      val lastUpdatefileExist = fs.exists(new org.apache.hadoop.fs.Path(lastUpdateParquet))
      if(( lastUpdatefileExist && System.currentTimeMillis()-spark.read.parquet(lastUpdateParquet).as[Long].first()>updateInterval)
        || !lastUpdatefileExist) {
          //update record from oracle
          //overwrite data to parquet
          Seq(System.currentTimeMillis()).toDS().write.parquet(lastUpdateParquet)
      }


      batchDS.write.partitionBy("srcDstIp")
        .mode("append")
        .parquet(logParquet)

      val ds = spark.read.parquet(logParquet).as[LoginAttempt]
        .persist()
      if(!ds.isEmpty){

        val timeThreshold = 120L*60L*1000L
        val maxTime = ds.groupBy().agg(max(ds("recordedTime"))).collect()(0).getAs[Timestamp](0)
        maxTime.setTime(maxTime.getTime-(timeThreshold))

        val recentSrcDst = ds.filter(log=>{(log.recordedTime.getTime > maxTime.getTime)})
          .select("srcDstIp")
          .withColumnRenamed("srcDstIp", "recentSrcDstIp")
            .dropDuplicates()

        val workingDf = ds.filter(log=>{(log.recordedTime.getTime <= maxTime.getTime)})
          .join(recentSrcDst,$"srcDstIp" === $"recentSrcDstIp","leftanti")
          .drop($"recentSrcDstIp").as[LoginAttempt]

        val computeUniqueRate = workingDf.map(log => {(log.srcDstIp, Seq(log), Seq(log.credentialPair))})
          .groupByKey(_._1)
          .reduceGroups((a, b) => (a._1, a._2++b._2, a._3++b._3))
          .map(x=>(x._1, x._2._2, x._2._3.distinct.length.toFloat/x._2._3.length.toFloat, x._2._2.length))
          .as[(String, Seq[LoginAttempt], Float, Int)]

        val nonSpam = computeUniqueRate.filter(x=>{
          val attempts = x._2.sortWith((x,y)=>{(x.loginTime.getTime<y.loginTime.getTime)})
          val attemptsCount = attempts.length
          var isSpam = false
          var i =0

          if(x._3>=0.75F && x._4>10){
            if(attemptsCount>=20){
              while(i < attemptsCount-19 && !isSpam){
                if(attempts(i+19).loginTime.getTime - attempts(i).loginTime.getTime< 120L*60L*1000L ){
                  isSpam = true
                }
                i=i+1
              }
            }
            i =0
            while(i < attemptsCount-9 && !isSpam){
              if(attempts(i+9).loginTime.getTime - attempts(i).loginTime.getTime< 5L*60L*1000L ){
                isSpam = true
              }
              i=i+1
            }
          }
          !isSpam
        })

        nonSpam.show()
        // join nonspam and data from oracle
        // output result to kafka?

        ds.join(recentSrcDst,$"srcDstIp" === $"recentSrcDstIp","inner")
          .drop($"recentSrcDstIp").as[LoginAttempt]
          .write
          .partitionBy("srcDstIp")
          .mode("overwrite")
          .parquet(logParquet)

      }
      ds.unpersist(true)
    })
    .trigger(Trigger.ProcessingTime(15*1000L))
    .outputMode(OutputMode.Append())
    .option("checkpointLocation", "src/main/resources/checkpoint")
    .start().awaitTermination()
  }
}
