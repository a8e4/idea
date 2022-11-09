package language.core.syntax

import com.intellij.lexer.LexerPosition
import com.intellij.psi.tree.IElementType
import scala.util.parsing.input.CharSequenceReader

trait Lexer extends Element with scala.util.parsing.combinator.RegexParsers {
  import language.core.logError
  def tokens:Parser[Token]

  object Lexer {
    case class Token(element:IElementType,value:String)
  }

  case class Lexer() extends com.intellij.lexer.Lexer { self ⇒
    def iter:Iterator[Lexer.Token] = new Iterator[Lexer.Token] {
      override def hasNext:Boolean = {
        self.getTokenType != null
      }

      override def next():Lexer.Token = {
        val token = Lexer.Token(getTokenType,getTokenText)
        self.advance()
        token
      }
    }

    case class Result(tokenStart: Int, tokenEnd: Int, result: ParseResult[Token]) {
      val value: Option[Token] = result.getOrElse(null).option.map(_.measure(tokenStart, tokenEnd - tokenStart))
      val error: Option[String] = result match {
        case NoSuccess(message, _) ⇒ Some(message)
        case _ => None
      }
    }

    case class State(buffer: CharSequence, bufferStart: Int, bufferEnd: Int, result: Either[Int, Result]) {
      def token = result.toOption.flatMap(_.value)
      def tokenStart = result.map(_.tokenStart).getOrElse(bufferStart)
      def tokenEnd = result.map(_.tokenEnd).getOrElse(tokenStart)
      def tokenText = token.map(_.value).getOrElse(null)

      def offset = result.fold(identity, _.result.next.offset)
      def next = result.fold(input _, _.result.next)
      def reset(position: LexerPosition) = this.copy(result = Left(position.getOffset))
      def input(offset: Int) = new CharSequenceReader(buffer, offset)
    }

    object State {
      def apply(buffer: CharSequence, startOffset: Int, endOffset: Int): State = {
        State(buffer = buffer, bufferStart = startOffset, bufferEnd = endOffset, result = Left(startOffset))
      }
    }

    private var _state: Option[State] = None
    def state = _state

    /**
      * See {@link scala.util.parsing.input.Reader}
      * Reset position by creating a reader
      * and passing the new reader into parse
      *
      * For the lexer parse just one token at a time
      */
    def currentOffset: Int = {
      _state.map(_.offset).getOrElse(0)
    }

    def startOffset: Int = {
      _state.map(_.bufferStart).getOrElse(0)
    }

    def endOffset: Int = {
      _state.map(_.bufferEnd).getOrElse(0)
    }

    override def start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = {
      _state = Some(State(buffer, startOffset, endOffset))
      advance()
    }

    override def advance(): Unit = {
      val haveToken = currentOffset < endOffset

      _state = for {
        s <- _state
        r = parse(tokens, s.next)
        v = Option(r.getOrElse(null)).map(_.value)
      } yield s.copy(
        result = Right(Result(tokenStart = s.offset, tokenEnd = s.offset + v.map(_.length).getOrElse(0), result = r))
      )

      if (haveToken) {
        _state
          .flatMap(_.result.toOption)
          .flatMap(_.error)
          .map(logError(_))
      }
    }

    override def restore(position: LexerPosition): Unit = {
      _state = _state.map(_.reset(position))
      advance()
    }

    override def getState: Int = 0
    override def getBufferSequence(): CharSequence = {
      _state.map(_.buffer).orNull
    }

    override def getBufferEnd: Int = {
      _state.map(_.bufferEnd).getOrElse(0)
    }

    override def getCurrentPosition: LexerPosition = {
      new LexerPosition {
        override def getOffset: Int = self.currentOffset
        override def getState: Int = self.getState()
      }
    }

    override def getTokenType: IElementType = {
      _state.flatMap(_.token).map(_.meta).orNull
    }

    override def getTokenStart: Int = {
      _state.map(_.tokenStart).getOrElse(0)
    }

    override def getTokenEnd: Int = {
      _state.map(_.tokenEnd).getOrElse(0)
    }

    override def getTokenText: String = {
      _state.map(_.tokenText).orNull
    }
  }
}
