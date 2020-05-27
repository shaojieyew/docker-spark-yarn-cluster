package org.example

import org.apache.spark.util.AccumulatorV2
import org.apache.spark.SparkContext

import scala.collection.mutable.ArrayBuffer


object BFSMapReduce {

  val startId = 1
  val targetId = 3

  val VISITED = 3
  val TO_VISIT = 2
  val NOT_SEEN = 1

  type BFSData = (Int, (Int, Array[Int], Int))
  type BFSNode = (Int, BFSData)

  // accumulator allows many executors to increment a shared variable
  var hitCounter:Option[AccumulatorV2[Int, Long]] = None

  /*
  (id, (distance, connectedTo, visitStatus))
  (1, (0, (2, 4), TO_VISIT))
  (2, (999, (1), NOT_SEEN))
  (3, (999, (5), NOT_SEEN))
  (4, (999, (1,5), NOT_SEEN))
  (5, (999, (3,4), NOT_SEEN))
   */

  def convertToBFS(line: String): BFSData ={
    val key = line.split(" ")(0).toInt
    val connections = line.split(" ").drop(1).map(_.toInt)
    var distance = Int.MaxValue
    var status = NOT_SEEN
    if(key==startId){
      distance = 0
      status = TO_VISIT
    }
    (key, (distance, connections, status))
  }

  /*
  (id, (distance, connectedTo, visitStatus))
  -(1, (0, (2, 4), TO_VISIT))
  +(1, (0, (2, 4), VISITED))
  +(2, (1, (), TO_VISIT))
  +(4, (1, (), TO_VISIT))
  (2, (999, (1), NOT_SEEN))
  (3, (999, (5), NOT_SEEN))
  (4, (999, (1,5), NOT_SEEN))
  (5, (999, (3,4), NOT_SEEN))
   */
  def mapBFS(x : BFSData): ArrayBuffer[BFSData] ={
    val currentId = x._1
    val distance = x._2._1
    val connections = x._2._2
    val toVisit = x._2._3

    var results:ArrayBuffer[BFSData] = ArrayBuffer()

    if(toVisit == TO_VISIT){
      connections.foreach(connection=>{
        val newId = connection
        val newDistance = distance + 1

        if(targetId==newId){
          if(hitCounter.isDefined){
            hitCounter.get.add(1)
          }
        }

        val newNode:BFSData = (newId, (newDistance, Array[Int](), TO_VISIT))
        results +=newNode
      })
      val newNode:BFSData = (currentId, (distance, connections, VISITED))
      results +=newNode
    }
    results +=x
    results
  }


  /*
  (id, (distance, connectedTo, visitStatus))
  (1, (0, (2, 4), VISITED))
  (2, (1, (), TO_VISIT))
  (4, (1, (), TO_VISIT))
  (2, (999, (1), NOT_SEEN))
  (3, (999, (5), NOT_SEEN))
  (4, (999, (1,5), NOT_SEEN))
  (5, (999, (3,4), NOT_SEEN))

  reduce min(distance) and max(visitStatus)

  (1, (0, (2, 4), VISITED))
  (2, (1, (1), TO_VISIT))
  (3, (999, (5), NOT_SEEN))
  (4, (1, (1,5), TO_VISIT))
  (5, (999, (3,4), NOT_SEEN))
   */
  def reduceBFS(x : (Int, Array[Int], Int), y : (Int, Array[Int], Int)): (Int, Array[Int], Int) = {
    ((Math.min(x._1, y._1),
      if(x._2.length>y._2.length) x._2 else y._2,
      Math.max(x._3, y._3)))
  }

  def main(args: Array[String]): Unit = {
    val sc = new SparkContext("local[*]", "BFSApp")
    var lines = sc.textFile("/home/ysj/Desktop/Workspace/Spark-Training/practice/sparkapp1/src/main/resources/graph.txt")
    var bfs = lines.map(convertToBFS)

    var break = false
    while(!break){
      bfs = bfs.flatMap(mapBFS)
      bfs = bfs.reduceByKey(reduceBFS)
      if(bfs.mapValues(_._3).filter((x)=> x._2.equals(2)).isEmpty()
      ||(hitCounter.isDefined && hitCounter.get.value>0)){
        break = true
      }
    }

    bfs.foreach(println)
    println("Total distance from %s to %s is %s".format(startId, targetId, bfs.filter(_._1==targetId).first()._2._1))

  }

}

