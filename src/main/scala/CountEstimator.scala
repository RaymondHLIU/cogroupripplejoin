package src.main.scala

class CountEstimator(length1: Long, length2: Long, p: Double) {

  val sizeA = length1
  val sizeB = length2
  val ratioA = 1.*length1/length2   //we assume that user join large table with small table
  val ratioB = 1.0
  //statistical info
  val zp =p
  var epson = 0.0
  
  var count1: Long = 1 //how many tuples passed by side1
  var count2: Long = 0
  val count = Array(2)
  val predicater = Array(2)//result
  var predicate1: Long = 0
  var predicate2: Long = 0
  var approxCount: Long = 0
  var countAvg: Double = 0.0
  //var countVar = Array[Double](2)
  var countVar1: Double = 0.0
  var countVar2: Double = 0.0
  var countVar: Double = 0.0
  
  def updateSide1(localPredicate: Long, idx1: Long, idx2: Long):Unit={
    predicate1 += localPredicate
    updateCountVar1(localPredicate, idx1: Long, idx2: Long)
    count2 += idx1
    updateApproxCount(idx1+1, idx2)
    updateCountVar(idx1+1,idx2)
  }
  def updateSide2(localPredicate: Long, idx1: Long, idx2: Long):Unit={
    predicate2 += localPredicate
    updateCountVar2(localPredicate, idx1: Long, idx2: Long)
    count1 += idx2
  }
  
  def updateApproxCount(idx1: Long, idx2: Long):Unit ={
    approxCount = (predicate1 + predicate2) * (sizeA * sizeB) / ((idx1+1) * (idx2+1)) 
  }
  
  //count variance
  //do divide operation only in updateCountVar func
  //update countAvg after update countVar1 or countVar2
  def updateCountVar1(predictCount: Long, idx1: Long, idx2: Long):Unit ={
    countVar1 += Math.pow((predictCount/(idx2+1) - countAvg), 2)  
    countAvg = (1.*(predicate1 + predicate2)/((idx1+1)*(idx2+1)))
    ///println("countAvg: "+countAvg+" countVar: "+countVar+"Iterval: "+countIterval)
  }    
  def updateCountVar2(predictCount: Long, idx1: Long, idx2: Long):Unit ={
    countVar2 += Math.pow((predictCount/(idx1+1) - countAvg), 2)
    countAvg = (1.*(predicate1 + predicate2)/((idx1+1)*(idx2+1)))
    //println("var2: "+countVar2 + " avg: "+ countAvg+"Iterval: "+interval(idx2))
  }
  def updateCountVar(idx1:Long, idx2:Long):Unit={
    countVar = countVar1/(idx1+1) + countVar2/(idx2+1) 
  }
  //idx2 is the current scaned length of small table
  //idx2/ratioB is current joined step
  def interval(idx2:Long):Double={
     zp * countVar / Math.sqrt(idx2/ratioB)
  }
}