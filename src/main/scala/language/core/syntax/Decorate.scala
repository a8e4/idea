package language.core.syntax

import com.intellij.lexer
import com.intellij.lang._
import com.intellij.psi._, tree.IElementType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.{SyntaxHighlighter, SyntaxHighlighterBase, SyntaxHighlighterFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

trait Decorate extends Element with Parser { self =>
  import SyntaxHighlighterBase.pack

  object Colorize extends SyntaxHighlighterBase {
    abstract class Factory extends SyntaxHighlighterFactory {
      override def getSyntaxHighlighter(project: Project, virtualFile: VirtualFile): SyntaxHighlighter = Colorize
    }

    object Key {
      val Illegal = createTextAttributesKey("LC_ILLEGAL", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
      val Comment = createTextAttributesKey("LC_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
      val BlockComment = createTextAttributesKey("LC_NCOMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
      val DocComment = createTextAttributesKey("LC_DOCUMENT", DefaultLanguageHighlighterColors.DOC_COMMENT)
      val String = createTextAttributesKey("LC_STRING", DefaultLanguageHighlighterColors.STRING)
      val Number = createTextAttributesKey("LC_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
      val Keyword = createTextAttributesKey("LC_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
      val TypeName = createTextAttributesKey("LS_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
      val FunctionName = createTextAttributesKey("LC_FUNCTION_NAME", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
      val Parentheses = createTextAttributesKey("LC_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
      val Brace = createTextAttributesKey("LC_BRACE", DefaultLanguageHighlighterColors.BRACES)
      val Bracket = createTextAttributesKey("LC_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
      val Variable = createTextAttributesKey("LC_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
      val PragmaContent = createTextAttributesKey("LC_PRAGMA_CONTENT", DefaultLanguageHighlighterColors.IDENTIFIER)
      val Constructor = createTextAttributesKey("LC_CONSTRUCTOR", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
      val Comma = createTextAttributesKey("LC_COMMA", DefaultLanguageHighlighterColors.COMMA)
      val Semicolon = createTextAttributesKey("LC_SEMICOLON", DefaultLanguageHighlighterColors.COMMA)
      val Operator = createTextAttributesKey("LC_OPERATOR", DefaultLanguageHighlighterColors.NUMBER)
      val ReservedSymbol = createTextAttributesKey("LC_SYMBOL", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)
      val Pragma = createTextAttributesKey("LC_PRAGMA", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)
      val Quasiquote = createTextAttributesKey("LC_QUASI_QUOTES", DefaultLanguageHighlighterColors.STRING)
      val Default = createTextAttributesKey("LC_DEFAULT", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
    }

    override def getHighlightingLexer: lexer.Lexer = Lexer()
    override def getTokenHighlights(elementType: IElementType): Array[TextAttributesKey] = {
      elementType match {
        case Space                                                   => pack(null)
        case Pragma                                                  => pack(Key.Comment)
        case Comment                                                 => pack(Key.Comment)
        case Comma                                                   => pack(Key.Operator)
        case Semicolon                                               => pack(Key.Operator)
        case LParen | RParen | LBrace | RBrace | LBracket | RBracket => pack(Key.Operator)
        case Literal                                                 => pack(Key.String)
        case VaName                                                  => pack(Key.Variable)
        case TyName                                                  => pack(Key.Constructor)
        case OpName                                                  => pack(Key.Operator)
        case Number                                                  => pack(Key.Number)
        case Keyword                                                 => pack(Key.Keyword)
        case _                                                       => pack(Key.Default)
      }
    }
  }

  object Matcher {
    final val PAIRS = Array(
      new BracePair(LParen, RParen, false),
      new BracePair(LPragma, RPragma, true),
      new BracePair(LBrace, RBrace, true),
      new BracePair(LBracket, RBracket, true)
    )
  }

  abstract class Matcher extends PairedBraceMatcher {
    def getPairs: Array[BracePair] = Matcher.PAIRS

    def isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType): Boolean = {
      true // !Ids.contains(contextType) && !Literals.contains(contextType) && contextType != HS_LEFT_PAREN && contextType != HS_LEFT_BRACE && contextType != HS_LEFT_BRACKET
    }

    def getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = {
      openingBraceOffset
    }
  }
}
