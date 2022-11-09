package language.core

import scala.concurrent.duration._
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

package object build {
  final val TIMEOUT = 10.minutes

  implicit class ServerFile(val self: VirtualFile) {
    def observer: Option[FileType.Base] = Option(self).flatMap(x => Option(x.getFileType)).flatMap(x => new Cast(x).cast[FileType.Base])
  }
}

package build {
  import com.intellij.lang
  import com.intellij.openapi.editor.Editor
  import com.intellij.psi.{PsiElement, PsiFile}
  import language.core, core.editor
  import editor.{Annotate,Diagnostic,Feature}, Annotate.Annotator, Feature.{Completion,Definition,Documentation}
  import scala.reflect.ClassTag

  abstract class Language[T <: Server: ClassTag](val name: String) extends lang.Language(name) {
    def server(project: Project): Option[Server] = project.service[T].option
  }

  abstract class Server() extends Disposable with Completion with Definition with Documentation with Annotator {
    Disposer.register(project, this)
    service[FileType.Listener]

    def name:Text
    def project:Project
    def enabled:Boolean

    def started: Boolean = _started
    private[this] var _started: Boolean = false
    private[this] var _paused: Long = 0
    lazy val initialized = initialize()

    private[build] def initialize():Unit = {
      if(enabled && !started) {
        start()
      }
    }

    override def dispose(): Unit = {
      println(s"dispose: $name")
      stop()
    }

    def paused: Boolean = {
      val time = milliTime - _paused
      val paused = time.millis < TIMEOUT
      !_started || paused
    }

    def pause():Server = this.synchronized {
      _paused = milliTime
      this
    }

    def resume():Unit = this.synchronized {
      _paused = 0
    }

    private[build] def start(): Unit = {
      println(s"start: $name $started $enabled")

      this.synchronized {
        println(s"try start server: $name $started $enabled")

        if (!started) {
          println(s"starting server: $name")
          doStart()
          _started = true
        } else {
          fatal(name, s"Unexpected start server")
        }
      }

      FileType.notifyOpenDocuments(project)
    }

    private[build] def stop(): Unit = synchronized {
      if (!started) {
        warning(s"Unexpected stop server: $name")
      } else {
        _started = false
        info(s"stopping: $name")
        doStop()
      }
    }

    override def annotate(psi:PsiFile, editor:Editor):Option[List[core.editor.Diagnostic]]
    = this.synchronized(if(paused) None else doAnnotate(psi, editor))

    def doStart(): Unit = ()
    def doStop(): Unit = ()
    def doAnnotate(psi:PsiFile, editor:Editor): Option[List[core.editor.Diagnostic]] = None

    def documentOpened(file: File, text: String): Unit = {}
    def documentClosed(file: File): Unit = {}
    def documentChanged(file: File, text: String): Unit = {}
    def documentSaved(file: File): Unit = {}
    def documentDeleted(file: File): Unit = {}
  }

  case class Default(name:Text,project:Project) extends Server() {
    def this(project:Project) = this("default",project)
    override def enabled: Boolean = false
    override def completion(psi: PsiElement): List[Completion.Item] = Nil
    override def annotate(psi: PsiFile, editor: Editor): Option[List[Diagnostic]] = None
    override def diagnostics(): List[Diagnostic] = Nil
    override def definition(psi: PsiElement): List[core.Location] = Nil
    override def documentation(psi: PsiElement): Option[String] = None
  }
}
