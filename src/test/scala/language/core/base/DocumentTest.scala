package language.core.base

import org.scalatest.flatspec._
import org.scalatest.matchers.should._

class DocumentTest extends AnyFlatSpec with Matchers with Common with Application {
  import Location.Lines

  val text =
    """line 1
and line 2
line 3
and 4"""
  val lines = Lines(text)

  it should "parse document into lines" in {
    lines.size shouldBe 4
    lines(0).toString shouldBe "Line(0,line 1)"
    lines(1).toString shouldBe "Line(7,and line 2)"
    lines(2).toString shouldBe "Line(18,line 3)"
    lines(3).toString shouldBe "Line(25,and 4)"

    Lines("").isEmpty shouldBe true
    Lines("\n").size shouldBe 1
    Lines("a\n").size shouldBe 1
    Lines("a\nb").size shouldBe 2
  }

  it should "translate position to offset" in {
    lines.offset(Position(0, 0)) shouldBe 0
    lines.offset(Position(0, 4)) shouldBe 4
    lines.offset(Position(1, 0)) shouldBe 7
    lines.offset(Position(1, 8)) shouldBe 15
  }

  it should "translate offset to position" in {
    lines.position(0) shouldBe Position(0, 0)
    lines.position(4) shouldBe Position(0, 4)
    lines.position(7) shouldBe Position(1, 0)
    lines.position(15) shouldBe Position(1, 8)
  }

  it should "canonicalize file path" in {
    file("test") shouldBe new File("test").getAbsoluteFile
    file("/test") shouldBe new File("/test")
    file("/test/.//1/2") shouldBe new File("/test/1/2")
    file("./test//1/././/2") shouldBe new File("test/1/2").getAbsoluteFile
  }
}
