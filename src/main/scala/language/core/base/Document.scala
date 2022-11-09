package language.core.base

import java.net.URI
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes
import com.intellij.psi.PsiElement
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.lang.Language
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiFile
import org.apache.commons.io.FilenameUtils
import scala.collection.JavaConverters._

import io.circe._
import io.circe.generic.semiauto._

trait Document extends Common with Logging { self:Application ⇒
  import Location.Lines

  def vfs(project:Project):VirtualFileSystem = project.getProjectFile.getFileSystem
  def file(file:File):File = file.getCanonicalFile
  def file(name:String):File = file(new File(name))
  def file(vfs:VirtualFile):File = file(vfs.getCanonicalPath)
  def file(psi:PsiFile):File = file(psi.getOriginalFile.getVirtualFile)
  def file(psi:PsiElement):File = file(psi.getContainingFile)
  def file(parent:File, name:String):File = self.file(new File(parent, name))
  def fileUri(file:File):String = s"file://${file.getAbsolutePath}"
  def parseUri(uri:String):File = file(new URI(uri).getPath)
  def fileName(file:File):File = new File(file.getName)
  def baseName(file:File):String = baseFile(file).getName
  def baseFile(file:File):File = this.file(FilenameUtils.removeExtension(file.getCanonicalPath))
  def extension(file:File):String = FilenameUtils.getExtension(file.getName)

  final type File = java.io.File
  final implicit val fileEncoder:Encoder[File]
  = a ⇒ Json.fromString(a.getCanonicalPath)
  final implicit val fileDecoder:Decoder[File]
  = a ⇒ a.value.asString.toRight(DecodingFailure(s"String Expected: $a", a.history)).map(file(_))

  object File {
    abstract class Base(self:FileType, provider:FileViewProvider, lang:Language) extends PsiFileBase(provider, lang) {
      override def toString:String = self.getName
      override def getFileType:fileTypes.FileType = self

      private var status:Long = 0
      private var _lines:Option[Lines] = None

      def lines:Lines = {
        if (status != this.getModificationStamp || _lines.isEmpty) {
          status = this.getModificationStamp
          _lines = Some(Lines(this.getText))
        }

        val lines = _lines.get
        assert(lines.length == this.getTextLength)
        lines
      }
    }
  }

  implicit class PositionOp(val self:LogicalPosition) {
    def position: Position = Position(self)
  }

  def ignore[T](x: T): Unit = {}

  def profile[R](label: String)(f: => R): R = {
    val time = System.currentTimeMillis()
    val ret = f
    println(s"$label: ${System.currentTimeMillis() - time}")
    ret
  }

  case class Position(line:Int, column:Int) {
    def adjust(offset:Int):Position
    = this.adjust(offset,offset)

    def adjust(line:Int,column:Int):Position
    = this.copy(line=this.line+line,column=this.column+column)

    def offset(psi:PsiFile): Int
    = psi.lines.offset(this)

    def location(psi:PsiFile):Location = {
      Location(file(psi),offset(psi))
    }
  }

  object Position extends Codec[Position] {
    implicit val encoder:Encoder[Position] = deriveEncoder
    implicit val decoder:Decoder[Position] = deriveDecoder
    def apply(position: LogicalPosition): Position = {
      Position(position.line, position.column)
    }
  }

  case class Location(file: File, offset: Int) {
    def vfs(project: Project): VirtualFile = project.vfs(file)
    def psi(project: Project): PsiFile = project.psiManager.findFile(vfs(project))
    def element(project: Project): PsiElement = psi(project).findElementAt(offset)
    def position(project: Project): Position = Lines(project.psi(file).getText).position(offset)
  }

  object Location {
    def apply(project: Project, file: File, position: Position): Location = {
      Location(file, position.offset(project.psi(file)))
    }

    case class Line(offset: Int, text: String) {
      def length = text.length
    }

    case class Lines(text: String) extends Iterable[Line] {
      def length = text.length
      def fold(x: List[Line], i: String): List[Line] =
        Line(x.headOption.fold(0)(i => i.offset + i.text.length + 1), i) :: x

      val lines =
        text.lines.iterator().asScala.toList
          .foldLeft[List[Line]](List[Line]())(fold)
          .reverse
          .toArray

      def apply(i: Int): Line = lines(i)
      def iterator: Iterator[Line] = lines.iterator
      def offset(p: Position): Int = lines(p.line).offset + p.column

      def position(p: Int): Position = {
        assert(p < length)
        val i = lines.indexWhere(i => p < i.offset + i.length)
        Position(i, p - lines(i).offset)
      }
    }
  }

  case class Span(location: Location, size: Int) {
    def range: TextRange = new TextRange(location.offset, location.offset + size)
  }

  object Span {
    def apply(psi:PsiFile,min:Position,max:Position):Span = {
      val loc = min.location(psi)
      Span(loc, max.location(psi).offset-loc.offset)
    }

    def apply(psi:PsiFile, position:Position):Span = {
      val min = position.location(psi)
      val elm = psi.findElementAt(min.offset)
      Span(elm.location, elm.getTextLength)
    }

    def apply(project:Project,file:File,min:Position,max:Position):Span = {
      apply(project.psi(file),min,max)
    }
  }

  implicit class PsiElementOp(val self: PsiElement) {
    def file: File = Document.this.file(self)

    def location: Location = {
      Location(file, self.getTextOffset)
    }

    def position: Position = {
      val p = self.getProject
      location.position(p)
    }

    def extent: Location = {
      location.copy(offset = self.getTextOffset + self.getTextLength)
    }

    def extentPosition: Position = {
      self.extent.position(self.getProject)
    }

    def containingFile:Option[PsiFile]
    = self.getContainingFile.option.orElse(
      self.getOriginalElement.option.flatMap(
        _.getContainingFile.option))
  }

  implicit class PsiFileOp(val self: PsiFile) {
    def lines: Lines = self.cast[File.Base].fold(Lines(self.getText))(_.lines)
  }

  implicit class VirtualFileOp(val self: VirtualFile) {
    def path: String = file.getAbsolutePath
    def file: File = Document.this.file(self)

    def project: Option[Project] =
      self.option.flatMap(
        i =>
          ProjectManager
            .getInstance()
            .getOpenProjects
            .find(p => i.path.startsWith(p.path))
      )

    def module(project: Project): Option[Module] =
      ModuleManager
        .getInstance(project)
        .getModules
        .find(_.getModuleContentScope.accept(self))

    def module: Option[Module] = project.flatMap(module(_))
  }
}
