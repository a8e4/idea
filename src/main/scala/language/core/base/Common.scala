package language.core.base

import java.util.Optional
import java.net.{URI, URL, URLDecoder, URLEncoder}
import java.io.{FileWriter, InputStream, PrintWriter, Reader, StringReader}
import java.util.concurrent.CompletableFuture
import java.nio.charset.StandardCharsets
import URLEncoder.encode
import URLDecoder.decode

import scala.util.Try
import scala.reflect.ClassTag
import scala.io.{BufferedSource, Codec, Source}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future, Promise, blocking}
import scala.util.parsing.combinator.{JavaTokenParsers, RegexParsers}
import com.intellij.lang
import com.intellij.openapi.util.Computable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.editor.Editor
import io.circe.Encoder
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.syntax._
import io.circe.parser.parse

/**
  * Scala Tutorials:
  * https://riptutorial.com/scala/example/22543/operator-precedence
  */

trait Common { this:Application =>
  import Function.tupled
  import cats.arrow.Arrow
  import cats.implicits._

  type Text = String
  implicit val globalExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  final val UTF8 = StandardCharsets.UTF_8
  final val SPACE_ENCODED: String = "%20"
  def milliTime = System.currentTimeMillis()

  abstract class Codec[T] {
    implicit val config:Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults
  }

  final type SIterator[T] = scala.Iterator[T]
  final type JIterator[T] = java.util.Iterator[T]
  final type JIterable[T] = java.util.Collection[T]

  implicit class Meta[A <: Object](val self: A) {
    def companion[T]: T = {
      import scala.reflect.runtime.{universe => ru}
      val runtime = ru.runtimeMirror(self.getClass.getClassLoader)
      val symbol = runtime.classSymbol(self.getClass)
      val mirror = runtime.reflectClass(symbol)
      val module = mirror.symbol.companion.asModule
      runtime.reflectModule(module).instance.asInstanceOf[T]
    }
  }

  def encodeUrl(value: String): String = encode(value, UTF8.name())
  def decodeUrl(value: String): String = decode(value, UTF8.name())

  implicit class Op[T](val self:T) {
    def ≡(that:T):Boolean = self == that
    def ≠(that:T):Boolean = self != that

    def each[A,B](f:T=>A)(g:T=>B):(A,B) = f(self) → g(self)
    def >>>[R](f:((T,T))⇒R):R = (arr[T,Function1] >>> f)(self)
    private def arr[A,F[_,_]:Arrow] = Arrow[F].lift((a: A) => (a, a))
  }

  implicit class Tuple1Op[A](val self:A) {
    def ⇨[B](that:B):(A, B) = self → that
  }

  implicit class Tuple2Op[A, B](val self:(A, B)) {
    def ⇨[T](that:T):(A, B, T) = (self._1, self._2, that)
  }

  implicit class Tuple3Op[A, B, C](val self:(A, B, C)) {
    def ⇨[T](that:T):(A, B, C, T) = (self._1, self._2, self._3, that)
  }

  implicit class Tuple4Op[A, B, C, D](val self:(A, B, C, D)) {
    def ⇨[T](that:T):(A, B, C, D, T) = (self._1, self._2, self._3, self._4, that)
  }

  trait As[A, B] {
    def apply(value: A): B
  }

  object As {
    implicit def Id[T]: As[T, T] = value => value
    implicit def Op[T]: As[T, Option[T]] = Option(_)

    implicit def NullAsType[T]:As[Null,T]
    = _.asInstanceOf[T]

    implicit object IntAsInteger extends As[Int, Integer] {
      override def apply(value: Int): Integer = Integer.valueOf(value)
    }

    implicit object StringAsInt extends As[String, Int] {
      override def apply(value: String): Int = value.toInt
    }

    implicit object StringAsLong extends As[String, Long] {
      override def apply(value: String): Long = value.toLong
    }

    implicit object StringAsJavaLong extends As[String, java.lang.Long] {
      override def apply(value: String): java.lang.Long = value.toLong
    }

    implicit object StringAsFloat extends As[String, Float] {
      override def apply(value: String): Float = value.toFloat
    }

    implicit object StringAsDouble extends As[String, Double] {
      override def apply(value: String): Double = value.toDouble
    }

    implicit object StringAsDecimal extends As[String, java.math.BigDecimal] {
      override def apply(value: String): java.math.BigDecimal = java.math.BigDecimal.valueOf(value.toDouble)
    }

    implicit object StringAsBoolean extends As[String, Boolean] {
      override def apply(value: String): Boolean = value.toBoolean
    }

    implicit object StringAsJavaBoolean extends As[String, java.lang.Boolean] {
      override def apply(value: String): java.lang.Boolean = value.toBoolean
    }

    implicit object FileAsString extends As[File, String] {
      override def apply(value:File):String = value.getCanonicalPath
    }
  }

  implicit class AsOp[A](val self: A) {
    def as[B](implicit a: As[A, B]): B = a(self)
  }

  implicit class JavaOption[T](val self: Optional[T]) {
    def asScala: Option[T] = Try(self.get).toOption
  }

  implicit class AsOption[T](val self: T) {
    def option: Option[T] = Option(self)
  }

  implicit class IterOption[T](val self: Iterable[T]) {
    def option: Option[T] = self.headOption
  }

  implicit class Cast[A](val self: A) {
    def cast[B](implicit tag: ClassTag[B]): Option[B] =
      if (!tag.runtimeClass.isInstance(self)) None
      else Some(self.asInstanceOf[B])
  }

  implicit class TryIterOp[T](val self: Iterable[Try[T]]) {
    def logErrors = self.map(_.toEither.left.map(i => println(i.getMessage)))
  }

  implicit class BooleanOp(val b: Boolean) {
    def option[A](a: => A): Option[A] = if (b) Option(a) else None
    def optionNot[A](a: => A): Option[A] = if (b) None else Option(a)
    def either[A, B](u: => A, v: => B): Either[A, B] = if (!b) Left(u) else Right(v)
  }

  implicit class IntOp(val self:Int) {
    def clamp(min:Int,max:Int):Int = math.max(min,math.min(self,max))
    def ≤(that:Int):Boolean = self <= that
    def ≥(that:Int):Boolean = self >= that
  }

  implicit class LongOp(val self:Long) {
    def ≤(that:Long):Boolean = self <= that
    def ≥(that:Long):Boolean = self >= that
  }

  final type Buffer[T] = scala.collection.mutable.Buffer[T]

  implicit class ListOb(val self:List.type) {
    val Buffer = collection.mutable.ListBuffer
  }

  implicit class SetOb(val self:Set.type) {
    val Buffer = collection.mutable.Set
  }

  implicit class MapOb(val self:Map.type) {
    val Buffer = collection.mutable.Map
  }

  implicit class StringOp(val self:String) {
    def orEmpty:String = Option(self).getOrElse("")
    def reader:Reader = new StringReader(self)
  }

  implicit class FileOp(val self:File) {
    def uri:URI = self.toURI
    def url:URL = uri.toURL
    def open:InputStream = url.openStream()
    def source[T](f:BufferedSource⇒T):T = managed(open)(i⇒f(Source.fromInputStream(i)(Codec.UTF8)))
    def text:String = source(_.mkString(""))
    def reader:Reader = text.reader
    def relativeTo(base:File):File = new File(base.toURI.relativize(self.toURI).getPath)
  }

  implicit class OptionOp[T](val self:Option[T]) {
    def unsafeOrNull:T = self.getOrElse(null.asInstanceOf[T])
    def ifNone(a: ⇒ Unit):Option[T] = self.fold[Option[T]] { a; None } { Option(_) }
    def swap[A](a:A):Option[A] = self.fold(Option(a))(_ ⇒ None)
    def replace[A](a:A):Option[A] = self.map(_ ⇒ a)
    def keep[A](f: T ⇒ A):Option[T] = self.map { a ⇒ f(a); a }
  }

  implicit class FutureOp[T](val self: CompletableFuture[T]) {
    def asScala: Future[T] = {
      val promise = Promise[T]()
      self.thenAccept(promise.success _)
      promise.future
    }
  }

  def succeed[T](implicit default:Default[T]):Future[T]
  = Future.successful(default.value)

  implicit class BiFunctorOp[A,B](val self:(A,B)) {
    def lget:A = self._1
    def rget:B = self._2
    def lmap[C](f:A⇒C):(C,B) = f(self._1) → self._2
    def rmap[C](f:B⇒C):(A,C) = self._1 → f(self._2)
    def join[C](f:(A,B)=>C):C = tupled(f)(self)
    def bmap[U,V](a:A⇒U,b:B⇒V):(U,V) = a(self._1) → b(self._2)
  }

  def await[T](f: Future[T], d: FiniteDuration): Try[T] = blocking {
    Try(Await.result(f, d))
  }

  def resource(name: String): URL = this.getClass.getClassLoader.getResource(name)
  def runnable(impl: => Unit): Runnable = () => impl
  def compute[T](impl: => T): Computable[T] = () => impl

  def managed[A <: AutoCloseable, B](resource: A)(reader: A => B): B = {
    try {
      reader(resource)
    } finally {
      resource.close()
    }
  }

  def defer(runnable: => Unit): Unit = {
    defer(() => runnable)
  }

  def defer(runnable: Runnable): Unit = {
    ApplicationManager.getApplication.invokeLater(runnable)
  }

  def load[T: Decoder](project: Project, name: String): Either[Exception, T] =
    load[T](file(project.file, s".cache/${name}.json"))

  def load[T: Decoder](file: File): Either[Exception, T] =
    for {
      _ <- file.exists().either(new Exception(s"File does not exist: $file"), ())
      x <- parse(Source.fromFile(file, "UTF-8").mkString)
      i <- x.as[T]
    } yield i

  def save[T: Encoder](project: Project, name: String, value: T): Unit =
    save[T](file(project.file, s".cache/${name}.json"), value)

  def save[T: Encoder](file: File, value: T): Unit = {
    file.getParentFile.mkdirs()
    val writer = new PrintWriter(file, "UTF-8")
    writer.print(value.asJson.toString())
    writer.close()
  }

  def tempFile[T](prefix: String, suffix: String)(action: File => T): T = {
    val p = file("/tmp/language-core")
    p.mkdirs()

    val f = java.io.File.createTempFile(prefix, suffix, p)
    f.deleteOnExit()

    val ret = action(f)
    f.delete()
    ret
  }

  def writeFile(file: File, text: String): Unit = {
    managed(new FileWriter(file))(_.write(text))
  }

  def fromFile(file:File):BufferedSource = {
    Source.fromFile(file)
  }

  def fromResource(res: String): BufferedSource = {
    Source.fromResource(res)
  }

  def editorToURIString(editor: Editor): String = {
    vfsToUri(FileDocumentManager.getInstance().getFile(editor.getDocument))
  }

  def vfsToUri(file: VirtualFile): String = {
    new URL(file.getUrl.replace(" ", SPACE_ENCODED)).toURI.toString
  }

  def renderMarkdown(md: String): String = {
    import com.vladsch.flexmark
    import flexmark.html.HtmlRenderer
    val parser = flexmark.parser.Parser.builder().build
    val renderer = HtmlRenderer.builder().build
    renderer.render(parser.parse(md))
  }

  implicit class LanguageOp(val self: lang.Language) {
    def extension: String = self.getAssociatedFileType.getDefaultExtension
  }

  case class Cache[K, V](ttl:FiniteDuration) {
    import scala.collection.mutable.{Queue, Map}
    private val queue = Queue[(Long, K)]()
    private val items = Map[K, V]()

    def get(k:K):Option[V] = synchronized {
      items.get(k)
    }

    def add(k:K, v:V): Unit = synchronized {
      val t = milliTime
      items += (k -> v)
      queue += (t -> k)
      queue.takeWhile(_._1 + ttl.toMillis < t).map(_._2).map(items.remove(_))
    }

    def remove(k:K): Unit = synchronized {
      items.remove(k)
    }
  }

  trait Parser {
    // http://www.lihaoyi.com/fastparse/#IndentationGrammars
    import fastparse._, NoWhitespace._
    val quote = "\""

    object delimit {
      // def include[_:P](start:P[String], end:P[String])
      // = P(start ~ (!end ~ AnyChar).rep.! ~ end).map(_.productIterator.mkString(""))
      def exclude[_:P](start:String, end:String)
      = P(start ~ (!end ~ AnyChar).rep.! ~ end)
    }
  }

  object Parser {
    trait Base extends RegexParsers with JavaTokenParsers {
      def any:Parser[String] = elem("any", _ ⇒ true) ^^ { _.toString }

      object delimit {
        abstract class parse extends ((Parser[String], Parser[String]) ⇒ Parser[String]) {
          def combine:(String ~ List[String] ~ Option[String]) ⇒ String
          def apply(start:Parser[String],end:Parser[String]):Parser[String]
          = (start ~ ((not(end) ~> any) *) ~ opt(end)).map(combine)
        }

        object include extends parse {
          def combine = { case a ~ b ~ c ⇒ s"$a${b.mkString("")}${c.getOrElse("")}" }
        }

        object exclude extends parse {
          def combine = { case _ ~ x ~ _ ⇒ x.mkString }
        }
      }
    }
  }
}
