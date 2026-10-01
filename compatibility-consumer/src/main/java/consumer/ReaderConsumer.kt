package consumer
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.*
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorController
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor
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

@Composable
fun EditorAndSelectionConsumer(source: String) {
    val selection = androidx.compose.runtime.remember { SmoothSelectionController() }
    val editor = androidx.compose.runtime.remember { MarkdownEditorController(source) }
    SmoothMarkdown(markdown = source, selectable = true, selectionController = selection,
        scrollable = false, styleSheet = MarkdownStyleSheet.default())
    SmoothMarkdownEditor(controller = editor)
}

@Composable
fun CustomTextConsumer(source: String) {
    val registry = androidx.compose.runtime.remember {
        MarkdownBuilderRegistry().register<com.jackcaow.smoothmarkdown.ast.Paragraph>(
            object : MarkdownNodeBuilder {
                override fun canBuild(node: com.jackcaow.smoothmarkdown.ast.Node) = true
                override fun documentText(node: com.jackcaow.smoothmarkdown.ast.Node) = "Consumer textConsumer text"
                override fun selectionMode(node: com.jackcaow.smoothmarkdown.ast.Node) = MarkdownBlockSelectionMode.NATIVE_TEXT
                @Composable override fun Render(node: com.jackcaow.smoothmarkdown.ast.Node, context: MarkdownBuilderContext) {
                    SmoothSelectableText("Consumer text")
                    SmoothSelectableText(androidx.compose.ui.text.AnnotatedString("Consumer text"))
                }
            })
    }
    SmoothMarkdown(source, selectable = true, builderRegistry = registry)
}
