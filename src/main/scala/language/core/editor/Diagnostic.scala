package language.core.editor

import com.intellij.compiler.ProblemsView
import com.intellij.pom.Navigatable
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.ide.errorTreeView.ErrorTreeElement
import com.intellij.openapi.compiler.CompilerMessage
import com.intellij.compiler.progress.CompilerTask
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import java.util.UUID

import com.intellij.compiler.impl.ProblemsViewPanel
import com.intellij.openapi.compiler.CompileScope
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.compiler.CompilerMessageImpl
import com.intellij.ide.errorTreeView.ErrorTreeElementKind
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.icons.AllIcons
import java.util

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.ide.errorTreeView.GroupingElement
import com.intellij.openapi.util.TextRange
import com.intellij.util.ui.UIUtil

import Diagnostic._

case class Diagnostic(span: language.core.Span, severity: Severity, message: String) {
  import Diagnostic.Editor

  def file = span.location.file
  def range: TextRange = span.range
  def isInfo: Boolean = severity == Severity.INFO
  def isWarning: Boolean = severity == Severity.WARNING
  def isError: Boolean = severity == Severity.ERROR

  def message(editor: Editor): CompilerMessage = {
    val position = span.location.position(editor.getProject)

    new CompilerMessageImpl(
      editor.getProject,
      severity,
      message,
      span.location.vfs(editor.getProject),
      position.line + 1,
      position.column + 1,
      null
    )
  }
}
object Diagnostic {
  import language.core, core._
  type Editor = com.intellij.openapi.editor.Editor

  type Severity = CompilerMessageCategory
  object Severity {
    val INFO = CompilerMessageCategory.INFORMATION
    val WARNING = CompilerMessageCategory.WARNING
    val ERROR = CompilerMessageCategory.ERROR

    def apply(level: String): Severity = level.toLowerCase match {
      case "error"   => ERROR
      case "warning" => WARNING
      case _         => INFO
    }
  }

  object Message {
    def apply(project:Project,diag:Diagnostic):Message = {
      val position = diag.span.location.position(project)
      Message(level = diag.severity.toString.toLowerCase, file = diag.file, line = position.line, column = position.column, message = diag.message)
    }
  }

  case class Message(level: String, file: File, line: Int, column: Int, message: String = "") {
    def category: CompilerMessageCategory =
      if (level == "error") CompilerMessageCategory.ERROR else CompilerMessageCategory.WARNING

    def diagnostic(project: Project): Diagnostic = {
      val location = Location(project, file, Position(line - 1, column - 1))
      val element = location.element(project)

      Diagnostic(
        severity = category,
        message = message,
        span = Span(size = element.option.fold(0)(_.getTextLength), location = location))
    }
  }

  def View(project: Project): View = ProblemsView.SERVICE.getInstance(project).asInstanceOf[View]

  class View(project: Project, toolWindowManager: ToolWindowManager) extends ProblemsView(project) {
    private final val ProblemsToolWindowId = "Diagnostics"
    private final val ActiveIcon = AllIcons.Toolwindows.Problems
    private final val PassiveIcon = IconLoader.getDisabledIcon(ActiveIcon)
    private val viewUpdater = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Diagnostics View Pool")
    private val problemsPanel = new ProblemsViewPanel(project)

    Disposer.register(project, () => {
      Disposer.dispose(problemsPanel)
    })

    UIUtil.invokeLaterIfNeeded(() => {
      if (!project.isDisposed) {
        val toolWindow =
          toolWindowManager.registerToolWindow(ProblemsToolWindowId, false, ToolWindowAnchor.LEFT, project, true)
        val content = ContentFactory.SERVICE.getInstance.createContent(problemsPanel, "", false)
        content.setHelpId("reference.problems.tool.window")
        toolWindow.getContentManager.addContent(content)
        Disposer.register(project, () => {
          toolWindow.getContentManager.removeAllContents(true)
        })
        updateIcon()
      }
    })

    def clearOldMessages(currentFile: Option[VirtualFile]): Unit = {
      viewUpdater.execute(() => {
        cleanupChildrenRecursively(
          problemsPanel.getErrorViewStructure.getRootElement.asInstanceOf[ErrorTreeElement],
          currentFile
        )
        updateIcon()
        problemsPanel.reload()
      })
    }

    def clearOldMessages(scope: CompileScope, currentSessionId: UUID): Unit = {
      // This method can be called. Do not know the reason. See issue #419.
      // For now do nothing because do not know why this method is called.
    }

    override def addMessage(
      messageCategoryIndex: Int,
      text: Array[String],
      groupName: String,
      navigatable: Navigatable,
      exportTextPrefix: String,
      rendererTextPrefix: String,
      sessionId: UUID
    ): Unit = {
      viewUpdater.execute(() => {
        if (navigatable != null) {
          problemsPanel.addMessage(
            messageCategoryIndex,
            text,
            groupName,
            navigatable,
            exportTextPrefix,
            rendererTextPrefix,
            sessionId
          )
        } else {
          problemsPanel.addMessage(messageCategoryIndex, text, null, -1, -1, sessionId)
        }
        updateIcon()
      })
    }

    def addMessage(message: CompilerMessage): Unit = {
      val file = message.getVirtualFile
      val navigatable = if (message.getNavigatable == null && file != null && !file.getFileType.isBinary) {
        new OpenFileDescriptor(myProject, file, -1, -1)
      } else {
        message.getNavigatable
      }
      val category = message.getCategory
      val categoryIndex = CompilerTask.translateCategory(category)
      val messageText = splitMessage(message)
      val groupName = if (file != null) file.getPresentableUrl else category.getPresentableText
      addMessage(
        categoryIndex,
        messageText,
        groupName,
        navigatable,
        message.getExportTextPrefix,
        message.getRenderTextPrefix,
        null
      )
    }

    def addAllMessages(project: Project, file: Option[VirtualFile], messages: List[CompilerMessage]): Unit = {
      defer {
        if (!project.isDisposed) {
          clearProgress()
          clearOldMessages(file)
        }

        messages.foreach(addMessage)
      }
    }

    def addBuildMessages(success: Boolean, messages: List[Diagnostic.Message]): Unit = {
      // for ((file, messages) <- log.groupBy(_.file)) {
        addAllMessages(
          project,
          None, // project.vfs(file),
          messages.map(
            m =>
              new CompilerMessageImpl(
                project,
                m.category,
                m.message,
                project.vfs(m.file),
                m.line,
                m.column,
                null
              )
          )
        )
      // }

      defer {
        val (category, message) =
          if (success) (CompilerMessageCategory.INFORMATION, "Build Succeeded")
          else (CompilerMessageCategory.ERROR, "Build Failed")
        addMessage(new CompilerMessageImpl(project, category, message))
      }
    }

    override def setProgress(text: String, fraction: Float): Unit = {
      problemsPanel.setProgress(text, fraction)
    }

    override def setProgress(text: String): Unit = {
      problemsPanel.setProgressText(text)
    }

    override def clearProgress(): Unit = {
      problemsPanel.clearProgressData()
    }

    private def splitMessage(message: CompilerMessage): Array[String] = {
      val messageText = message.getMessage
      if (messageText.contains("\n")) {
        messageText.split("\n")
      } else {
        Array[String](messageText)
      }
    }

    private def cleanupChildrenRecursively(errorTreeElement: ErrorTreeElement, currentFile: Option[VirtualFile]): Unit = {
      val errorViewStructure = problemsPanel.getErrorViewStructure
      for (element <- errorViewStructure.getChildElements(errorTreeElement)) {
        element match {
          case groupElement: GroupingElement =>
            if (currentFile.isEmpty || groupElement.getFile == currentFile.get) {
              cleanupChildrenRecursively(element, currentFile)
            }
          case _ => errorViewStructure.removeElement(element)
        }
      }
    }

    private def updateIcon(): Unit = {
      UIUtil.invokeLaterIfNeeded(() => {
        if (!myProject.isDisposed) {
          val toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ProblemsToolWindowId)
          if (toolWindow != null) {
            val active = problemsPanel.getErrorViewStructure.hasMessages(
              util.EnumSet.of(ErrorTreeElementKind.ERROR, ErrorTreeElementKind.WARNING, ErrorTreeElementKind.NOTE))
            toolWindow.setIcon(if (active) ActiveIcon else PassiveIcon)
          }
        }
      })
    }
  }
}
