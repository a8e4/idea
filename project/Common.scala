import Common.Text
import sbt.{Def, _}
import org.jetbrains.sbtidea.{SbtIdeaPlugin, Keys => idea}
import org.apache.commons.text.StringEscapeUtils.escapeJava

import scala.sys.process.Process

object exec {
  def apply(command:String,args:String*):List[String] = {
    Process(command,args.toSeq).lineStream.toList
  }
}

object listFiles {
  final val isFile = new FileFilter {
    override def accept(file: File): Boolean = file.exists() && !file.isDirectory
  }

  def apply(path:File, filter:FileFilter=isFile):Seq[File] = {
    val dirs = IO.listFiles(path)
    val files = IO.listFiles(path, filter)
    files ++ dirs.flatMap(apply(_, filter))
  }
}

object Case {
  def camel(name:Text):Text = snakeToCamel(camelToSnake(name))
  def pascal(name:Text):Text = name.head.toUpper + camel(name.tail)

  def camelToSnake(name: String) = "[A-Z\\d]".r.replaceAllIn(name, {m =>
    "_" + m.group(0).toLowerCase()
  })

  def snakeToCamel(name: String) = "_([a-z\\d])".r.replaceAllIn(name, {m =>
    m.group(1).toUpperCase()
  })
}

object Runner extends AutoPlugin {
  override def requires = SbtIdeaPlugin
  override def trigger = noTrigger

  /*object autoImport {
    lazy val createPluginRunner = TaskKey[File]("createPluginRunner")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    // Create IDEA Run Configuration
    // Need to load the project after the configuration has been created
    createPluginRunner := idea.createIDEARunConfiguration.value)*/
}

object Idea {
  import scala.xml

  case class File(name:Text,icon:Text,exten:Seq[Text])
  case class Lang(name:Text,build:Text,file:Seq[File],syntax:Syntax)
  case class Syntax(comments:List[(Text,Text)],literals:List[(Text,Text)],keywords:List[Text])

  def text(x:Text):Text = s""""${escapeJava(x)}""""
  def pair(x:(Text,Text)):Text = s"(${text(x._1)},${text(x._2)})"

  def language(base:sbt.File,packName:Text,lang:Seq[Lang]):Seq[sbt.File] = {
    val packBase = packName.replaceAll("\\.","/")

    val langFile = lang.map { lang =>
      val file = base/packBase/s"${lang.name}.scala"
      IO.write(file,
s"""package $packName

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.fileTypes.LanguageFileType
import language.core._, build.FileType

object ${lang.name} extends build.Language[build.${lang.build}]("${lang.name}") {
  override def getAssociatedFileType: LanguageFileType = ${lang.file.head.name}

  object Icon {
    ${lang.file.map(i=>s"""val ${i.name} = IconLoader.getIcon("${i.icon}")""").mkString("\n    ")}
  }

  class Factory extends FileType.Factory(${lang.file.map(_.name).mkString(",")})
  ${lang.file.map(i => s"""object ${i.name} extends FileType(this, "${lang.name}", Icon.${i.name}, ${i.exten.map(i=>s""""$i"""").mkString(",")})""").mkString("\n  ")}

  class Reference extends editor.Feature.Reference.Contributor
  class Documentation extends editor.Feature.Documentation.Provider
  class Completion extends editor.Feature.Completion.Contributor(this, Syntax)

  object Syntax extends syntax.Syntax(this, Icon.${lang.file.head.name})(${lang.file.head.name}) {
    override def comments = List(${lang.syntax.comments.map(pair).mkString(",")})
    override def literals = List(${lang.syntax.literals.map(pair).mkString(",")})
    override def keywords = List(${lang.syntax.keywords.map(text).mkString(",")})
  }

  class Matcher extends Syntax.Matcher
  class Parser extends Syntax.Parser.Definition
  class Colorize extends Syntax.Colorize.Factory
}""")
      file
    }

    langFile
  }

  def plugin(base:Seq[sbt.File],packName:Text,lang:Seq[Lang],action:Seq[xml.Elem],extens:Seq[xml.Elem]):Seq[sbt.File] = {
    val pretty = new xml.PrettyPrinter(256, 2)
    val description = s"Language Plugin: ${lang.map(_.name).mkString(", ")}"

    val pluginXml =
      <idea-plugin>
        <id>language.core</id>
        <name>Language Core</name>
        <version>0.0.1-1-SNAPSHOT</version>
        <vendor email="support@a811.net" url="https://www.a811.net">A811</vendor>
        <description>{description}</description>
        <change-notes>{description}</change-notes>
        <depends>com.intellij.modules.lang</depends>
        <actions>
          {action}
        </actions>
        <extensions defaultExtensionNs="com.intellij">
          {extens}
          {lang.map { lang =>
            <fileTypeFactory implementation={s"language.core.lang.${lang.name}$$Factory"}/>
            <lang.parserDefinition language={lang.name} implementationClass={s"language.core.lang.${lang.name}$$Parser"}/>
            <lang.braceMatcher language={lang.name} implementationClass={s"language.core.lang.${lang.name}$$Matcher"}/>
            <lang.syntaxHighlighterFactory language={lang.name} implementationClass={s"language.core.lang.${lang.name}$$Colorize"}/>
            <completion.contributor language={lang.name} implementationClass={s"language.core.lang.${lang.name}$$Completion"}/>
            <lang.documentationProvider language={lang.name} implementationClass={s"language.core.lang.${lang.name}$$Documentation"}/>
            <psi.referenceContributor language={lang.name} implementation={s"language.core.lang.${lang.name}$$Reference"}/>
            <externalAnnotator language={lang.name} implementationClass={s"language.core.editor.Annotate"}/>
          }}
        </extensions>
      </idea-plugin>

    val pluginFile = base.map { base =>
      val file = base/"META-INF/plugin.xml"
      IO.write(file,
s"""<?xml version="1.0" encoding="UTF-8"?>
${pretty.format(pluginXml)}""")
      file }

    pluginFile
  }
}

// https://tpolecat.github.io/2017/04/25/scalac-flags.html
object Common {
  type Text = String

  def compilerOptions = Seq(
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
    "-language:higherKinds",             // Allow higher-kinded types
    "-language:implicitConversions",     // Allow definition of implicit functions called views
    "-language:postfixOps",
    "-language:reflectiveCalls",
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
    // "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
    "-Xfuture",                          // Turn on future language features.
    // "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
    // "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
    "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
    "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
    // "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:option-implicit",            // Option.apply used implicit view.
    "-Xlint:package-object-classes",     // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    // "-Xlint:unsound-match",              // Pattern match may not be typesafe.
    // "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    // "-Ypartial-unification",             // Enable partial unification in type constructor inference
    // "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    // "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    // "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    // "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    // "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    // "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    // "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    // "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
  )
}
