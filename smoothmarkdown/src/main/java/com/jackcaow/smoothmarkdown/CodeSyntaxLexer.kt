package com.jackcaow.smoothmarkdown

/** Owned deterministic code lexer. Token offsets use Kotlin UTF-16 indices. */
internal enum class CodeTokenKind { KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION, PROPERTY, OPERATOR, PUNCTUATION, DIFF_ADD, DIFF_REMOVE }
internal data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind)

internal fun normalizedCodeLanguage(language: String?): String? {
    val raw = language?.trim()?.lowercase()?.split(Regex("[\\s,]+"))?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
    return when (raw) {
        "js", "jsx", "javascript", "node" -> "javascript"
        "ts", "tsx", "typescript" -> "typescript"
        "kt", "kts", "kotlin" -> "kotlin"
        "java" -> "java"
        "c", "h" -> "c"
        "cc", "cpp", "c++", "cxx", "hpp", "hh", "hxx" -> "cpp"
        "cs", "c#", "csharp" -> "csharp"
        "go", "golang" -> "go"
        "rs", "rust" -> "rust"
        "php", "php3", "php4", "php5", "phtml" -> "php"
        "rb", "ruby" -> "ruby"
        "dart" -> "dart"
        "scala", "sc" -> "scala"
        "sql", "mysql", "pgsql", "postgres", "postgresql", "sqlite" -> "sql"
        "lua" -> "lua"
        "r" -> "r"
        "m", "mm", "objc", "objective-c", "objectivec" -> "objectivec"
        "swift" -> "swift"
        "py", "python", "python3" -> "python"
        "sh", "bash", "zsh", "shell", "shellscript", "powershell", "ps1" -> "shell"
        "json", "jsonc" -> "json"
        "yaml", "yml" -> "yaml"
        "diff", "patch" -> "diff"
        "html", "xml", "svg", "vue" -> "markup"
        "css", "scss", "sass" -> "css"
        "md", "markdown" -> "markdown"
        "text", "txt", "plain", "plaintext" -> "plain"
        else -> raw
    }
}

private val codeKeywordSets = mapOf(
    "javascript" to "await async break case catch class const continue default delete do else export extends finally for from function if import in instanceof let new of return switch this throw try typeof var void while yield interface type enum implements private protected public null true false".split(' ').filter { it.isNotEmpty() }.toSet(),
    "typescript" to "await async break case catch class const continue default delete do else export extends finally for from function if import in instanceof let new of return switch this throw try typeof var void while yield interface type enum implements private protected public null true false".split(' ').filter { it.isNotEmpty() }.toSet(),
    "kotlin" to "as break class continue data do else false for fun if in interface is null object package private protected public return sealed super this throw true try typealias val var when while suspend internal import".split(' ').filter { it.isNotEmpty() }.toSet(),
    "java" to "abstract break case catch class const continue default do else enum extends final finally for if implements import instanceof interface new package private protected public return static super switch this throw throws try void while null true false".split(' ').filter { it.isNotEmpty() }.toSet(),
    "c" to "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while".split(' ').filter { it.isNotEmpty() }.toSet(),
    "cpp" to "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while alignas alignof and asm bitand bitor bool catch class concept const_cast constexpr decltype delete dynamic_cast explicit export false friend mutable namespace new noexcept not nullptr operator or private protected public reinterpret_cast requires static_assert static_cast template this thread_local throw true try typename using virtual xor".split(' ').filter { it.isNotEmpty() }.toSet(),
    "csharp" to "abstract as base bool break case catch char checked class const continue decimal default delegate do double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while async await var record".split(' ').filter { it.isNotEmpty() }.toSet(),
    "go" to "break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var".split(' ').filter { it.isNotEmpty() }.toSet(),
    "rust" to "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while".split(' ').filter { it.isNotEmpty() }.toSet(),
    "php" to "abstract and array as break callable case catch class clone const continue declare default die do echo else elseif empty enddeclare endfor endforeach endif endswitch endwhile eval exit extends final finally fn for foreach function global goto if implements include include_once instanceof insteadof interface isset list match namespace new or print private protected public readonly require require_once return static switch throw trait try unset use var while xor yield".split(' ').filter { it.isNotEmpty() }.toSet(),
    "ruby" to "alias and begin break case class def defined do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield".split(' ').filter { it.isNotEmpty() }.toSet(),
    "dart" to "abstract as assert async await break case catch class const continue covariant default deferred do dynamic else enum export extends extension external factory false final finally for function get hide if implements import in interface is late library mixin new null on operator part required rethrow return set show static super switch sync this throw true try typedef var void while with yield".split(' ').filter { it.isNotEmpty() }.toSet(),
    "scala" to "abstract case catch class def do else enum export extends false final finally for forSome given if implicit import lazy match new null object override package private protected return sealed super then this throw trait true try type val var while with yield".split(' ').filter { it.isNotEmpty() }.toSet(),
    "sql" to "add all alter and as asc between by case create delete desc distinct drop else end exists false from group having in insert into is join left like limit not null on or order outer right select set table then true union update values when where inner".split(' ').filter { it.isNotEmpty() }.toSet(),
    "lua" to "and break do else elseif end false for function goto if in local nil not or repeat return then true until while".split(' ').filter { it.isNotEmpty() }.toSet(),
    "r" to "break else false for function if in inf na nan next null repeat return true while".split(' ').filter { it.isNotEmpty() }.toSet(),
    "objectivec" to "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while @autoreleasepool @catch @class @dynamic @encode @end @finally @implementation @interface @optional @private @property @protected @protocol @public @required @selector @synthesize @throw @try id instancetype nil no self super yes".split(' ').filter { it.isNotEmpty() }.toSet(),
    "swift" to "actor as break case catch class continue default defer do else enum extension false for func guard if import in let nil private public return self struct super switch throw true try var while protocol async await".split(' ').filter { it.isNotEmpty() }.toSet(),
    "python" to "and as assert async await break class continue def elif else except false finally for from global if import in is lambda none not or pass raise return true try while with yield".split(' ').filter { it.isNotEmpty() }.toSet(),
    "shell" to "case do done elif else esac export fi for function if in local then while".split(' ').filter { it.isNotEmpty() }.toSet(),
    "markup" to "html head body div span script style template section".split(' ').filter { it.isNotEmpty() }.toSet(),
    "css" to "@media @supports from to important".split(' ').filter { it.isNotEmpty() }.toSet(),
    "markdown" to "todo fixme note".split(' ').filter { it.isNotEmpty() }.toSet(),
    "json" to "".split(' ').filter { it.isNotEmpty() }.toSet(),
    "yaml" to "".split(' ').filter { it.isNotEmpty() }.toSet(),
)
private val slashCodeComments = setOf("javascript", "typescript", "kotlin", "java", "c", "cpp", "csharp", "go", "rust", "php", "dart", "scala", "objectivec", "swift", "css")
private val hashCodeComments = setOf("python", "shell", "yaml", "markdown", "ruby", "r", "php")
private val typedCodeLanguages = slashCodeComments + setOf("python", "ruby") - "css"

internal fun tokenizeCode(code: String, language: String?): List<CodeToken> {
    val lang = normalizedCodeLanguage(language) ?: return emptyList()
    if (lang == "diff") {
        val tokens = mutableListOf<CodeToken>(); var start = 0
        while (start < code.length) {
            val end = code.indexOf('\n', start).let { if (it < 0) code.length else it }
            val kind = when {
                code.startsWith("+", start) && !code.startsWith("+++", start) -> CodeTokenKind.DIFF_ADD
                code.startsWith("-", start) && !code.startsWith("---", start) -> CodeTokenKind.DIFF_REMOVE
                code.startsWith("@@", start) -> CodeTokenKind.KEYWORD
                else -> null
            }
            if (kind != null) tokens += CodeToken(start, end, kind)
            start = end + 1
        }
        return tokens
    }
    val keywords = codeKeywordSets[lang] ?: return emptyList()
    val tokens = mutableListOf<CodeToken>(); var index = 0
    fun afterWhitespace(at: Int): Char? {
        var end = at; while (end < code.length && code[end].isWhitespace()) end++
        return code.getOrNull(end)
    }
    fun emit(start: Int, kind: CodeTokenKind) { tokens += CodeToken(start, index, kind) }
    while (index < code.length) {
        val start = index; val ch = code[index]
        val commentEnd = when {
            lang == "markup" && code.startsWith("<!--", index) -> "-->"
            (lang in slashCodeComments || lang == "markup") && code.startsWith("/*", index) -> "*/"
            else -> null
        }
        if (commentEnd != null) {
            val end = code.indexOf(commentEnd, index + 2)
            index = if (end < 0) code.length else end + commentEnd.length
            emit(start, CodeTokenKind.COMMENT); continue
        }
        if ((lang in slashCodeComments && code.startsWith("//", index)) ||
            (lang in setOf("sql", "lua") && code.startsWith("--", index)) ||
            (lang in hashCodeComments && ch == '#')) {
            index = code.indexOf('\n', index).let { if (it < 0) code.length else it }
            emit(start, CodeTokenKind.COMMENT); continue
        }
        if (ch == '"' || ch == '\'' || ch == '`') {
            index++
            while (index < code.length) {
                if (code[index] == '\\') index = (index + 2).coerceAtMost(code.length)
                else if (code[index++] == ch) break
            }
            emit(start, if (lang in setOf("json", "yaml") && afterWhitespace(index) == ':') CodeTokenKind.PROPERTY else CodeTokenKind.STRING)
            continue
        }
        if (ch.isDigit() || (ch == '-' && code.getOrNull(index + 1)?.isDigit() == true)) {
            index++
            while (index < code.length && (code[index].isDigit() || code[index] == '.' || code[index] == '_')) index++
            if (code.getOrNull(index)?.lowercaseChar() == 'e') {
                index++
                if (code.getOrNull(index) == '+' || code.getOrNull(index) == '-') index++
                while (code.getOrNull(index)?.isDigit() == true) index++
            }
            emit(start, CodeTokenKind.NUMBER); continue
        }
        if (ch.isLetter() || ch == '_' || ch == '$' || ch == '@') {
            index++
            while (index < code.length && (code[index].isLetterOrDigit() || code[index] == '_' || code[index] == '$')) index++
            val word = code.substring(start, index)
            val kind = when {
                word.lowercase() in keywords || word.lowercase() in setOf("true", "false", "null", "nil", "none") -> CodeTokenKind.KEYWORD
                lang in setOf("json", "yaml") && afterWhitespace(index) == ':' -> CodeTokenKind.PROPERTY
                lang in typedCodeLanguages && word.first().isUpperCase() -> CodeTokenKind.TYPE
                afterWhitespace(index) == '(' -> CodeTokenKind.FUNCTION
                else -> null
            }
            if (kind != null) emit(start, kind)
            continue
        }
        index++
        if (ch in "=+-*/%!<>&|?:") emit(start, CodeTokenKind.OPERATOR)
        else if (ch in "(){}[].,;") emit(start, CodeTokenKind.PUNCTUATION)
    }
    return tokens
}
