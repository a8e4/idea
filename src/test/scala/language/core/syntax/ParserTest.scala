package language.core.syntax

import scala.util.parsing.combinator.RegexParsers
import com.intellij.psi.{FileViewProvider, PsiFile}
import language.core
import org.scalatest.flatspec._
import org.scalatest.matchers.should._

object ParserTest {
  trait Base { this:AnyFlatSpec with Matchers ⇒
    import language.core._
    import syntax.{VaName,TyName,OpName,Space,Comment,Element}

    val syntax:core.syntax.Syntax
    val decls:CharSequence = fromResource("language/core/test/syntax/haskell/Decl.hs").mkString
    val comments:CharSequence = fromResource("language/core/test/syntax/haskell/Comment.hs").mkString
    val network:CharSequence = fromResource("language/core/test/syntax/haskell/Network.hs").mkString
    val userGuide:CharSequence = fromResource("language/core/test/syntax/haskell/UsersGuide.hs").mkString

    object Lexer {
      def apply(source:CharSequence):syntax.Lexer = {
        val lexer = syntax.Lexer()
        lexer.start(source, 0, source.length(), 0)
        lexer
      }
    }

    implicit class LexerOp(val self:syntax.Lexer) {
      def next(ty:syntax.Element, state:Int, id:String): Unit = {
        self.getState shouldBe state
        self.getTokenType shouldBe ty
        self.getTokenText shouldBe id
        self.advance()
      }

      def adv(ty:syntax.Element, id:String): Unit = {
        self.next(ty, 0, id)
      }

      def va(id:String)=  adv(VaName, id)
      def ty(id:String) = adv(TyName, id)
      def op(id:String) = adv(OpName, id)

      def space(count:Int=1):Unit = consume(Space, count)
      def pragma(count:Int=1):Unit = consume(Comment, count)
      def comment(count:Int=1):Unit = consume(Comment, count)
      def consumeAll():List[syntax.Lexer.Token] = self.iter.toList
      def consume(ty:Element,count:Int,space:Boolean=false):Unit = for {
        _ <- 0 until count
      } {
        self.getTokenType shouldBe ty
        self.getTokenText should not be null
        self.advance()
        if(space) this.space()
      }
    }
  }
}

class ParserTest extends AnyFlatSpec with Matchers with ParserTest.Base {
  object syntax extends core.syntax.Syntax(null, null)(null) { self:RegexParsers ⇒
    override def comments = List("{-" → "-}")
    override def keywords = List("does_not_exist")
    override def createFile(fileViewProvider: FileViewProvider): PsiFile = ???
  }

  it should "not fail to parse empty file" in {
    val lexer = syntax.Lexer()
    lexer.start("", 0, 0, 0)
    lexer.next(null, 0, null)
  }

  it should "parse haskell source file" in {
    val lexer = Lexer(decls)
    lexer.va("module")
    lexer.space()
    lexer.ty("DataDecl")
    lexer.space()
    lexer.va("where")
    lexer.space(2)
    lexer.va("data")
    lexer.space()
    lexer.ty("AB")
    lexer.space()
    lexer.op("=")
    lexer.space()
    lexer.ty("A")
    lexer.space()
    lexer.op("|")
    lexer.space()
    lexer.ty("B")
    lexer.space()
    lexer.ty("Int")
    lexer.space(2)
    lexer.va("f")
    lexer.space()
    lexer.op("::")
    lexer.space()
    lexer.ty("AB")
    lexer.space()
    lexer.op("->")
    lexer.space()
    lexer.ty("Int")
    lexer.space()
  }

  it should "parse all comment tokens" in {
    val lexer = Lexer(comments)
    lexer.consumeAll()
    lexer.startOffset shouldBe 0
    lexer.endOffset shouldBe comments.length
    lexer.getBufferEnd shouldBe comments.length
    lexer.getCurrentPosition.getOffset shouldBe comments.length
  }
}
