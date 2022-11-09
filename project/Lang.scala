// Minimalistic HaskellLike Language
// https://github.com/bef0/write-you-a-haskell
// https://github.com/bydriv/mhc

object Lang {
  import Idea.{Lang=>lang,Syntax=>syntax,File=>file}
  def apply():Seq[lang] = all

  val defaultIcon = "/language/core/haskell/icon/alex.png"
  val makefileIcon = "/language/core/clang/icon/makefile.gif"

  val haskell = syntax(
    comments = List("--" → "\n", "{-" → "-}"),
    literals = List("[i|" → "|]", "[text|" → "|]"),
    keywords = List(
      "infix", "infixr", "infixl", "module", "where", "import", "qualified", "as", "data", "type", "newtype",
      "class", "instance", "deriving", "family", "do", "let", "in", "hiding", "case", "of", "if", "then", "else"))

  val idris = haskell.copy(
    keywords = "public"::"export"::"record"::"with"::"using"::"idiom"::haskell.keywords)

  val clang = syntax(
    comments = List("//" → "\n", "/*" → "*/"),
    literals = List("\"" → "\"", "'" → "'"),
    keywords = List(
      "while", "do", "for", "if", "else", "break", "continue", "public", "private", "protected", "virtual",
      "volatile", "auto", "const", "signed", "unsigned", "struct", "class", "template", "typename", "namespace",
      "using", "typedef", "void", "char", "short", "int", "long", "float", "double", "return", "extern", "new",
      "delete"))

  val all = Seq(
    lang(name="Agda",build="Default",
      syntax=haskell.copy(keywords="open"::haskell.keywords),
      file=Seq(file(name="Source",icon=defaultIcon,exten=Seq("agda","as")))),
    lang(name="CMM",build="Default",syntax=clang,file=Seq(
      file(name="Source",icon=defaultIcon,exten=Seq("cmm")))),
    lang(name="CXX",build="Default",syntax=clang,file=Seq(
      file(name="Source",icon="/language/core/clang/icon/c_file_obj.gif",exten=Seq("c","cc","cpp","cxx")),
      file(name="Header",icon="/language/core/clang/icon/h_file_obj.gif",exten=Seq("h","hh","hpp","hxx")))),
    lang(name="DS",build="Default",syntax=haskell,
      file=Seq(file(name="Source",icon=defaultIcon,exten=Seq("ds","conf")))),
    lang(name="Grin",build="Default",syntax=haskell,file=Seq(
      file(name="Source",icon=defaultIcon,exten=Seq("grin","gs")))),
    lang(name="Haskell",build="Command",syntax=haskell,file=Seq(
      file(name="Source",icon="/language/core/haskell/icon/hs16.png",exten=Seq("hs")))),
    lang(name="Idris",build="Default",syntax=idris,file=Seq(
      file(name="Source",icon=defaultIcon,exten=Seq("idr","is")))),
    lang(name="Julia",build="Default",
      syntax=clang.copy(keywords="function"::"end"::"local"::"global"::clang.keywords),
      file=Seq(file(name="Source",icon=defaultIcon,exten=Seq("jl")))),
    lang(name="LLVM",build="Default",syntax=clang,file=Seq(
      file(name="Source",icon=defaultIcon,exten=Seq("ll","ir")))),
    lang(name="ML",build="Default",
      syntax=haskell.copy(
        comments=List("(*"->"*)"),
        keywords=
          "open"::"local"::"val"::"fun"::"exception"::
          "signature"::"structure":: "end"::"struct"::
          "andalso"::"raise"::"datatype"::"sig"::haskell.keywords),
      file=Seq(file(name="Source",icon=defaultIcon,exten=Seq("ml", "sml", "sig","ats","sats","cats","dats","hats")))),
    lang(name="Ninja",build="Default",
      syntax=clang.copy(keywords=List("ninja_required_version"),comments=List("#"->"\n")),
      file=Seq(file(name="Source",icon=makefileIcon,exten=Seq("ninja")))),
    lang(name="Nix",build="Default",
      syntax=syntax(
        comments=("#","\n")::("/*","*/")::Nil,
        literals=("''","''")::clang.literals,
        keywords=List("let","in","import","with","if","then","else","abort","rec","inherit","builtins")),
      file=Seq(file(name="Source",icon=defaultIcon,exten=Seq("nix")))),
    lang(name="Zig",build="Default",
      syntax=clang.copy(keywords="pub"::"fn"::"var"::clang.keywords),
      file=Seq(file(name="Source",icon=defaultIcon,exten=Seq("zig","zs")))))
}