package language.core.syntax

import language.core
import scala.Function.tupled

trait Token extends Element with core.Parser.Base { self ⇒
  override def skipWhitespace: Boolean = false

  object Token {
    type Parser[+T] = self.Parser[T]
  }

  implicit class DelimitOp(val p:delimit.parse) {
    def parser[T<:Token](f:Element.Factory[T])(start:String,end:String):Token.Parser[T] = self.parser(p(start,end).map(f.token(_)))
    def parser[T<:Token](start:Parser[String],end:Parser[String])(f:Element.Factory[T]):Token.Parser[T] = self.parser(p(start,end).map(f.token(_)))
  }

  def keywords:List[String]
  def comments:List[(String,String)]
  def literals:List[(String,String)] = Nil

  def parser[T<:Token](p: ⇒ Token.Parser[T]):Token.Parser[T] = positioned(p)
  def space = parser("""\s""".r ^^ { Space(_) })
  def literal = internal.literals
  def single = parser("'.'".r ^^ { Literal(_) })
  def string = parser(stringLiteral ^^ { Literal(_) })
  def pragma = delimit.include.parser("$#".r, "\n")(Pragma)
  def comma = parser("," ^^ { Comma(_) })
  def semicolon = parser(";" ^^ { Semicolon(_) })
  def vaName = parser("[a-z_][a-zA-Z0-9_']*".r ^^ { VaName(_) })
  def tyName = parser("[A-Z_'][a-zA-Z0-9_']*".r ^^ { TyName(_) })
  def opName = parser("""[^\s\w\"\'\(\)\[\]\{\}\,\;]+""".r  ^^ { OpName(_) })
  def number = parser(wholeNumber ^^ { Number(_) } | floatingPointNumber ^^ { Number(_) })
  def anything = parser("""\S+""".r ^^ { Comment(_) })

  def lParen = parser("(" ^^ { LParen(_) })
  def rParen = parser(")" ^^ { RParen(_) })
  def lBrace = parser("{" ^^ { LBrace(_) })
  def rBrace = parser("}" ^^ { RBrace(_) })
  def lBracket = parser("[" ^^ { LBracket(_) })
  def rBracket = parser("]" ^^ { RBracket(_) })

  object internal {
    val comments = self.comments.map(tupled(delimit.include.parser(Comment))).reduce(_|_)
    val literals = self.literals.map(tupled(delimit.include.parser(Literal))).foldLeft(parser(string | single))(_|_)
    val keywords = self.keywords.map(s"\\b"+_+"\\b").map(_.r ^^ { Keyword(_) }).map(parser(_)).reduce(_|_)
  }

  def tokens:Parser[Token] =
    internal.comments | space | literal | comma | lParen | rParen |
    lBrace | rBrace | lBracket | rBracket | semicolon | pragma |
    internal.keywords | vaName | tyName | opName | number | anything
}
