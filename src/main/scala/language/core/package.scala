package language

import language.core.base._
// import scala.jdk.CollectionConverters._ // scala.collection.convert.{DecorateAsJava, DecorateAsScala}
import scala.collection.convert.{AsJavaExtensions, AsScalaExtensions}

package object core extends AsJavaExtensions with AsScalaExtensions with Common with Logging with Application
