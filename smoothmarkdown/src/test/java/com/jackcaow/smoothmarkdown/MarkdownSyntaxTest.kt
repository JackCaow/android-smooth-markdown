package com.jackcaow.smoothmarkdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BulletList
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSyntaxTest {
    @Test fun parsesGfmTablesAndTasks() {
        val document = parseMarkdown("""
            | Name | Value |
            | ---- | ----- |
            | one  | two   |

            - [x] done
            - [ ] todo
        """.trimIndent())

        assertTrue(document.firstChild is TableBlock)
        val list = document.firstChild.next as BulletList
        assertTrue((list.firstChild as ListItem).firstChild is TaskListItemMarker)
        val markers = descendants(document).filterIsInstance<TaskListItemMarker>().toList()
        assertEquals(2, markers.size)
        assertTrue(markers[0].isChecked)
        assertFalse(markers[1].isChecked)
    }

    @Test fun rejectsUnsafeLinksAndImages() {
        assertFalse(isSafeLink("javascript:alert(1)"))
        assertFalse(isSafeLink("data:text/html,test"))
        assertTrue(isSafeLink("https://example.com"))
        assertTrue(isSafeLink("/relative/path"))
        assertFalse(isSafeImage("file:///etc/passwd"))
        assertTrue(isSafeImage("https://example.com/a.png"))
    }

    private fun descendants(node: Node): Sequence<Node> = sequence {
        var child = node.firstChild
        while (child != null) {
            yield(child)
            yieldAll(descendants(child))
            child = child.next
        }
    }
}
