package language.core.build

import java.io.File
import org.apache.commons.io.FilenameUtils.{getExtension=>extension}
import com.intellij.execution.{ExecutorRegistry, ProgramRunnerUtil, RunManager}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompileContext, CompilerManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import io.circe.parser.parse
import language.core, core._
import editor.Feature.Completion
import scala.concurrent.Future
import scala.collection.mutable.{Map => MutableMap}
import scala.util.Try

object Command {
  private[Command] val CODE = Array("error","warning","info")
}

case class Command(name:String, project: Project) extends Server() {
  def this(project:Project) = this("command",project)

  final val cmds = Document
  val shell = s"${project.file}/.cache/build/session"
  val build = s"$shell build"
  def path(file:File):File = file.getCanonicalFile

  val cache = MutableMap[File, List[Completion.Item]]()
  override def enabled:Boolean = project.fileExists(".cache/build/session")

  override def initialize():Unit = {
    super.initialize()

    if(enabled) {
      CompilerManager.getInstance(project).addBeforeTask((_:CompileContext) ⇒ {
        info(project, s"build: ${project.getBasePath}")

        ApplicationManager.getApplication.invokeAndWait(runnable {
          val runManager = RunManager.getInstance(project)
          val configuration = runManager.createConfiguration("build", classOf[Runner.ConfigurationType.Build])
          val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
          ProgramRunnerUtil.executeConfiguration(configuration, executor)
        })

        true
      })
    }
  }

  def locate(method:String,psi:PsiElement):List[cmds.Reference] = {
    for {
      pos <- Try(psi.position)
      json <- psi.getProject.run(s"$build -q $method ${path(psi.file)} ${pos.line+1} ${pos.column+1}")
      pret <- parse(json).toTry
      retn <- pret.as[List[cmds.Reference]].toTry
    } yield retn
  }.getOrElse(Nil)

  override def documentation(psi:PsiElement):Option[String] = for {
    e ← this.locate("info",psi).headOption
  } yield s"""<div><div>${e.detail.name} ${e.detail.repr.fold("")(" :: " + _)}</div><div><a href="file://${psi.getProject.file(e.location.file).toURI.toURL}">${e.definitionId}</a></div></div>"""

  override def definition(psi:PsiElement):List[core.Location] = for {
    a ← locate("defn-refs",psi)
  } yield core.Location(psi.getProject, psi.getProject.file(a.location.file), a.location.position)

  override def completion(psi:PsiElement):List[Completion.Item]
  = cache.get(psi.location.file).getOrElse(Nil)

  override def doAnnotate(psi:PsiFile, editor:Editor):Option[List[core.editor.Diagnostic]] = {
    val project = psi.getProject
    val file = path(psi.file)
    val temp = core.file(project.file,s".cache/temp/session.${extension(file.getPath)}")

    core.writeFile(temp,psi.getText)
    // ApplicationManager.getApplication.runReadAction(core.runnable {psi.getContainingDirectory.add(psi)})
    val build = project.run(s"${this.build} -f $file -t $temp")

    val retn = for {
      s ← project.run(s"${this.build} -s $file").toOption.toList
      a ← parse(s).left.map(System.err.println(_)).toOption.toList
      b ← a.as[cmds.Result].left.map(System.err.println(_)).toOption.toList
    } yield {
      info(s"file: ${b.file}")
      info(s"diagnostics: ${b.diagnostics.take(2)}")
      info(s"completions: ${b.completions.take(4)}")
      cache += (core.file(psi) → b.completions.map(_.completion))
      b.diagnostics.filter(i=>file.toString.equals(i.file)).map(_.diagnostic(psi))
    }

    for(_ <- build) Future(project.run(s"${this.build} -i -f $file"))
    Some(retn.flatten)
  }

  override def diagnostics():List[editor.Diagnostic] = {
    val retn:Option[List[editor.Diagnostic]] = for {
      a <- project.run(s"${this.build} -d").toOption
      b <- parse(a).left.map(System.err.println(_)).toOption
      c <- b.as[List[cmds.Diagnostic]].left.map(System.err.println(_)).toOption
    } yield {
      info(s"diagnostics: ${c.take(2)}")

      ApplicationManager.getApplication.runReadAction(core.compute(
        c.map(i => i.diagnostic(project.psi(project.file(i.file))))))
    }

    retn.getOrElse(Nil)
  }

  override def documentClosed(file:core.File):Unit = {
    cache -= file
  }
}
