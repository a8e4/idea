package language.core.editor

import com.intellij.lang.annotation.{AnnotationHolder, ExternalAnnotator}
import com.intellij.openapi, openapi.editor.{Editor=>Idea}
import com.intellij.psi.PsiFile

import scala.util.Try
import language.core._
import language.core.build.FileType

object Annotate {
  case class Info(file: PsiFile, editor: Idea)
  case class Result(info: Info, diagnostics: List[Diagnostic])

  trait Annotator {
    def annotate(psi: PsiFile, editor: Idea): Option[List[Diagnostic]]
    def diagnostics():List[Diagnostic]
  }
}

class Annotate extends ExternalAnnotator[Annotate.Info, Try[Option[Annotate.Result]]] {
  import Annotate._

  override def collectInformation(file: PsiFile, editor: Idea, hasErrors: Boolean): Info
    = Info(file, editor)

  override def doAnnotate(info: Info): Try[Option[Result]]
  = Try(for {
    t <- info.editor.file.getFileType.cast[FileType]
    s <- t.lang.server(info.editor.getProject)
    a <- s.annotate(info.file, info.editor)
  } yield Result(info, a))

  override def apply(file: PsiFile, result: Try[Option[Result]], holder: AnnotationHolder): Unit = {
    for {
      option <- result
      result <- option
    } {
      file.getVirtualFile.option.map { f =>
        val view = Diagnostic.View(file.getProject)
        view.clearOldMessages(Some(f))

        view.addAllMessages(file.getProject, Some(f), result.diagnostics
            // FIXME: It would be better not to discard diagnostics
            .filter(_.file.getCanonicalPath==f.file.getCanonicalPath)
            .map(_.message(result.info.editor))) }

      for (i <- result.diagnostics) {
        if (i.isError) {
          holder.createErrorAnnotation(i.range, i.message)
        } else if (i.isWarning) {
          holder.createWarningAnnotation(i.range, i.message)
        } else {
          holder.createWeakWarningAnnotation(i.range, i.message)
        }
      }
    }
  }
}
