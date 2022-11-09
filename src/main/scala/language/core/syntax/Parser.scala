package language.core.syntax

import com.intellij.lexer
import com.intellij.core.CoreASTFactory
import com.intellij.lang.{ASTNode, LightPsiParser, ParserDefinition, PsiBuilder, PsiParser}
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.{PsiFile, PsiElement, FileViewProvider}
import com.intellij.psi.tree.{IFileElementType, TokenSet, IElementType}

trait Parser extends Element with Lexer { self =>
  val FILE = new IFileElementType(lang)
  def createFile(fileViewProvider: FileViewProvider): PsiFile

  case class Parser() extends PsiParser with LightPsiParser {
    override def parse(root: IElementType, builder: PsiBuilder): ASTNode = {
      builder.setDebugMode(true)
      parseLight(root, builder)
      builder.getTreeBuilt
    }

    override def parseLight(root: IElementType, builder: PsiBuilder): Unit = {
      val rootMarker = builder.mark()

      while (builder.getTokenType != null) {
        val marker = builder.mark()
        val token = builder.getTokenType
        builder.advanceLexer()
        marker.done(token)
      }

      rootMarker.done(root)
    }
  }

  object Parser {
    abstract class Definition extends CoreASTFactory with ParserDefinition {
      override def createLexer(project: Project): lexer.Lexer = Lexer()
      override def createParser(project: Project): PsiParser = Parser()
      override def createFile(fileViewProvider: FileViewProvider): PsiFile = self.createFile(fileViewProvider)
      override def getFileNodeType: IFileElementType = FILE

      override def createElement(node: ASTNode): PsiElement =
        node.getElementType match {
          case token: Element => token.factory(node)
          case _              => throw new IllegalArgumentException(s"Unexpected element type: ${node.getElementType}")
        }

      override def createLeaf(kind: IElementType, text: CharSequence): LeafElement = {
        super.createLeaf(kind, text)
      }

      override def getStringLiteralElements: TokenSet = TokenSet.create(Literal)
      override def getCommentTokens: TokenSet = TokenSet.create(Comment)
      override def getWhitespaceTokens: TokenSet = TokenSet.create(Space)
    }
  }
}
