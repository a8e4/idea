package language.core.build

import com.intellij.psi.{PsiElement, PsiFile}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}

import language.core, core._
import editor.Diagnostic.Severity

object Document {
  case class Span(min:Position, max:Position)
  object Span extends Codec[Span] {
    implicit val positionEncoder:Encoder[Position] = Position.encoder
    implicit val positionDecoder:Decoder[Position] = Position.decoder
    implicit val encoder:Encoder[Span] = deriveEncoder
    implicit val decoder:Decoder[Span] = deriveDecoder
    def apply(element:PsiElement):Span = Span(element.position, element.extentPosition)
  }

  case class Location(module:String, file:String, span:Span) {
    def position = span.min.adjust(-1)
  }

  object Location extends Codec[Location] {
    implicit val encoder:Encoder[Location] = deriveEncoder
    implicit val decoder:Decoder[Location] = deriveDecoder
    def apply(element:PsiElement):Location = Location(null, element.file.getCanonicalPath, Span(element))
  }

  case class Detail(name:String, repr:Option[String]) {
    def completion = core.editor.Feature.Completion.Item(name,repr)
  }

  object Detail extends Codec[Detail] {
    implicit val decoder:Decoder[Detail] = deriveDecoder
  }

  case class Reference(id:String, definitionId:String, location:Location, detail:Detail) {
    def position = location.position
  }

  object Reference extends Codec[Reference] {
    implicit val decoder:Decoder[Reference] = deriveDecoder
  }

  case class Diagnostic(severity:String, file:String, span:Span, message:String, detail:Option[String]) {
    def diagnostic(psi:PsiFile)
    = core.editor.Diagnostic(
      severity=Severity(severity),
      message=s"$message${detail.fold("")("\n"+_)}",
      span=core.Span(psi,span.min.adjust(-1),span.max.adjust(-1)))
  }

  object Diagnostic extends Codec[Diagnostic] {
    implicit val decoder:Decoder[Diagnostic] = deriveDecoder
  }

  case class Result(file:String, diagnostics:List[Diagnostic], completions:List[Detail])
  object Result extends Codec[Result] {
    implicit val decoder:Decoder[Result] = deriveDecoder
  }
}
