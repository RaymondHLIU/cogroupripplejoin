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

import org.apache.hadoop.conf.{Configurable, Configuration}
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.io.SequenceFile.CompressionType
import org.apache.hadoop.io.compress.CompressionCodec
import org.apache.hadoop.mapred.{FileOutputCommitter, FileOutputFormat, JobConf, OutputFormat}
import org.apache.hadoop.mapreduce.{Job => NewAPIHadoopJob, OutputFormat => NewOutputFormat,
RecordWriter => NewRecordWriter, SparkHadoopMapReduceUtil}

object JoinJob extends App{
    
  val path = "/home/liuhao/spark-1.0.0/join-conf"
  val lines = Source.fromFile(path).getLines()
  val RDDSIZE1:Long = lines.next.toLong
  val RDDSIZE2:Long = lines.next.toLong
  val part1: Int = lines.next.toInt
  val part2: Int = lines.next.toInt
  val path1:String = lines.next.toString
  val path2:String = lines.next.toString
  val tableSize = 2
  
  val conf = new SparkConf()
                   .setMaster("spark://sing050:7077")
                   //.setMaster("local")
                   .setAppName("RippleJoin")
                   //.setSparkHome("/home/liuhao/spark-1.0.0")
                   //.setSparkHome(System.getenv("SPARK_HOME"))
                   //.setJars(Seq(System.getenv("SPARK_RIPPLEJOIN_JAR")))
                   //.setJars(Seq("/home/liuhao/workspace/cogroupripplejoin/target/scala-2.10/cogroupripplejoin-project_2.10-1.0.jar"))                   
                   //.set("spark.executor.uri", "/home/liuhao/spark-0.9.1/spark-0.9.1.tar.gz")
                   .set("spark.executor.memory", "6g")
                   .set("spark.hadoop.validateOutputSpecs", "false")
                   //.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                   //.set("spark.kryo.registrator", "mypackage.MyRegistrator")
                   //.set("spark.executor.extraJavaOptions", value)
                   //.set("spark.cores.max", "10")
      
  val sc = new SparkContext(conf)
  //sc.hadoopConfiguration.setBoolean("spark.hadoop.validateOutputSpecs", false)
  val S = new PrintWriter("test1.txt")
  //rdd1.saveAsSequenceFile("hdfs://sing050:9001/home/liuhao/data/rdd1")
  val rdd1 = sc.sequenceFile[Long,Long](path1)  
  val rdd2 = sc.sequenceFile[Long,Long](path2)
  val size1 = rdd1.count
  val size2 = rdd2.count
  S.println("rdd1: "+size1)
  S.println("par1: "+rdd1.partitions.length)
  S.println("rdd2: "+size2)
  S.println("par1: "+rdd2.partitions.length)
  
  
   
  val count1 = sc.accumulator(0.0)
  val count2 = sc.accumulator(0.0)
  val sum1 = sc.accumulator(0.0)
  val sum2 = sc.accumulator(0.0)
  val rdd1Passed = sc.accumulator(0.0)
  val rdd2Passed = sc.accumulator(0.0)
  val countVar1 = sc.accumulator(0.0)
  val countVar2 = sc.accumulator(0.0)
  val sumVar1 = sc.accumulator(0.0)
  val sumVar2 = sc.accumulator(0.0)
  val rho1 = sc.accumulator(0.0)
  val rho2 = sc.accumulator(0.0)
  var test_value = 0.0
  
  //define accumulator to calulate avg of count and sum, and then boardcast
  var count_broadcast = sc.broadcast(Array(111.0, 222.0))
  //var count_broadcast = sc.broadcast(Array(count1.value, count2.value))
  
  var sum_broadcast = sc.broadcast(Array(sum1.value, sum2.value))
  var pass_broadcast = sc.broadcast(Array(rdd1Passed.value, rdd2Passed.value))
  var broadcastRDD = sc.makeRDD(Array(count1.value, count2.value,sum1.value, sum2.value,
      rdd1Passed.value, rdd2Passed.value))
  val output = "hdfs://sing050:9001/home/liuhao/data/broadcastRDD"
  val hadoopConf = new org.apache.hadoop.conf.Configuration()
  val hdfs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI("hdfs://sing050:9001"), hadoopConf)  
  //try { hdfs.delete(new org.apache.hadoop.fs.Path(output), true) } catch { case _ : Throwable => { } }
  //broadcastRDD.saveAsTextFile(output)
  //try { hdfs.delete(new org.apache.hadoop.fs.Path(output), true) } catch { case _ : Throwable => { } }
  //broadcastRDD.saveAsTextFile(output)
  
  
  //set daemon thread to query join process
  val estimator = new Thread(new Runnable{
    //@volatile var exit = true; 
    //val joiner = new JoinEstimator(count1, count2, sum1, sum2, rdd1Passed, rdd2Passed, size1, size2)
     def approximateCount():Double={
    1.* (count1.value + count2.value) * ( size1* size2) / (rdd1Passed.value * rdd2Passed.value) 
  }
  def approximateSum():Double={
    1.* (sum1.value + sum2.value) * (size1 * size2) / (rdd1Passed.value * rdd2Passed.value)
  }
  def approximateAvg():Double={
    approximateSum / approximateCount
  }
  //countVar1.value/rdd1Passed.value is real countVar1
  def countVar():Double={
    countVar1.value/rdd1Passed.value + countVar2.value/rdd2Passed.value 
  }
  def sumVar():Double={
    sumVar1.value/rdd1Passed.value + sumVar2.value/rdd2Passed.value 
  }
  def avgVar():Double={
    val rho = rho1.value/rdd1Passed.value + rho2.value/rdd2Passed.value 
    val avg = (sum1.value+sum2.value)/(count1.value+count2.value)
    (sumVar() - 2*avg*rho +  Math.pow(avg, 2)*countVar()) / 
    	Math.pow((count1.value+count2.value)/(rdd1Passed.value+rdd2Passed.value), 2)
  }
    def run() {
      while(true){
        S.println(approximateAvg)
        Thread.sleep(1000)
        //S.println("COUNT:" +approximateCount+" avg: "+approximateAvg + " Time: " + System.currentTimeMillis())
        //S.println("avgVar: "+ avgVar + " epson: " + (0.99*avgVar/Math.sqrt(rdd1Passed.value)))
        //S.println((0.99*avgVar/Math.sqrt(rdd1Passed.value)))
        //S.println("COUNT error: " +((countVar*0.05)/Math.sqrt(rdd2Passed.value))
        //    + "SUM error: " + ((sumVar*0.05)/Math.sqrt(rdd2Passed.value))
        //    + "AVG error: " + ((avgVar*0.05)/Math.sqrt(rdd2Passed.value)))
        //Thread.sleep(1000)
        test_value = test_value + 1
        //S.println("test_value: " + test_value )
      }
    }
  })
  estimator.setDaemon(true)
  
  val broadcaster = new Thread(new Runnable{
    //@volatile var exit = true;  
    def run() {
      while(true){
        //count_broadcast.unpersist(true)
        //count_broadcast = sc.broadcast(Array(2333333.0))//sc.broadcast(Array(count1.value, count2.value))
        //S.println("count_broadcast: " + count_broadcast.value.sum )
        //sum_broadcast.unpersist(true)
        //sum_broadcast = sc.broadcast(Array(sum1.value, sum2.value))
        //pass_broadcast.unpersist(true)
        //pass_broadcast = sc.broadcast(Array(rdd1Passed.value, rdd2Passed.value))
        //broadcastRDD.unpersist(true)
        //broadcastRDD = sc.makeRDD(Array(count1.value, count2.value,sum1.value, sum2.value,
        //  rdd1Passed.value, rdd2Passed.value))
        broadcastRDD = sc.makeRDD(Array(count1.value, count2.value,sum1.value, sum2.value,
          rdd1Passed.value, rdd2Passed.value))
        //try { hdfs.delete(new org.apache.hadoop.fs.Path(output), true) } catch { case _ : Throwable => { } }
        //broadcastRDD.saveAsTextFile(output)
        Thread.sleep(1000)
      }
    }
  })
  broadcaster.setDaemon(true)

    val begintime2=System.currentTimeMillis()
    S.println("START JOIN: "+ System.currentTimeMillis())    
    
    val jresult2 = new ripplejoin[Long](Seq(rdd1,rdd2),
        defaultPartitioner(rdd1, rdd2), count1, count2, sum1, sum2, 
        rdd1Passed, rdd2Passed, rho1, rho2, countVar1, countVar2, sumVar1, sumVar2)
        //count_broadcast, sum_broadcast, pass_broadcast, sc)
     //val jresult2 = rdd1.join(rdd2)
    //broadcaster.start()
    estimator.start()
    val count = jresult2.count
    val endtime2 = System.currentTimeMillis()    
    println(count)
    S.println("COUNT" + count)
    val costTime2 = (endtime2 - begintime2)
    S.println("ACCUM" + (count1.value+count2.value))
    S.println("SUM" + (sum1.value+ sum2.value))
    S.println("AVG: " + (sum1.value + sum2.value) /(count1.value + count2.value))    
    S.println("Join Time: " + costTime2)
    S.close()
    //rdd1.partitions.length
  
}