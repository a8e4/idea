package language.core.build

import scala.reflect.ClassTag
import java.awt.GridLayout
import javax.swing.{Icon, JComponent, JLabel, JPanel}
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.{icons, openapi}
import openapi.compiler.CompileContext
import openapi.options.SettingsEditor
import openapi.project.Project
import openapi.util.Key
import com.intellij.ui.{RawCommandLineEditor, TextFieldWithHistoryWithBrowseButton}
import com.intellij.execution.{ExecutionResult, Executor, configurations, process, runners, ui}
import ui.{ConsoleView, ConsoleViewContentType, FragmentedSettings}
import runners.{ExecutionEnvironment, ProgramRunner}
import process.{ColoredProcessHandler, ProcessEvent, ProcessHandler, ProcessListener}
import configurations._
import configurations.{ConfigurationType => BaseConfigurationType}
import GeneralCommandLine.ParentEnvironmentType
import com.intellij.openapi.application.ApplicationManager

import java.util

object Runner {
  import language.core, core._, editor.Diagnostic
  import io.circe, circe.generic.auto._, circe.parser._, circe.syntax._

  type Process = ProcessHandler
  case class Command(command:String="build all; build -i all",workDir:String="",envVars:Map[String,String]=Map[String,String]())

  abstract class ConfigurationType extends BaseConfigurationType {
    def process:Listener.Factory
    override def getIcon:Icon = icons.AllIcons.Nodes.ModuleGroup
    override def getConfigurationTypeDescription = "Run Configuration Type"
    override def getConfigurationFactories:Array[ConfigurationFactory]
    = Array[ConfigurationFactory](Configuration.Factory(this, process))
  }

  object ConfigurationType {
    class Shell extends ConfigurationType {
      override def getDisplayName:String = "Shell"
      override def getId:String = "SHELL_RUN_CONFIGURATION"
      override def process:Listener.Factory = Listener.Shell()
    }

    class Build extends ConfigurationType {
      override def getDisplayName:String = "Build"
      override def getId:String = "BUILD_RUN_CONFIGURATION"
      override def process:Listener.Factory = Listener.Build[build.Command]()
    }
  }

  class Configuration(project:Project, factory:Configuration.Factory, name:String)
    extends RunConfigurationBase[Configuration.Settings](project, factory, name) {
    import Configuration.Settings
    var state = Command().copy(workDir = file(project.getBasePath).getCanonicalPath)

    case class Process(
      project:Project,
      config:Command,
      environment:ExecutionEnvironment,
      factory:Configuration.Factory
    ) extends CommandLineState(environment) {
      var execution:Option[ExecutionResult] = None
      def console:Option[ConsoleView] = execution.map(_.getExecutionConsole.asInstanceOf[ConsoleView])
      def message(value:String):Unit = console.map(_.print(value, ConsoleViewContentType.NORMAL_OUTPUT))

      override def execute(
        executor:com.intellij.execution.Executor,
        runner:ProgramRunner[_]
      ):ExecutionResult = {
        val result = super.execute(executor, runner)
        execution = Some(result)
        result
      }

      override def startProcess():ProcessHandler = {
        Runner.Process(project, factory.createListener(project), config)
      }
    }

    override def checkConfiguration():Unit = {
      if (state.command.trim.isEmpty) {
        throw new RuntimeConfigurationException("Command line must not be empty")
      }
    }

    override def getConfigurationEditor():Settings = Settings()
    override def getState(executor:Executor, environment:ExecutionEnvironment):RunProfileState
    = Process(project, state, environment, factory)
    override def readExternal(element:org.jdom.Element):Unit
    = state = parse(element.getAttributeValue("model").orEmpty).toOption.flatMap(_.as[Command].toOption).getOrElse(Command())
    override def writeExternal(element:org.jdom.Element):Unit = element.setAttribute("model", state.asJson.toString())
    // FIXME: Finish updating intellij API
    override def setSelectedOptions(list: util.List[FragmentedSettings.Option]): Unit = ???
  }

  object Configuration {
    case class Factory(config:BaseConfigurationType, listener:Listener.Factory) extends ConfigurationFactory(config) {
      def createListener(project:Project):Listener = listener(project)
      override def createTemplateConfiguration(project:Project) = new Configuration(project, this, "Command")
      override def getName:String = "Command Runner"
    }

    case class Settings() extends SettingsEditor[Configuration] {
      private val command = new RawCommandLineEditor()
      private val workDir = new TextFieldWithHistoryWithBrowseButton()
      private val envVars = new EnvironmentVariablesComponent()

      override def applyEditorTo(settings:Configuration):Unit
      = settings.state = settings.state.copy(
        envVars = envVars.getEnvs.asScala.toList.toMap,
        command = command.getText,
        workDir = workDir.getText)

      override def resetEditorFrom(settings:Configuration):Unit = {
        envVars.setEnvs(settings.state.envVars.asJava)
        command.setText(settings.state.command)
        workDir.setText(settings.state.workDir)
      }

      override def createEditor():JComponent = {
        envVars.setText("")
        val panel = new JPanel(new GridLayout(0, 2))
        panel.add(new JLabel("Command:"))
        panel.add(command)
        panel.add(new JLabel("Working Directory:"))
        panel.add(workDir)
        panel.add(new JLabel("Environment:"))
        panel.add(envVars)
        panel
      }
    }
  }

  object Process {
    def apply(project: Project, listener: Listener, config: Command): Process = {
      val workDir = Option(config.workDir.trim).filter(!_.isEmpty).getOrElse(project.getBasePath)
      val commandLine: GeneralCommandLine = new GeneralCommandLine().withParentEnvironmentType(ParentEnvironmentType.CONSOLE)
      commandLine.setExePath("/bin/sh")
      commandLine.setWorkDirectory(workDir)
      commandLine.addParameters("-c", config.command.split(";").map(i => s".cache/build/session $i 2>&1").mkString(" && "))
      val environment = commandLine.getEnvironment
      environment.putAll(System.getenv())
      environment.putAll(config.envVars.asJava)
      val procHandler = new ColoredProcessHandler(commandLine)
      procHandler.addProcessListener(listener)
      procHandler
    }
  }

  abstract class Listener(project: Project) extends ProcessListener() {
    override def onTextAvailable(event: ProcessEvent, key: Key[_]): Unit = {}
    override def processWillTerminate(event: ProcessEvent, b: Boolean): Unit = {}
    override def startNotified(event: ProcessEvent): Unit = {}
    override def processTerminated(event: ProcessEvent): Unit = {}
  }

  object Listener {
    trait Factory {
      def apply(project: Project): Listener
    }

    abstract class Core[T <: Server : ClassTag](project: Project) extends Listener(project) {
      private val service = new ProjectOp(project).service[T].option
      private val server = service.map(_.pause())

      def addMessage(event: ProcessEvent, message: Diagnostic.Message): Unit = {}
      def addMessages(event: ProcessEvent, messages: List[Diagnostic.Message]): Unit = messages.map(addMessage(event, _))
      override def startNotified(event: ProcessEvent): Unit = {}
      override def processTerminated(event: ProcessEvent): Unit
      = for(s <- server) {
          ApplicationManager.getApplication.runReadAction(compute(addMessages(event, s.diagnostics().map(Diagnostic.Message(project, _)))))
          s.resume()
        }
    }

    object Shell {
      def apply(): Factory = new Factory {
        def apply(project: Project): Shell = new Shell(project) {}
      }
    }

    abstract class Shell(project: Project) extends Listener(project) {}

    object Build {
      def apply[T <: core.build.Server : ClassTag](): Factory = new Factory() {
        def apply(project: Project): Build[T] = new Build[T](project) {}
      }
    }

    abstract class Build[T <: core.build.Server : ClassTag](project: Project) extends Core[T](project) {
      override def addMessages(event: ProcessEvent, messages: List[Diagnostic.Message]): Unit
      = Diagnostic.View(project).addBuildMessages(event.getExitCode == 0, messages)
    }

    object Compile {
      def apply[T <: core.build.Server : ClassTag](context: CompileContext): Factory = new Factory() {
        override def apply(project: Project): Compile[T] = new Compile[T](project, context) {}
      }
    }

    abstract class Compile[T <: core.build.Server : ClassTag](project: Project, context: CompileContext) extends Core[T](project) {
      override def addMessage(event: ProcessEvent, message: Diagnostic.Message): Unit
      = context.addMessage(message.category, message.message, fileUri(message.file), message.line, message.column)
    }
  }
}
