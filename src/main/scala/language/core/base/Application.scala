package language.core

import javax.swing.JComponent

import scala.reflect.{classTag, ClassTag}
import com.intellij.psi.PsiElement
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

import scala.sys.process.Process
import scala.util.Try

package base {
  trait Application extends Common with Logging with Document { app =>
    def service[T:ClassTag]:T = ServiceManager.getService(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    def disposeWith(parent:Disposable, disposable: Disposable*): Unit = disposable.map(Disposer.register(parent, _))
    implicit def toDisposable(closeable:AutoCloseable):Disposable = () => closeable.close()

    implicit class ProjectOp(val self: Project) {
      def path: String = file.getAbsolutePath
      def file: File = Application.this.file(self.getBasePath)

      def file(path:String):File = {
        val f = new java.io.File(path)

        if(f.isAbsolute) {
          app.file(f)
        } else {
          app.file(this.file,path)
        }
      }

      def psiManager: PsiManager = PsiManager.getInstance(self)
      // FIXME: getProjectFile returns null if misc.xml does not exist
      // Find another way to get a reference to the VFS
      def fileSystem: VirtualFileSystem = {
        val f = self.getWorkspaceFile
        val s = f.getFileSystem
        s // self.getProjectFile.getFileSystem
      }
      def service[T: ClassTag]: T = ServiceManager.getService(self, classTag[T].runtimeClass.asInstanceOf[Class[T]])
      def vfs(file: File): VirtualFile = fileSystem.findFileByPath(file.getAbsolutePath)
      def fileExists(name: String): Boolean = Application.this.file(self.file, name).exists()

      def psi(file: File): PsiFile = try {
        psiManager.findFile(vfs(file))
      } catch {
        case e:Throwable => {
          info(s"psi: ")
          throw e
        }
      }

      def run(command:String):Try[String] = {
        info(s"Command: $file: $command")
        val retn = Try(Process(command,file)!!)
        retn.toEither
          .left.map(e => logError(self,s"Failed: $e"))
          .right.map(i => info(self,s"Succeeded: "+i.take(512)))
        retn
      }
    }

    implicit class ModuleOp(val self: Module) {
      def service[T: ClassTag]: T = ModuleServiceManager.getService(self, classTag[T].runtimeClass.asInstanceOf[Class[T]])
    }

    implicit class EditorOp(val editor: Editor) {
      def component: JComponent = editor.getContentComponent
      def file: VirtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument)
      def position(psi: PsiElement): LogicalPosition = position(psi.getTextOffset)
      def position(offset: Int): LogicalPosition = editor.offsetToLogicalPosition(offset)
    }

    implicit class EventOp(val event: EditorFactoryEvent) {
      def file: VirtualFile = event.getEditor.file
    }

    abstract class Service(project:Project) extends AutoCloseable {
      def parent:Disposable = project
      def dispose(service:Disposable*):Unit
      = disposeWith(this, service:_*)
      override def close():Unit = {}
      disposeWith(parent, this)
    }
  }
}

package module {
  import javax.swing.Icon
  import org.jdom.Element
  import com.intellij.icons.AllIcons
  import com.intellij.ide.util.projectWizard.{ModuleBuilder => BaseModuleBuilder}
  import com.intellij.openapi.module.{ModifiableModuleModel, Module => BaseModule, ModuleTypeManager, ModuleType => BaseModuleType }
  import com.intellij.openapi.projectRoots._
  import com.intellij.openapi.roots.ModifiableRootModel

  object Module {
    final val ID = "CORE"
    final val NAME = "Core"
    final val EXCLUDE = Seq(".build", ".shake", ".stack-work", ".cache", ".direnv", ".envrc", ".shell")

    class ModuleType() extends BaseModuleType[ModuleBuilder](ID) {
      override def getName = ID
      override def getDescription = NAME
      override def createModuleBuilder = new ModuleBuilder
      override def getNodeIcon(b: Boolean): Icon = AllIcons.Nodes.Module
    }

    class ModuleBuilder extends BaseModuleBuilder {
      override def getModuleType: BaseModuleType[_ <: BaseModuleBuilder] = ModuleTypeManager.getInstance.findByID(ID).asInstanceOf[ModuleType]
      override def setupRootModel(model: ModifiableRootModel): Unit = doAddContentEntry(model).setExcludePatterns(EXCLUDE.asJava)
      override def createModule(moduleModel: ModifiableModuleModel): BaseModule = super.createModule(moduleModel)
    }

    class Core extends com.intellij.openapi.projectRoots.SdkType(ID) {
      override def isValidSdkHome(s: String): Boolean = true
      override def getPresentableName: String = NAME
      override def suggestSdkName(a: String, b: String): String = NAME
      override def suggestHomePath(): String = s"${sys.env("HOME")}/.local"
      override def saveAdditionalData(sdkAdditionalData: SdkAdditionalData, element: Element): Unit = {}
      override def createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable = null
    }
  }
}
