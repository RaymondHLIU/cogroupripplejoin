package src.main.scala

trait Estimator {

  val sizeA: Long 
  val sizeB: Long 
  val ratioA: Double   //we assume that user join large table with small table
  val ratioB: Double
  //statistical info
  val zp: Double 
  var epson: Double = 0.0

  def updateApprox()
  def updateVar1()
  def updateVar2()
  def updateVar()
}