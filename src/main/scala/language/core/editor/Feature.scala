package language.core.editor

import java.util.Collection
import java.awt.event.{KeyEvent, KeyListener}
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.{CompletionContributor, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionType}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.{Language,documentation}, documentation.AbstractDocumentationProvider
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.{event, Editor,EditorFactory}, event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.{tree, PsiElement, PsiPolyVariantReferenceBase, PsiReference, PsiReferenceContributor, PsiReferenceRegistrar, ResolveResult}, tree.TokenSet
import com.intellij.util.ProcessingContext

object Feature {
  import language.core, core._, build.FileType

  def insertText(editor: Editor, move:Boolean, text: String): Unit = {
    WriteCommandAction.runWriteCommandAction(editor.getProject, core.runnable {
      editor.getDocument.insertString(editor.getCaretModel.getOffset, text)
      editor.getCaretModel.moveToOffset(editor.getCaretModel.getOffset + (if(move) text.length else 0))
    })
  }

  trait Initializer {
    core.service[Listener]
  }

  class Listener extends EditorFactoryListener {
    EditorFactory.getInstance().addEditorFactoryListener(this, ApplicationManager.getApplication)
    val file = core.file(s"${System.getenv("HOME")}/.local/etc/idea/keymap.json")
    case class Keymap(time:Long, keys:Map[String, String])
    object Keymap { var cache:Option[Keymap] = None }

    def keymap = {
      def load = {
        val time = core.milliTime
        val keys = core.load[Map[String, String]](file)
          .left.map(i => core.warning(i.getMessage)).right.get
        val keymap = Keymap(time,keys)
        Keymap.cache = Some(keymap)
        keymap
      }

      Keymap.cache.map { i =>
        val time = core.milliTime
        if(i.time > time - 10000) i
        else if(i.time > file.lastModified()) i.copy(time=time)
        else load
      }.getOrElse(load)
    }

    override def editorCreated(event: EditorFactoryEvent): Unit = { import core._
      event.getEditor.getContentComponent.addKeyListener(new KeyListener {
        override def keyTyped(e: KeyEvent): Unit = {}
        override def keyReleased(e: KeyEvent): Unit = {}
        override def keyPressed(e: KeyEvent): Unit = {
          val code = Seq(
            e.isControlDown.option("ctrl"),
            e.isMetaDown.option("meta"),
            e.isAltDown.option("alt"),
            e.isShiftDown.option("shift"),
            Some(e.getKeyCode.toChar.toString))
            .flatten.mkString("-")

          for(text <- keymap.keys.get(code)) {
            insertText(event.getEditor, true, text)
          }
        }
      })
    }
  }

  class InsertTab extends AnAction with Initializer {
    override def actionPerformed(e: AnActionEvent): Unit = {
      insertText(e.getRequiredData(CommonDataKeys.EDITOR), false, "\t")
    }
  }

  trait Completion {
    def completion(psi: PsiElement): List[Completion.Item]
  }

  object Completion {
    case class Item(label: String, detail: Option[String])

    abstract class Contributor(lang: Language, token: core.syntax.Element) extends CompletionContributor {
      extend(CompletionType.BASIC, psiElement().withLanguage(lang).withElementType(TokenSet.create(token.VaName)),
        new CompletionProvider[CompletionParameters]() {
          def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet):Unit = {
            for {
              x <- parameters.getPosition.option
              p <- x.getProject.option
              f <- x.containingFile
              s <- f.getFileType.cast[FileType]
              s <- s.lang.server(p)
              i <- s.completion(x)
            } {
              val x = LookupElementBuilder.create(i.label)
              val d = i.detail.map(i => s" :: $i")
              val m = d.map(x.withTailText(_)).getOrElse(x)
              resultSet.addElement(m)
            }
          }
        }
      )
    }
  }

  trait Documentation {
    def documentation(psi: PsiElement): Option[String]
  }

  object Documentation {
    abstract class Provider() extends AbstractDocumentationProvider {
      override def generateDoc(psi: PsiElement, psi1: PsiElement): String = {
        for {
          p <- psi.getProject.option
          t <- psi.getContainingFile.getFileType.cast[FileType]
          s <- t.lang.server(p)
          x <- s.documentation(psi)
        } yield x
      }.getOrElse(null)
    }
  }

  trait Definition {
    def definition(psi: PsiElement): List[core.Location]
  }

  object Definition {
    class Provider() extends TargetElementUtil {
      override def getTargetCandidates(reference: PsiReference): Collection[PsiElement] = {
        for {
          a <- reference.option
          e <- a.getElement.option
          f <- e.getContainingFile.option
          t <- f.getFileType.cast[build.FileType]
          s <- t.lang.server(e.getProject)
        } yield s.definition(e).map(_.element(e.getProject))
        }.getOrElse(Nil).asJava
    }
  }

  case class Reference(element: syntax.psi.Named, textRange: TextRange)
    extends PsiPolyVariantReferenceBase[PsiElement](element, textRange) {
    override def multiResolve(b: Boolean): Array[ResolveResult] = {
      Array()
    }
  }

  object Reference {
    class Contributor extends PsiReferenceContributor {
      def registerReferenceProviders(registrar: PsiReferenceRegistrar):Unit = {
        registrar.registerReferenceProvider(
          psiElement(classOf[syntax.psi.Named]),
          (element: PsiElement, _: ProcessingContext) =>
            element match {
              case ne: syntax.psi.Named => Array(Reference(ne, TextRange.from(0, element.getTextLength)))
              case _                    => PsiReference.EMPTY_ARRAY
            }
        )
      }
    }
  }
}
