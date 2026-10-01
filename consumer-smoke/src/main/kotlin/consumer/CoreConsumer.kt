package consumer
import com.jackcaow.smoothmarkdown.MarkdownCoreParser
import com.jackcaow.smoothmarkdown.ast.Heading
fun main() {
    val parser = MarkdownCoreParser()
    check(parser.parse("# Hello").firstChild is Heading)
    check(parser.renderHtml("**bold**") == "<p><strong>bold</strong></p>\n")
    check(parser.renderHtml("- [x] Done").contains("checked"))
    println("Public core consumer passed without Android or Compose")
}
