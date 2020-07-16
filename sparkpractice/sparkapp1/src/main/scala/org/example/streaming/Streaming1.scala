package org.example.streaming

import java.sql.Timestamp
import java.time.Instant

import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.apache.spark.sql.{Dataset, Encoders, SparkSession}
import org.example.streaming.Data.LoginLog
import org.example.streaming.Data.output
import org.apache.hadoop.fs.Path

object Streaming1 {
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
      .option("subscribe", "login")
      .option("startingOffsets", "earliest")
      .load().select("value").as[Array[Byte]].map(LoginLog.parseFrom)
      .as[LoginLog](Encoders.product[LoginLog])
      .map(wrapData).as[LoginAttempt](Encoders.product[LoginAttempt])


    mainDf.writeStream.foreachBatch((batchDS: Dataset[LoginAttempt], batchId: Long)  => {

      val logParquet = "src/main/resources/loginLog.parquet"
      val logParquetTemp = "src/main/resources/loginLog.temp.parquet"
      val lastUpdateParquet = "src/main/resources/loginLog_lastUpdate.parquet"

      val fs = org.apache.hadoop.fs.FileSystem.get(spark.sparkContext.hadoopConfiguration)
      if(fs.exists(new Path(logParquetTemp))){
        fs.delete(new Path(logParquetTemp), true)
      }
      if(fs.exists(new Path(logParquet))){
        fs.copyFromLocalFile(new Path(logParquet), new Path(logParquetTemp))
      }

      batchDS.show()
      batchDS.write.partitionBy("srcDstIp")
        .mode("append")
        .parquet(logParquetTemp)

      val ds = spark.read.parquet(logParquetTemp).as[LoginAttempt]
        .persist()
      if(!ds.isEmpty){

        val timeThreshold = 10L*1000L
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
        fs.delete(new Path(logParquetTemp), true)

        nonSpam.show()
        val df = nonSpam.flatMap(row=>row._2.map(_.loginLog)).toDF()
        df.show()
        df.select(df.col("*")).show()
        df.map(row=>{
          val timestamp=row.getStruct(row.fieldIndex(LoginLog.scalaDescriptor.findFieldByNumber(LoginLog.LOGINTIMESTAMP_FIELD_NUMBER).get.name))
          output(loginTimestamp = Option(new com.google.protobuf.timestamp.Timestamp(timestamp.getAs("seconds"),timestamp.getAs("nanos"))))
        }).show()
        //df.explain(true)
        //df.explain()
        //df.write.format("console").save()
      }
      ds.unpersist(true)
    })
    .trigger(Trigger.ProcessingTime(15*1000L))
    .outputMode(OutputMode.Append())
    .option("checkpointLocation", "src/main/resources/checkpoint")
    .start().awaitTermination()
  }
}
