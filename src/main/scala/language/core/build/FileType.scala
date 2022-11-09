package language.core.build

import javax.swing.Icon
import com.intellij.{lang => _lang}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileMoveEvent
import com.intellij.psi.FileViewProvider
import language.core
import core._

abstract class FileType(override val lang: Language[_], description: String, icon: Icon, extension: String*)
  extends FileType.Base(lang, description, icon, extension: _*) { self =>

  override def getIcon: Icon = icon
  override def getName: String = description
  override def getDescription: String = description
  override def getDefaultExtension: String = extension.head

  override def apply(provider: FileViewProvider): File = new File(provider)
  class File(provider: FileViewProvider) extends super.File(provider) {
    lang.server(provider.getManager.getProject).map(_.initialized)
  }

  private def server(project: Project): Option[Server] = lang.server(project)
  private def server(vfs: VirtualFile): Option[Server] = vfs.module.map(_.getProject).flatMap(server(_))

  def documentOpened(vfs: VirtualFile, editor: Editor): Unit = {
    sendDocumentOpened(vfs, editor)
  }

  def sendDocumentOpened(vfs: VirtualFile, editor: Editor): Unit = {
    server(editor.getProject).map(_.documentOpened(file(vfs), editor.getDocument.getText))
  }

  def documentClosed(vfs: VirtualFile, editor: Editor): Unit = {
    sendDocumentClosed(vfs, editor)
  }

  def sendDocumentClosed(vfs: VirtualFile, editor: Editor): Unit = {
    server(editor.getProject).map(_.documentClosed(file(vfs)))
  }

  def documentChanged(vfs: VirtualFile, editor: Editor): Unit = {
    server(editor.getProject).map(_.documentChanged(file(vfs), editor.getDocument.getText))
  }

  override def fileChanged(vfs: VirtualFile): Unit = {
    server(vfs).map(_.documentSaved(vfs.file))
  }

  override def fileDeleted(vfs: VirtualFile): Unit = {
    server(vfs).map(_.documentDeleted(vfs.file))
  }
}

object FileType {
  abstract class Base(val lang: _lang.Language, val description: String, val icon: Icon, val extension: String*)
    extends LanguageFileType(lang) { self =>

    override def getIcon: Icon = icon
    override def getName: String = description
    override def getDescription: String = description
    override def getDefaultExtension: String = extension.headOption.orNull

    def apply(provider: FileViewProvider): File = new File(provider)
    class File(provider: FileViewProvider) extends core.File.Base(this, provider, lang)

    def matchers:Seq[FileNameMatcher]
    = extension.map(new ExtensionFileNameMatcher(_))
    def fileChanged(vfs: VirtualFile): Unit = {}
    def fileMoved(event: VirtualFileMoveEvent): Unit = {}
    def fileDeleted(vfs: VirtualFile): Unit = {}
  }

  abstract class Factory(fileType: Base*) extends FileTypeFactory {
    override def createFileTypes(fileTypeConsumer: FileTypeConsumer): Unit = {
      fileType.map(i ⇒ fileTypeConsumer.consume(i, i.matchers:_*))
    }
  }

  /*
  1) FileEditorManager.getInstance(e.getProject()).getAllEditors();
  2) FileEditorManager.getInstance(e.getProject()).getSelectedEditors();
   */

  def notifyOpenDocuments(project: Project): Unit = {
    for {
      e <- EditorFactory.getInstance().getAllEditors
      f <- e.file.option
      a <- f.getFileType.cast[FileType]
    } {
      // a.sendDocumentOpened(f, e.editor)
    }
  }

  class Listener extends EditorFactoryListener with VirtualFileListener with DocumentListener {
    val parent = ApplicationManager.getApplication()

    EditorFactory.getInstance().addEditorFactoryListener(this, parent)
    VirtualFileManager.getInstance().addVirtualFileListener(this, parent)

    override def editorCreated(event: EditorFactoryEvent): Unit = {
      for (a <- event.file.option.flatMap(_.getFileType.cast[FileType])) {
        a.documentOpened(event.file, event.getEditor)
        event.getEditor.getDocument.addDocumentListener(this)
      }
    }

    override def editorReleased(event: EditorFactoryEvent): Unit = {
      for (a <- event.file.option.flatMap(_.getFileType.cast[FileType])) {
        event.getEditor.getDocument.removeDocumentListener(this)
        a.documentClosed(event.file, event.getEditor)
      }
    }

    override def fileCreated(event: VirtualFileEvent): Unit = {
      // println(s"created: ${event.getFile}")
      event.getFile.observer.map(_.fileChanged(event.getFile))
    }

    // is called even if file is deleted while ide is closed
    override def fileDeleted(event: VirtualFileEvent): Unit = {
      // println(s"deleted: ${event.getFile}")
      event.getFile.observer.map(_.fileDeleted(event.getFile))
    }

    override def fileMoved(event: VirtualFileMoveEvent): Unit = {
      // println(s"moved: ${event.getFile}")
      event.getFile.observer.map(_.fileMoved(event))
    }

    override def contentsChanged(event: VirtualFileEvent): Unit = {
      // println(s"changed: ${event.getFile}")
      event.getFile.observer.map(_.fileChanged(event.getFile))
    }

    override def documentChanged(event: DocumentEvent): Unit = {
      for (e <- EditorFactory.getInstance().getEditors(event.getDocument)) {
        e.file.getFileType.cast[FileType].map(_.documentChanged(e.file, e))
      }
    }
  }
}
