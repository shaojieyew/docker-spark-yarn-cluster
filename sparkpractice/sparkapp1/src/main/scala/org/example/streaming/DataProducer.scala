package org.example.streaming

import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.Properties

import com.google.protobuf.timestamp.Timestamp
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.example.streaming.Data.LoginLog

import scala.util.Random

// case class LoginLog (srcIp: String, dstIp: String, username: String, password: String, loginTimestamp: Timestamp, recordedTimestamp: Timestamp)

object DataProducer {

  def main(arg: Array[String]): Unit ={
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:29092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
    while(true){
      var attempt = Random.nextInt(10)
      val producer = new KafkaProducer[String, Array[Byte]](props)
      for(i <- 1 to attempt){
        Random.shuffle(generateLoginLogs(i)).foreach(log=>{
          val record = new ProducerRecord[String, Array[Byte]]("credential", log.dstIp+log.srcIp, log.toByteArray)
          //println(log)
          producer.send(record)
        })
      }
      producer.close()
      Thread.sleep(5000L)
    }
  }

  def generateLoginLogs(id: Int): Seq[LoginLog] ={
    var attempt = 8+Random.nextInt(22)
    var period = Random.nextFloat()*60*150
    var uniqueRate = 0.5F+(Random.nextFloat()*0.40F)
    var delay =  Random.nextInt(60*60)
    /*
    attempt = 10
    period = 60*120
    uniqueRate = 1F
     */
    val log = createLog(id)
    var logins = Seq[LoginLog]()

    var uniqueCount = 1
    var uniqueRateNow = uniqueCount.toFloat/attempt.toFloat
    var random = Seq[Float]()
    for(i<-0 to attempt-1) {
      random = random ++ Seq(Random.nextFloat())
    }
    random = random ++ Seq(0F)
    random = random.map(x => {
      (x / random.sum) * period
    })

    var accumulatedTime = 0F
    for(i<-1 to attempt){
      accumulatedTime = accumulatedTime + random(i-1)
      import java.time.Instant
      val instant = Instant.ofEpochSecond(log.getLoginTimestamp.seconds, log.getLoginTimestamp.nanos).minusSeconds(accumulatedTime.toLong + delay.toLong)
      val timestamp = Timestamp.defaultInstance.copy(seconds = instant.getEpochSecond, nanos = instant.getNano)

      if(uniqueRateNow<uniqueRate){
        logins = logins ++ Seq(
          log.copy(password = Random.alphanumeric.filter(_.isDigit).take(15).mkString,
            loginTimestamp =  Option(timestamp),
            recordedTimestamp = Option(timestamp)
          ))
        uniqueCount = uniqueCount + 1
        uniqueRateNow = uniqueCount.toFloat/attempt.toFloat;
      }else{
        logins = logins ++ Seq(log.copy(loginTimestamp =  Option(timestamp),
          recordedTimestamp = Option(timestamp)))
      }
    }
    logins
  }

  def createLog(id: Int): LoginLog ={
    val srcIp = System.nanoTime().toString+id.toString
    val dstIp = System.nanoTime().toString+id.toString
    val username =  "username"+Random.nextInt(10)
    val password = Random.alphanumeric.filter(_.isDigit).take(15).mkString
    val time = Instant.now
    val timestamp = Timestamp.defaultInstance.copy(seconds = time.getEpochSecond, nanos = time.getNano)
    LoginLog(srcIp,dstIp,username, password, Option(timestamp), Option(timestamp))
  }
}
