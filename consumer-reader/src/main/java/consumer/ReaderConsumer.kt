package consumer
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.*
import kotlinx.coroutines.flow.Flow
@Composable
fun LegacyConsumer(source: String) { SmoothMarkdown(markdown = source, selectable = true) }
@Composable
fun StructuredConsumer(source: String, chunks: Flow<String>) {
    val options = MarkdownRenderOptions(useEnhancedComponents = true)
    val events = MarkdownEvents(onImageClick = { event -> println(event.source) })
    val resources = MarkdownResourceOptions(headers = mapOf("X-Consumer" to "smoke"))
    val strings = MarkdownStrings(copy = "复制", copied = "已复制")
    SmoothMarkdown(markdown = source, renderOptions = options, events = events, resourceOptions = resources, strings = strings)
    StreamMarkdown(chunks = chunks, renderOptions = options, events = events, resourceOptions = resources, strings = strings)
}
