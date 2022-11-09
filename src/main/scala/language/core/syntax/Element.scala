package language.core.syntax

import javax.swing.Icon
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.Language
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory, PsiNamedElement, PsiReference}

import scala.util.parsing.input.Positional
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil

import language.core, core._

package psi {
  import com.intellij.navigation.NavigationItem

  trait Element extends PsiElement
  trait Named extends PsiElement with PsiNamedElement with NavigationItem
}

object Element {
  def createElement[C <: PsiElement](
    project: Project,
    lang: Language,
    newName: String,
    namedElementClass: Class[C]
  ): Option[C] = {
    val file = createFileFromText(project, lang, newName)
    Option(PsiTreeUtil.findChildOfType(file, namedElementClass))
  }

  def createFileFromText(project: Project, lang: Language, text: String): PsiFile = {
    PsiFileFactory.getInstance(project).createFileFromText(s"a.${lang.extension}", lang, text)
  }
}

abstract class Element(val lang: Language, val icon: Icon) { self =>
  import com.intellij.lang.ASTNode
  import core.syntax.Element.{createElement}

  abstract class Token() extends Positional {
    override def toString:String = value
    def value: String

    var offset: Option[Int] = None
    var length: Option[Int] = None

    def print: String = {
      s"Token(name=${this.getClass.getSimpleName}, offset=$offset, length=$length, value=$value)"
    }

    def measure(offset: Int, length: Int) = {
      this.offset = Some(offset)
      this.length = Some(length)
      this
    }

    lazy val meta = {
      import scala.reflect.runtime.{universe => ru}
      val runtime = ru.runtimeMirror(getClass.getClassLoader)
      val symbol = runtime.classSymbol(getClass)
      val instance = runtime.reflect(self)
      val mirror = instance.reflectClass(symbol)
      val module = mirror.symbol.companion.asModule
      instance.reflectModule(module).instance.asInstanceOf[Element]
    }
  }

  abstract class Element(name: String) extends IElementType(name, lang) {
    def factory(node: ASTNode): Element = new Element(node)
    class Element(node: ASTNode) extends ASTWrapperPsiElement(node) with psi.Element
  }

  implicit def elementOp[T<:Token](self:Element.Factory[T]):Element.Op[T] = Element.Op[T](self)
  object Element {
    type Factory[T<:Token] = {
      def apply(value:String):T
    }

    case class Op[T <: Token](self:Factory[T]) {
      def token(value:String):T = self.apply(value)
    }
  }

  abstract class Named(name: String) extends Element(name) {
    override def factory(node: ASTNode): Element = new Element(node)
    class Element(node: ASTNode) extends super.Element(node) with psi.Named { self =>
      override def getName: String = this.getText

      def setName(newName: String): PsiElement = {
        val newVarid = createElement(getProject, lang, newName, this.getClass)
        newVarid.foreach(replace)
        this
      }

      override def getReference: PsiReference =
        ArrayUtil.getFirstElement(ReferenceProvidersRegistry.getReferencesFromProviders(this))

      override def getPresentation: ItemPresentation = new ItemPresentation {
        override def getPresentableText: String = self.getName
        override def getLocationString: String = self.getContainingFile.getName
        override def getIcon(b: Boolean): Icon = icon
      }
    }
  }

  abstract class Paren(name: String) extends Element(name) {
    override def factory(node: ASTNode): Element = new Element(node)
    class Element(node: ASTNode) extends super.Element(node)
  }

  case class Space(value: String) extends Token()
  object Space extends Element("Space")
  case class Comment(value: String) extends Token()
  object Comment extends Element("Comment")
  case class Pragma(value: String) extends Token()
  object Pragma extends Element("Pragma")
  case class Number(value: String) extends Token()
  object Number extends Element("Number")
  case class Comma(value: String) extends Token()
  object Comma extends Element("Comma")
  case class Semicolon(value: String) extends Token()
  object Semicolon extends Element("Semicolon")
  case class Literal(value: String) extends Token()
  object Literal extends Element("Literal")
  case class Keyword(value: String) extends Token()
  object Keyword extends Element("Keyword")
  case class VaName(value: String) extends Token()
  object VaName extends Named("VA Name")
  case class TyName(value: String) extends Token()
  object TyName extends Named("TY Name")
  case class OpName(value: String) extends Token()
  object OpName extends Named("OP Name")
  case class LParen(value: String) extends Token()
  object LParen extends Paren("L Paren")
  case class RParen(value: String) extends Token()
  object RParen extends Paren("R Paren")
  case class LBrace(value: String) extends Token()
  object LBrace extends Paren("L Brace")
  case class RBrace(value: String) extends Token()
  object RBrace extends Paren("R Brace")
  case class LBracket(value: String) extends Token()
  object LBracket extends Paren("L Bracket")
  case class RBracket(value: String) extends Token()
  object RBracket extends Paren("R Bracket")
  case class LPragma(value: String) extends Token()
  object LPragma extends Paren("L Pragma")
  case class RPragma(value: String) extends Token()
  object RPragma extends Paren("R Pragma")
}
