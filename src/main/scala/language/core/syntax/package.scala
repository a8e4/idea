package language.core

import javax.swing.Icon
import com.intellij.lang.Language

package syntax {
  import com.intellij.psi.FileViewProvider
  import com.intellij.psi.PsiFile
  import language.core.build.FileType

  abstract class Syntax(lang: Language, icon: Icon)(fileType:FileType) extends Element(lang, icon) with Lexer with Token with syntax.Parser with Decorate {
    override def createFile(fileViewProvider:FileViewProvider):PsiFile = fileType(fileViewProvider)
  }
}
