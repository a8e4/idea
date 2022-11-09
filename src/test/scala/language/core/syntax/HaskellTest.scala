package language.core.syntax

import org.scalatest.flatspec._
import org.scalatest.matchers.should._

abstract class HaskellTest extends AnyFlatSpec with Matchers with ParserTest.Base {
  // import language.core._
  // object syntax extends haskell.Haskell.Syntax

  /*it should "parse all comment tokens" in {
    val source = network
    val lexer = Lexer(source)
    val text = lexer.consumeAll().map(_.value).mkString("")
    text shouldBe source.toString
    lexer.currentOffset shouldBe source.length
    println()
  }*/
}
