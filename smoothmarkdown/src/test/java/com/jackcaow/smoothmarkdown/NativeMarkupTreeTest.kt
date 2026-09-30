package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.*
import org.junit.Assert.*
import org.junit.Test

class NativeMarkupTreeTest {
    @Test fun movingBetweenParentsPreservesSiblingEnds() {
        val first = Paragraph()
        val second = Paragraph()
        val a = Text("a")
        val b = Text("b")
        val c = Text("c")
        first.appendChild(a); first.appendChild(b); first.appendChild(c)
        second.appendChild(b)
        assertSame(c, a.next); assertSame(a, c.previous)
        assertSame(first, a.parent); assertSame(second, b.parent)
        assertSame(b, second.firstChild); assertSame(b, second.lastChild)
        assertNull(b.previous); assertNull(b.next)
        c.insertBefore(a)
        assertSame(a, first.firstChild); assertSame(c, first.lastChild)
        a.insertAfter(c)
        assertSame(c, a.next); assertSame(a, c.previous)
        c.unlink(); a.unlink()
        assertNull(first.firstChild); assertNull(first.lastChild)
    }

    @Test fun cycleRejectionDoesNotDetachNodes() {
        val root = Document(); val paragraph = Paragraph(); val text = Text("text")
        root.appendChild(paragraph); paragraph.appendChild(text)
        assertThrows(IllegalArgumentException::class.java) { text.appendChild(root) }
        assertSame(paragraph, root.firstChild)
        assertSame(root, paragraph.parent)
        assertSame(text, paragraph.firstChild)
    }

    @Test fun descendantsStopAtTheirOwnRootInsteadOfLeakingSiblings() {
        val root = Document(); val first = Paragraph(); val next = Paragraph()
        val emphasis = Emphasis(); val text = Text("a")
        emphasis.appendChild(text); first.appendChild(emphasis)
        root.appendChild(first); root.appendChild(next)
        assertEquals(listOf(emphasis, text), first.descendants().toList())
        assertEquals(listOf(first, emphasis, text, next), root.descendants().toList())
    }

    @Test fun publicAstExportsEscapedContentAndUnicodeDestinations() {
        val root = Document(); val paragraph = Paragraph(); root.appendChild(paragraph)
        val link = Link("https://example.com/世界?a=b&c=d", "a\"b")
        link.appendChild(Text("<read>")); paragraph.appendChild(link)
        assertEquals("<p><a href=\"https://example.com/%E4%B8%96%E7%95%8C?a=b&amp;c=d\" title=\"a&quot;b\">&lt;read&gt;</a></p>\n", NativeMarkdownHTMLSerializer().render(root))
    }

    @Test fun taskMarkersStayInsideParagraphForLooseListsAndInlineForTightLists() {
        val list = BulletList(); val item = ListItem(); val marker = TaskListItemMarker(true)
        val paragraph = Paragraph(); paragraph.appendChild(Text("done"))
        item.appendChild(marker); item.appendChild(paragraph); list.appendChild(item)
        assertEquals("<ul>\n<li><input checked=\"\" disabled=\"\" type=\"checkbox\"> done</li>\n</ul>\n", NativeMarkdownHTMLSerializer().render(list))
        list.isTight = false
        assertEquals("<ul>\n<li>\n<p><input checked=\"\" disabled=\"\" type=\"checkbox\"> done</p>\n</li>\n</ul>\n", NativeMarkdownHTMLSerializer().render(list))
    }

    @Test fun sourceSpansRemainUtf16CoordinatesAcrossTreeMutation() {
        val source = SourceSpan(0, 2, 2, 2)
        val text = Text("😀"); text.addSourceSpan(source)
        val before = Paragraph(); val after = Paragraph()
        before.appendChild(text); after.appendChild(text)
        assertEquals(listOf(source), text.sourceSpans)
        assertEquals(2, text.sourceSpans.single().length)
    }
}
