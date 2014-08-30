package src.main.scala

class SumEstimator(length1: Long, length2: Long, p: Double) {

  val sizeA = length1
  val sizeB = length2
  val ratioA = 1.*length1/length2   //we assume that user join large table with small table
  val ratioB = 1.0
  //statistical info
  val zp =p
  var epson = 0.0
  
  var count1: Long = 1 //how many tuples passed by side1
  var count2: Long = 0
  var predicate1: Long = 0
  var predicate2: Long = 0
  var approxSum: Long = 0
  var sumAvg: Double = 0.0
  var sumVar1: Double = 0.0
  var sumVar2: Double = 0.0
  var sumVar: Double = 0.0
  
  def updateSide1(localPredicate: Long, idx1: Long, idx2: Long):Unit={
    predicate1 += localPredicate
    updateSumVar1(localPredicate, idx1: Long, idx2: Long)
    count2 += idx1
    updateApproxSum(idx1+1, idx2)
    updateSumVar(idx1+1,idx2)
  }
  def updateSide2(localPredicate: Long, idx1: Long, idx2: Long):Unit={
    predicate2 += localPredicate
    updateSumVar2(localPredicate, idx1: Long, idx2: Long)
    count1 += idx2
  }
  
  
  def updateApproxSum(idx1: Long, idx2: Long):Unit ={
    approxSum= (predicate1 + predicate2) * (sizeA * sizeB) / ((idx1+1) * (idx2+1)) 
  }  
  //count variance
  //do divide operation only in updateCountVar func
  //update countAvg after update countVar1 or countVar2
  def updateSumVar1(localPredicateSum: Long, idx1: Long, idx2: Long):Unit ={
    sumVar1 += Math.pow((localPredicateSum/(idx2+1) - sumAvg), 2)  
    sumAvg = (1.*(predicate1 + predicate2)/((idx1+1)*(idx2+1)))
    ///println("countAvg: "+countAvg+" countVar: "+countVar+"Iterval: "+countIterval)
  }    
  def updateSumVar2(localPredicateSum: Long, idx1: Long, idx2: Long):Unit ={
    sumVar2 += Math.pow((localPredicateSum/(idx1+1) - sumAvg), 2)
    sumAvg = (1.*(predicate1 + predicate2)/((idx1+1)*(idx2+1)))
    //println("var2: "+countVar2 + " avg: "+ countAvg+"Iterval: "+interval(idx2))
  }
  def updateSumVar(idx1:Long, idx2:Long):Unit={
    sumVar = sumVar1/(idx1+1) + sumVar2/(idx2+1) 
  }
  //idx2 is the current scaned length of small table
  //idx2/ratioB is current joined step
  def interval(idx2:Long):Double={
     zp * sumVar / Math.sqrt(idx2/ratioB)
  }
  
  
}