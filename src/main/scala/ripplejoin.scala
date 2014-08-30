package src.main.scala

import java.io.{IOException, ObjectOutputStream}
import scala.collection.mutable.ArrayBuffer
import scala.language.existentials
import org.apache.spark.serializer._
import org.apache.spark.rdd.RDD
import org.apache.spark._
import SparkContext._
import java.util.{HashMap => JHashMap}
import org.apache.spark.util.collection.{ExternalAppendOnlyMap, AppendOnlyMap}
import org.apache.spark.serializer.Serializer
import org.apache.spark.broadcast.Broadcast

sealed trait CoGroupSplitDep extends Serializable

case class NarrowCoGroupSplitDep(
    rdd: RDD[_],
    splitIndex: Int,
    var split: Partition
  ) extends CoGroupSplitDep {

  @throws(classOf[IOException])
  private def writeObject(oos: ObjectOutputStream) {
    // Update the reference to parent split at the time of task serialization
    split = rdd.partitions(splitIndex)
    oos.defaultWriteObject()
  }
}

case class ShuffleCoGroupSplitDep(shuffleId: Int) extends CoGroupSplitDep

class CoGroupPartition(idx: Int, val deps: Array[CoGroupSplitDep])
  extends Partition with Serializable {
  override val index: Int = idx
  override def hashCode(): Int = idx
}

class ReadHDFSFile() extends Serializable{
  val output = "hdfs://sing050:9001/home/liuhao/data/broadcastRDD"
  val hadoopConf = new org.apache.hadoop.conf.Configuration()
  val hdfs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI("hdfs://sing050:9001"), hadoopConf)    
  //def file():Array[Any]={
  val file = hdfs.open(new org.apache.hadoop.fs.Path(output)).readDouble()    
  //}
}

class ripplejoin [K](@transient var rdds: Seq[RDD[_ <: Product2[K, _]]], part: Partitioner, 
    count1: Accumulator[Double], count2: Accumulator[Double], sum1: Accumulator[Double], 
    sum2: Accumulator[Double], rdd1Passed: Accumulator[Double], rdd2Passed: Accumulator[Double],
    rho1: Accumulator[Double], rho2: Accumulator[Double], countVar1: Accumulator[Double], 
    countVar2: Accumulator[Double], sumVar1: Accumulator[Double], sumVar2: Accumulator[Double])
    //count_broadcast: Broadcast[Array[Double]], sum_broadcast: Broadcast[Array[Double]], 
    //pass_broadcast: Broadcast[Array[Double]]) 
    extends RDD[(K, Array[ArrayBuffer[Any]])](rdds.head.context, Nil) with Logging{
  // For example, `(k, a) cogroup (k, b)` produces k -> Seq(ArrayBuffer as, ArrayBuffer bs).
  // Each ArrayBuffer is represented as a CoGroup, and the resulting Seq as a CoGroupCombiner.
  // CoGroupValue is the intermediate state of each value before being merged in compute.
  private type CoGroup = ArrayBuffer[Any]
  private type CoGroupValue = (Any, Int)  // Int is dependency number
  private type CoGroupCombiner = Array[CoGroup]
  private type CoSum = (Long, Long)

  private var serializer: Serializer = null

  /** Set a serializer for this RDD's shuffle, or null to use the default (spark.serializer) */
  def setSerializer(serializer: Serializer): ripplejoin[K] = {
    this.serializer = serializer
    this
  }
  
  //val aggr = new CoGroupAggregator

  override def getDependencies: Seq[Dependency[_]] = {
    rdds.map { rdd =>
      if (rdd.partitioner == Some(part)) {
        logDebug("Adding one-to-one dependency with " + rdd)
        new OneToOneDependency(rdd)
      } else {
        logDebug("Adding shuffle dependency with " + rdd)
        new ShuffleDependency[Any, Any](rdd, part, serializer)
      }
    }
  }

  override def getPartitions: Array[Partition] = {
    val array = new Array[Partition](part.numPartitions)
    for (i <- 0 until array.size) {
      // Each CoGroupPartition will have a dependency per contributing RDD
      array(i) = new CoGroupPartition(i, rdds.zipWithIndex.map { case (rdd, j) =>
        // Assume each RDD contributed a single dependency, and get it
        dependencies(j) match {
          case s: ShuffleDependency[_, _] =>
            new ShuffleCoGroupSplitDep(s.shuffleId)
          case _ =>
            new NarrowCoGroupSplitDep(rdd, i, rdd.partitions(i))
        }
      }.toArray)
    }
    array
  }
  
  override val partitioner: Some[Partitioner] = Some(part)
  
  override def compute(s: Partition, context: TaskContext): Iterator[(K, CoGroupCombiner)] = {
    val sparkConf = SparkEnv.get.conf
    val externalSorting = sparkConf.getBoolean("spark.shuffle.spill", true)
    val split = s.asInstanceOf[CoGroupPartition]
    val numRdds = split.deps.size

    val serializer = SparkEnv.get.serializer
    
    //val hdfs_value =  new ReadHDFSFile
  
    def updateIdx(size: Long, depNum: Int):Unit={
      if (depNum ==0)
        rdd1Passed += size
      else
        rdd2Passed += size
    }
    def updateCount(localCount: Long, depNum: Int):Unit={
      //val mu = 16156507200.0 / (4000000*40000000)
      if(depNum == 0){
        count1 += localCount
        countVar1 +=  Math.pow((Math.random()*80), 2)
        }
      else{
        count2 += localCount
        countVar2 +=  Math.pow((Math.random()*800), 2)
        }
    }
    //update sum and sumVar and rho
    def updateSum(iter: Iterator[Any], localCount: Long, depNum: Int):Unit={
      //val mu = 8074816499200.0 / (4000000*40000000)
      //val mu_count = 16156507200.0 / (4000000*40000000)
      if(depNum == 0){
        var localSum: Long = 0
        while(iter.hasNext)
            localSum = localSum + iter.next.asInstanceOf[Long]
        sum1 += localSum
        sumVar1 += Math.pow((Math.random()*80)*499.5, 2)
        rho1 += (Math.random()*80)*(Math.random()*80*499.5)
        }
      else{
        var localSum: Long = 0
        while(iter.hasNext)
            localSum += iter.next.asInstanceOf[Long]
        sum2 += localSum
        sumVar2 += Math.pow((Math.random()*800)*499.5, 2)
        rho2 += (Math.random()*800)*(Math.random()*800*499.5)
      }
    }    
    
    val map = new AppendOnly[K, CoGroupCombiner]
    val update: (Boolean, CoGroupCombiner) => CoGroupCombiner = (hadVal, oldVal) => {
      if (hadVal) oldVal else Array.fill(numRdds)(new CoGroup)
    }
    val getCombiner: K => CoGroupCombiner = key => {
      map.changeValue(key, update)
    }

    //broadcaster.start()
    // A list of (rdd iterator, dependency number) pairs
    for ((dep, depNum) <- split.deps.zipWithIndex) dep match {
      case NarrowCoGroupSplitDep(rdd, _, itsSplit) =>{
        // Read them from the parent
        val it = rdd.iterator(itsSplit, context).asInstanceOf[Iterator[Product2[K, Any]]]
        var localCount = 0         
        while (it.hasNext) {
          val kv = it.next()
          getCombiner(kv._1)(depNum) += kv._2
          updateCount(map(kv._1)(1-depNum).size, depNum)
          //val iter = map(kv._1)(1-depNum).iterator
          localCount+=1
          updateSum(map(kv._1)(1-depNum).iterator, localCount, depNum)
        }   
        updateIdx(localCount, depNum)
        //println("count1:" + count1.localValue+"count2: "+count2.localValue)
      }

      case ShuffleCoGroupSplitDep(shuffleId) =>{
        // Read map outputs of shuffle
        val fetcher = SparkEnv.get.shuffleFetcher
        val it = fetcher.fetch[Product2[K, Any]](shuffleId, split.index, context, serializer)
        var localCount = 0  
        while (it.hasNext) {
          val kv = it.next()
          getCombiner(kv._1)(depNum) += kv._2
          updateCount(map(kv._1)(1-depNum).size, depNum)
          //val iter = map(kv._1)(1-depNum).iterator
          localCount+=1
          updateSum(map(kv._1)(1-depNum).iterator, localCount, depNum)
        }
        updateIdx(localCount, depNum)
        //println("count1:" + count1.localValue+"count2: "+count2.localValue)
      }
    }
    
    new InterruptibleIterator(context, map.iterator)

  }
  
  override def clearDependencies() {
    super.clearDependencies()
    rdds = null
  }

}