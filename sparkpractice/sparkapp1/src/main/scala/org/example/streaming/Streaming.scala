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


    mainDf.writeStream.foreachBatch((batchDF: Dataset[LoginAttempt], batchId: Long)  => {
      // write current microbatch to parquet
      batchDF.write
        .format("parquet")
        .partitionBy("srcDstIp")
        .mode("append")
        .save("src/main/resources/loginLog.parquet")
      // read all from parquet
      val df = spark
        .read
        .format("parquet")
        .load("src/main/resources/loginLog.parquet").as[LoginAttempt]
      df.persist()

       // get max recordedTime
      val timeThreshold = 1L*60L*1000L
      val maxtime = df.groupBy().agg(max(df("recordedTime"))).collect()(0).getAs[Timestamp](0)
      maxtime.setTime(maxtime.getTime-(timeThreshold))
      val maxtimeBroadcast = spark.sparkContext.broadcast(maxtime)

      // get all srcDst recorded within last 2 hours from max recordedTime
      val recentSrcDst = df.filter(log=>{(log.recordedTime.getTime - maxtimeBroadcast.value.getTime>0)})
        .select("srcDstIp").withColumnRenamed("srcDstIp", "recentSrcDstIp").dropDuplicates()
        recentSrcDst.show(100000, truncate = false)

      // get all without recent
      val workingDf = df.filter(log=>{(log.recordedTime.getTime - maxtimeBroadcast.value.getTime<=0)})
        .join(recentSrcDst,$"srcDstIp" === $"recentSrcDstIp","leftanti").drop($"recentSrcDstIp").as[LoginAttempt]

      val computeUniqueRate = workingDf.groupByKey(_.srcDstIp)
        .mapGroups((srcDstIp, itr) => {(srcDstIp, itr.toSeq, itr.map(_.credentialPair).toSeq)})
        .map( srcDstAttempts => {(srcDstAttempts._1,
          srcDstAttempts._2,
          srcDstAttempts._3.distinct.length.toFloat/srcDstAttempts._3.length.toFloat,
          srcDstAttempts._3.length)}).as[(String, Seq[LoginAttempt], Float, Int)]

      var nonSpamDf1 = computeUniqueRate.filter(x=>{if(x._3<0.75F || x._4<10){
        true
      }else{
        false
      }}).map(x=>{(x._1,x._2)})

      var nonSpamDf2 = computeUniqueRate.filter(_._3>0.75F).map(x=>{(x._1,x._2)})
          .filter(x=>{
            val attempts = x._2.sortWith((x,y)=>{(x.loginTime.getTime<y.loginTime.getTime)})
            val attemptsCount = attempts.length
            var isSpam = false
            var i =0
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
            !isSpam
          }).map(x=>{(x._1,x._2)})

      var nonSpam = nonSpamDf1.union(nonSpamDf2)
      nonSpam.show(100000, truncate = false)

      recentSrcDst.unpersist()
      df.unpersist()

      /*


      // Save all recent srcDst and remove all log > time threshold
      val saveDf = df.joinWith(recentSrcDst,$"srcDstIp" === $"recentSrcDstIp","rightouter")
        .map(_._1)
      saveDf.write
        .format("parquet")
        .partitionBy("srcDstIp")
        .mode("overwrite")
        .save("src/main/resources/loginLog.parquet")
       */


      /*
*/
      /*
     val nonSpammer = computeUniqueRate.filter(srcDstAttemptsUniqueRate=>{
       srcDstAttemptsUniqueRate._3<0.75F || srcDstAttemptsUniqueRate._4<100000000
     }).map(nonSpammerSrcDstAttempts=>{
       nonSpammerSrcDstAttempts._2
     }).flatMap(_.toList).map(_.loginLog)
       */



    })
    .trigger(Trigger.ProcessingTime(5*1000))
    .outputMode(OutputMode.Append())
    .option("checkpointLocation", "src/main/resources/checkpoint")
    .start().awaitTermination()



    /*
    val df = mainDf.withWatermark("recordedTime", "4 hours").withColumnRenamed("srcDstIp", "srcDstIp1")
      .join(mainDf.withWatermark("recordedTime", "2 hours").withColumnRenamed("srcDstIp", "srcDstIp2")
      ,$"srcDstIp1" === $"srcDstIp2","leftanti")
    df.writeStream.format("console")
  .outputMode("update")
  .start()
  .awaitTermination()
*/
    /*
    val grpMaxTime = df.map(log=>((1, Seq[LoginAttempt](log),log.recordedTime)))
      .groupByKey(_._1)
      .reduceGroups((a,b)=>{(1, a._2++b._2, if(a._3.getTime>b._3.getTime){a._3 } else {b._3})})
      .flatMap(x=>x._2._2.map(y=>y.copy(globalMaxTime = x._2._3)).toList)

    val grpMaxTime2 = grpMaxTime.map(x=>{(x.srcDstIp , (Seq[LoginAttempt](x),x.recordedTime)) })
      .groupByKey(_._1)
      .reduceGroups((a, b)=> {(a._1,(a._2._1++b._2._1, if(a._2._2.getTime>b._2._2.getTime){a._2._2 } else {b._2._2}))})
      .flatMap(x=>x._2._2._1.map(y=>y.copy(localMaxTime = x._2._2._2)).toList)

    val grpMaxTime111 = df.map(log=>(log.srcDstIp, (Seq[LoginAttempt](log),log.recordedTime) )).as[(String, (Seq[LoginAttempt],Timestamp))]
      .groupByKey(_._1)
      .reduceGroups((a, b)=> {(a._1,(a._2._1++b._2._1, if(a._2._2.getTime>b._2._2.getTime){a._2._2 } else {b._2._2}))})
      .map(x=>{(x._2._1,x._2._2._1, x._2._2._2)})
      .map(x=>{(Seq(x), x._3, 1)})
      .groupByKey(_._3)
      .reduceGroups((a,b)=>{(a._1++b._1, if(a._2.getTime>b._2.getTime){a._2 } else {b._2}, 1)})
      .map(x=>{x._2._1.filter(l=>{(x._2._2.getTime-(120L*60L)>l._3.getTime)}) })
      .flatMap(x=>x.toList)




    grpMaxTime2.writeStream.format("console")
      .outputMode("update")
      .start()
      .awaitTermination()
*/

    /*
    val df1 = df
      .map(log => (log.srcDstIp, (log , 1, Seq[String](log.credentialPair)))).as[(String, (LoginAttempt, Int, Seq[String]))]



    val df2 = df1.groupByKey(_._1)
        .reduceGroups((a, b) => {( a._1, (a._2._1,a._2._2 + b._2._2 , a._2._3 ++ b._2._3 ) )})
        .map( x=>{
          (x._1,x._2._2._3.distinct.length.toFloat/x._2._2._2.toFloat)
        }).as[(String, Float)]
        .filter(x=>{x._2<0.75F})


    df2.writeStream.format("console")
      .outputMode("update")
      .start()
      .awaitTermination()*/
  }
}
