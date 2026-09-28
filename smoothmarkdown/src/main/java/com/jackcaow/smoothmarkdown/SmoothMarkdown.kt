package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser

private val parser = Parser.builder()
    .extensions(listOf(StrikethroughExtension.create()))
    .build()

/** Initial native renderer. Unsupported AST blocks are currently shown as plain text. */
@Composable
fun SmoothMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
) {
    val document = remember(markdown) { parser.parse(markdown) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        for (node in document.children()) {
            MarkdownBlock(node, onLinkClick)
        }
    }
}

@Composable
private fun MarkdownBlock(node: Node, onLinkClick: (String) -> Unit) {
    val body = when (node) {
        is Heading -> inlineText(node)
        is Paragraph -> inlineText(node)
        is FencedCodeBlock -> AnnotatedString(node.literal)
        is IndentedCodeBlock -> AnnotatedString(node.literal)
        is BlockQuote -> AnnotatedString("❝  " + node.plainText())
        is BulletList, is OrderedList -> AnnotatedString(node.plainText())
        is ThematicBreak -> AnnotatedString("──────────")
        else -> AnnotatedString(node.plainText())
    }
    val style = when (node) {
        is Heading -> MaterialTheme.typography.headlineMedium.copy(
            fontSize = (32 - (node.level - 1) * 3).sp,
            fontWeight = FontWeight.Bold,
        )
        is FencedCodeBlock, is IndentedCodeBlock -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else -> MaterialTheme.typography.bodyLarge
    }
    val blockModifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    val decorated = if (node is FencedCodeBlock || node is IndentedCodeBlock) {
        blockModifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
    } else blockModifier
    ClickableText(
        text = body,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = decorated,
        onClick = { position ->
            body.getStringAnnotations("url", position, position).firstOrNull()?.let {
                onLinkClick(it.item)
            }
        },
    )
}

private fun inlineText(node: Node): AnnotatedString = buildAnnotatedString {
    fun appendNode(current: Node) {
        val start = length
        if (current is org.commonmark.node.Text) {
            append(current.literal)
        } else if (current is Code) {
            append(current.literal)
        } else if (current is SoftLineBreak || current is HardLineBreak) {
            append("\n")
        } else {
            for (child in current.children()) appendNode(child)
        }
        val end = length
        when (current) {
            is StrongEmphasis -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            is Emphasis -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            is Code -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
            is Strikethrough -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            is Link -> {
                addStyle(SpanStyle(color = Color(0xFF0969DA), textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("url", current.destination, start, end)
            }
        }
    }
    for (child in node.children()) appendNode(child)
}

private fun Node.children(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        yield(child)
        child = child.next
    }
}

private fun Node.plainText(): String = buildString {
    fun visit(node: Node) {
        when (node) {
            is org.commonmark.node.Text -> append(node.literal)
            is Code -> append(node.literal)
            is FencedCodeBlock -> append(node.literal)
            is IndentedCodeBlock -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            is ListItem -> { append("• "); node.children().forEach(::visit); append('\n') }
            else -> node.children().forEach(::visit)
        }
    }
    visit(this@plainText)
}
