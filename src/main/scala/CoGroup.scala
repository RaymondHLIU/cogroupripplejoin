//package org.apache.spark
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
import java.io._
//import main.scala.AppendOnlyMap



class CoGroupAggregator
  extends Aggregator[Any, Any, ArrayBuffer[Any]](
    { x => ArrayBuffer(x) },
    { (b, x) => b += x },
    {(c1, c2) => c1++c2 })
  with Serializable


class CoGroup[K](@transient var rdds: Seq[RDD[_ <: Product2[K, _]]], part: Partitioner) 
    extends RDD[(K, Array[ArrayBuffer[Any]])](rdds.head.context, Nil) with Logging {
   // For example, `(k, a) cogroup (k, b)` produces k -> Seq(ArrayBuffer as, ArrayBuffer bs).
  // Each ArrayBuffer is represented as a CoGroup, and the resulting Seq as a CoGroupCombiner.
  // CoGroupValue is the intermediate state of each value before being merged in compute.
  private type CoGroup = ArrayBuffer[Any]
  private type CoGroupValue = (Any, Int)  // Int is dependency number
  private type CoGroupCombiner = Array[CoGroup]
  
  private var serializer: Serializer = null

  /** Set a serializer for this RDD's shuffle, or null to use the default (spark.serializer) */
//  def setSerializer(serializer: Serializer): CoGroup[K] = {
//    this.serializer = serializer
//    this
//  }
  val aggr = new CoGroupAggregator

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
    //val S = new PrintWriter("tmpresult.txt")

    val serializer = SparkEnv.get.serializer
    // A list of (rdd iterator, dependency number) pairs
    val rddIterators = new ArrayBuffer[(Iterator[Product2[K, Any]], Int)]
    for ((dep, depNum) <- split.deps.zipWithIndex) dep match {
      case NarrowCoGroupSplitDep(rdd, _, itsSplit) =>{
        // Read them from the parent
        val it = rdd.iterator(itsSplit, context).asInstanceOf[Iterator[Product2[K, Any]]]
        rddIterators += ((it, depNum))
        //println("depNum:" + depNum.asInstanceOf[Int])
    }

      case ShuffleCoGroupSplitDep(shuffleId) =>{
        // Read map outputs of shuffle
        val fetcher = SparkEnv.get.shuffleFetcher
        val it = fetcher.fetch[Product2[K, Any]](shuffleId, split.index, context, serializer)
        rddIterators += ((it, depNum))
        //println("depNum:" + depNum.asInstanceOf[Int])
        }
    }
    
     //S.close()

    //val map = new AppendOnlyMap[K, CoGroupCombiner]
     val map = new AppendOnly[K, CoGroupCombiner]
    val update: (Boolean, CoGroupCombiner) => CoGroupCombiner = (hadVal, oldVal) => {
      if (hadVal) oldVal else Array.fill(numRdds)(new CoGroup)
    }
    val getCombiner: K => CoGroupCombiner = key => {
      map.changeValue(key, update)
    }
    rddIterators.foreach { case (it, depNum) =>
      while (it.hasNext) {
        val kv = it.next()
        getCombiner(kv._1)(depNum) += kv._2
      }
    }
    new InterruptibleIterator(context, map.iterator)

  }

  
  override def clearDependencies() {
    super.clearDependencies()
    rdds = null
  }
  
}
  