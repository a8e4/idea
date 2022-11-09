import Common._

version := "0.0.1-SNAPSHOT"

ThisBuild / intellijBuild := "213.7172.25"
ThisBuild / intellijPluginName := "language-core"
ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity

val circeVersion = "0.14.1"
val orientDbVersion = "3.0.22"

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.14" % Test
val scalaMock = "org.scalamock" %% "scalamock" % "4.4.0" % Test
val fastParse = "com.lihaoyi" %% "fastparse" % "2.1.3"
val circeCore = "io.circe" %% "circe-core" % circeVersion
val circeParser = "io.circe" %% "circe-parser" % circeVersion
val circeYaml = "io.circe" %% "circe-yaml" % circeVersion
val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
val circeExtras = "io.circe" %% "circe-generic-extras" % circeVersion
val monixAll = "io.monix" %% "monix" % "3.4.0"
val lsp4j = "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "0.7.2"
val flexMark = "com.vladsch.flexmark" % "flexmark-all" % "0.42.12"
val parserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
val orientGraphDb = "com.orientechnologies" % "orientdb-graphdb" % orientDbVersion
val orientServer = "com.orientechnologies" % "orientdb-server" % orientDbVersion
val shapeless = "com.chuusai" %% "shapeless" % "2.3.3"
val arangoTinkerpop  = "org.arangodb" % "arangodb-tinkerpop-provider" % "2.0.2"
def blaze(key:String) = "com.blazegraph" % s"$key-jar" % "2.1.5"
def rdf4j(key:String) = "org.eclipse.rdf4j" % s"rdf4j-$key" % "3.0.0"

val packageName = "language.core.lang"

val language = Def.task(Idea.language(
  packName=packageName,lang=Lang(),base=(Compile/sourceManaged).value))

val plugin = Def.task(Idea.plugin(
  packName=packageName,lang=Lang(),
  base=Seq(
    (Compile/resourceManaged).value,
    baseDirectory.value/"../plugin/resources"),
  action=Seq(
      <action id="language.core.insertTab" class="language.core.editor.Feature$InsertTab" text="Insert" description="Insert Character"/>),
  extens=Seq(
      <projectService serviceImplementation="language.core.build.Default"/>,
      <projectService serviceImplementation="language.core.build.Command"/>,
      <projectService overrides="true"
                      serviceInterface="com.intellij.compiler.ProblemsView"
                      serviceImplementation="language.core.editor.Diagnostic$View"/>,
      <sdkType implementation="language.core.module.Module$Core"/>,
      <moduleType id="CORE" implementationClass="language.core.module.Module$ModuleType"/>,
      <configurationType implementation="language.core.build.Runner$ConfigurationType$Shell"/>,
      <configurationType implementation="language.core.build.Runner$ConfigurationType$Build"/>,
      <applicationService overrides="true"
                          serviceInterface="com.intellij.codeInsight.TargetElementUtil"
                          serviceImplementation="language.core.editor.Feature$Definition$Provider"/>,
      <applicationService serviceImplementation="language.core.editor.Feature$Listener"/>,
      <applicationService serviceImplementation="language.core.build.FileType$Listener"/>)))

val dependencies = Seq(
  scalaTest, scalaMock, monixAll, shapeless, blaze("blazegraph"),
  circeCore, circeParser, circeYaml, circeGeneric, circeExtras,
  lsp4j, flexMark, parserCombinators, fastParse, orientGraphDb, orientServer,
  rdf4j("runtime"), rdf4j("repository"), rdf4j("rio"))

val commonSettings = Seq(
  scalaVersion := "2.13.2",
  Global / intellijAttachSources := true,
  Compile / javacOptions ++= "--release" :: "11" :: Nil,
  scalacOptions in Global ++= "-target:jvm-1.8" :: compilerOptions.toList)

// val ideaSettings = Seq(
//  assemblyExcludedJars in assembly := ideaFullJars.value)

lazy val core =
  (project in file("core"))
    .enablePlugins(SbtIdeaPlugin)
    .settings(commonSettings:_*)
    .settings(
      name := "intellij-core",
      libraryDependencies ++= dependencies,
      libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      Compile / sourceGenerators += language.taskValue,
      Compile / resourceGenerators += plugin.taskValue,
      intellijPlugins ++= Seq("com.intellij.java".toPlugin, "com.intellij.properties".toPlugin),
      libraryDependencies += "com.eclipsesource.minimal-json" % "minimal-json" % "0.9.5" withSources(),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
      unmanagedResourceDirectories in Test += baseDirectory.value / "testResources")
