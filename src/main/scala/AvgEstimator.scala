package src.main.scala

class AvgEstimator(length1: Long, length2: Long, p: Double) {

  val sizeA = length1
  val sizeB = length2
  val ratioA = 1.*length1/length2   //we assume that user join large table with small table
  val ratioB = 1.0
  //statistical info
  val zp =p
  var epson = 0.0

  var approxAvg: Double = 0
  var avgVar: Double = 0.0
  var rho1: Double = 0.0
  var rho2: Double = 0.0
  var rho: Double = 0.0
 
  def updateSide1(localSumAvg: Double, localCountAvg: Double, sumAvg: Double,
      countAvg: Double, sumVar: Double, countVar: Double, approxSum: Long, 
      approxCount: Long, idx1: Long, idx2: Long):Unit={
    updateRho1(localSumAvg, localCountAvg, sumAvg, countAvg)
    updateRho(idx1, idx2)
    updateAvgVar(sumVar, countVar, sumAvg, countAvg)
    updateApproxAvg(approxSum, approxCount)
  }
  def updateSide2(localSumAvg: Double, localCountAvg: Double, sumAvg: Double, countAvg: Double):Unit={
    updateRho2(localSumAvg, localCountAvg, sumAvg, countAvg)
  }
  
  def updateApproxAvg(approxSum: Long, approxCount: Long):Unit ={
    approxAvg= 1. * approxSum / approxCount
  } 
  //count variance
  def updateAvgVar(sumVar:Double, countVar:Double, sumAvg:Double, countAvg:Double):Unit={
    avgVar = (sumVar - 2 * (sumAvg/countAvg)*rho + Math.pow(rho,2)*countVar)
    //println("sumVar: "+sumVar+" sumAvg/countAvg: "+2 * (sumAvg/countAvg)*rho + "countVar:"+Math.pow(rho,2)*countVar)
    ///Math.pow(countAvg, 2) 
  }
  def updateRho1(localSumAvg: Double, localCountAvg: Double, sumAvg: Double, countAvg: Double):Unit={
    rho1 += 1. * (localSumAvg - sumAvg) * (localCountAvg - countAvg)
  }
  def updateRho2(localSumAvg: Double, localCountAvg: Double, sumAvg: Double, countAvg: Double):Unit={
    rho2 += (localSumAvg - sumAvg) * (localCountAvg - countAvg)
  }
  def updateRho(idx1:Long, idx2:Long){
    rho = rho1/(idx1+1) + rho2/(idx2+1)
  }
  def interval(idx2:Long):Double={
     zp * avgVar / Math.sqrt(idx2/ratioB)
  }
}