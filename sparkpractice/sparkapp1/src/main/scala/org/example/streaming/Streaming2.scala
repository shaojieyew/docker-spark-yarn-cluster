package org.example.streaming

import java.sql.Timestamp
import java.time.Instant

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.log4j.{Level, LogManager}
import org.apache.spark.sql.functions.max
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.apache.spark.sql.{Dataset, Encoders, SparkSession}
import org.example.streaming.Data.LoginLog
import org.apache.spark.storage.StorageLevel

object Streaming2 {
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

    val checkpointDir = "src/main/resources/loginAttempt.checkpoint"
    val kafkaCheckpoint = "src/main/resources/checkpoint"
    val loginParquet = "src/main/resources/loginAttempt.parquet"

    spark.sparkContext.setCheckpointDir(checkpointDir)
    spark.sparkContext.setLogLevel(Level.WARN.toString)
    LogManager.getRootLogger.setLevel(Level.WARN)
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

    var holdingDf = spark.createDataFrame(Seq[LoginAttempt]()).as[LoginAttempt]
    val fs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
    if(fs.exists(new Path(checkpointDir))){
      fs.delete(new Path(checkpointDir), true)
    }
    if(fs.exists(new Path(loginParquet))){
      holdingDf = spark.read.parquet(loginParquet).as[LoginAttempt]
    }

    mainDf.writeStream.foreachBatch((batchDS: Dataset[LoginAttempt], batchId: Long)  => {
      println(" ==========BATCH RUN========== ")
      println("TOTAL MICROBATCH COUNT: "+batchDS.count())
      println("TOTAL PENDING COUNT: "+holdingDf.count())
      holdingDf = holdingDf.union(batchDS)
      val ds = holdingDf.persist(StorageLevel.MEMORY_AND_DISK)

      println("TOTAL WORKING COUNT (PENDING + MICROBATCH): "+ds.count())

      val timeThreshold = 30000L//120L*60L*1000L
      val maxTime = ds.groupBy().agg(max(ds("recordedTime"))).collect()(0).getAs[Timestamp](0)
      maxTime.setTime(maxTime.getTime-(timeThreshold))
      println("TIME THRESHOLD: "+ maxTime)

      val recentSrcDst = ds.filter(log=>{(log.recordedTime.getTime > maxTime.getTime)})
        .select("srcDstIp")
        .withColumnRenamed("srcDstIp", "recentSrcDstIp")
        .dropDuplicates()
        .persist()

      val workingDf = ds.filter(log=>{(log.recordedTime.getTime <= maxTime.getTime)})
        .join(recentSrcDst,$"srcDstIp" === $"recentSrcDstIp","leftanti")
        .drop($"recentSrcDstIp").as[LoginAttempt]

      val computeUniqueRate = workingDf.map(log => {(log.srcDstIp, Seq(log), Seq(log.credentialPair))})
        .groupByKey(_._1)
        .reduceGroups((a, b) => (a._1, a._2++b._2, a._3++b._3))
        .map(x=>(x._1, x._2._2, x._2._3.distinct.length.toFloat/x._2._3.length.toFloat, x._2._2.length))
        .as[(String, Seq[LoginAttempt], Float, Int)]
        .map(x=>(x._1,x._2,x._3,x._4,{

          val loginTimestamps = x._2.map(s=>s.loginTime)
          val min = loginTimestamps.min
          val max = loginTimestamps.max
          val attemptRate = ((max.getTime - min.getTime) / x._4)/1000
          val attemptsCount = x._2.length

          if(x._3>=0.75F && x._4>10) {
            val spamRateModel = ((attemptsCount - 10 * 11.5 + 5) * 60 / attemptsCount)
            if (spamRateModel > attemptRate) {
              return false // is spam, because attempt rate is faster than the equation
            } else {
              return true
            }
          }
          return true

          /*

          var isSpam = false
          val attempts = x._2.sortWith((x,y)=>{(x.loginTime.getTime<y.loginTime.getTime)})
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
           */
        })).as[(String, Seq[LoginAttempt], Float, Int, Boolean)]
      val nonSpamDf = computeUniqueRate.filter(x=>x._5).flatMap(_._2)
      val spamDf = computeUniqueRate.filter(x=>{!x._5}).flatMap(_._2)
      // nonSpam.show()
      // join nonspam and data from oracle
      // output result to kafka?

      holdingDf = ds.join(recentSrcDst,$"srcDstIp" === $"recentSrcDstIp","inner")
        .drop($"recentSrcDstIp").as[LoginAttempt].checkpoint(true)
      holdingDf.write.mode("overwrite").parquet(loginParquet)

      recentSrcDst.unpersist()
      ds.unpersist()
      
      println("TOTAL PENDING COUNT: "+holdingDf.count())
      //holdingDf.show(10)
      println("TOTAL NONSPAM COUNT: "+nonSpamDf.count())
      //nonSpamDf.show(10)
      println("TOTAL SPAM COUNT: "+spamDf.count())
      //spamDf.show(10)
    })
      .outputMode(OutputMode.Append())
      .option("checkpointLocation", kafkaCheckpoint)
      .start().awaitTermination()
  }
}
