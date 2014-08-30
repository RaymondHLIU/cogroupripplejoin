package main.scala
import java.util.Random
import java.io.PrintWriter
import scala.io.Source
import scala.tools.nsc.io.File

object WriteToLocalFile {

  
  def main(args: Array[String]) {

    val path = "/home/liuhao/spark-1.0.0/sql-data-conf"
    val lines = Source.fromFile(path).getLines()
    val size1 = lines.next.toLong
    val size2 = lines.next.toLong 
    val path1:String = lines.next.toString
    val path2:String = lines.next.toString
    
    val S = new PrintWriter(path1)
    
    val ranGen = new Random  
    var i = 0
    while(i < size1){
      S.println(ranGen.nextInt(1000000)+" "+ranGen.nextInt(10))
      i+=1
      if(i%10000 == 0)
        println("count" + i)
    }
    S.close
    
    val S1 = new PrintWriter(path2)
    var j = 0
    while(j < size2){
      S1.println(ranGen.nextInt(1000000)+" "+ranGen.nextInt(10))
      j+=1
      if(j%10000 == 0)
        println("count" + j)
    }
    S1.close
  }
}