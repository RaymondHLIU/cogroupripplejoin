package src.main.scala
import org.apache.spark._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd._
import java.util.Random
import java.io.PrintWriter
import scala.io.Source
import org.apache.spark.Partitioner.defaultPartitioner

object DataGenerator extends App{
  val path = "/home/liuhao/spark-1.0.0/join-conf"
  val lines = Source.fromFile(path).getLines()
  val RDDSIZE1:Long = lines.next.toLong
  val RDDSIZE2:Long = lines.next.toLong
  println(RDDSIZE1)
  //val RDDSIZE1:Long = 2000000
  //val RDDSIZE2:Long = 1000000
  val part1: Int = lines.next.toInt
  val part2: Int = lines.next.toInt
  val path1:String = lines.next.toString
  val path2:String = lines.next.toString
  
  val conf = new SparkConf()
                   .setMaster("spark://sing050:7077")
                   .setAppName("JoinDataGenerator")
                   //.set("spark.executor.uri", "/home/liuhao/spark-0.9.1/spark-0.9.1.tar.gz")
                   .set("spark.executor.memory", "6g")
      
  val sc = new SparkContext(conf)
  val ranGen = new Random  
  
  def makeLocalList(RDDSIZE: Long):List[(Long, Long)]={
    var result = List[(Long, Long)]()
    var i :Long = 0
    while (i < RDDSIZE) {
      result = ((ranGen.nextInt(10000).asInstanceOf[Long]), ranGen.nextInt(1000).asInstanceOf[Long]) :: result
      i += 1
    }
    result
  }
  
  def makeRDDWithPartitioner(seq: Seq[(Long, Long)]) ={
      sc.makeRDD(seq, 4)
        .map(x => (x, null))
        .partitionBy(new HashPartitioner(4))
        .mapPartitions(_.map(_._1), true)
   }

  var rdd1 = makeRDDWithPartitioner(makeLocalList(RDDSIZE1))
  println("rdd1 count: " + rdd1.count)  
  
  var i = 0
    //7 iteration 3G join 3G 128,000,000 join 128,000,000 =  180,153,384,960
  while(i < 2){ 
    rdd1 =  rdd1 ++ rdd1
    println("rdd1 count: " + rdd1.count)
    i += 1
  } 
  rdd1 = rdd1 ++ rdd1 ++ rdd1 ++ rdd1 ++ rdd1
  //rdd1 = rdd1.repartition(512)
  //rdd1 = rdd1 ++ rdd1 ++ rdd1
  //rdd1 = rdd1.map(x => (x._1*(new Random).nextInt(100), x._2))  //project current rdd, more random
  //rdd1 =  rdd1 ++ rdd1
  //rdd1 = rdd1.repartition(512)
  rdd1.saveAsSequenceFile(path1)
  i = 0
  var rdd2 = rdd1;
  while (i < 9){
    rdd2 = rdd2 ++ rdd1
    i += 1
  }
  //rdd2 = rdd2.repartition(1024)
  //rdd2 = rdd2.map(x => (x._1*(new Random).nextInt(100), x._2))
  rdd2.saveAsSequenceFile(path2)

}