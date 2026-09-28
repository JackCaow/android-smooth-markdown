package com.jackcaow.smoothmarkdown.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QwenChatClientTest {
    @Test fun sseFramingWaitsForEventBoundaryAndIgnoresMetadata() {
        val framer = QwenSseFramer()
        assertNull(framer.acceptLine(": keep-alive"))
        assertNull(framer.acceptLine("event: message"))
        assertNull(framer.acceptLine("data: {\"choices\":["))
        assertNull(framer.acceptLine("data: {\"delta\":{\"content\":\"Hi\"}}] }"))
        assertEquals("{\"choices\":[\n{\"delta\":{\"content\":\"Hi\"}}] }", framer.acceptLine(""))
        assertNull(framer.acceptLine(""))
        assertNull(framer.acceptLine("data: [DONE]"))
        assertEquals("[DONE]", framer.flush())
        assertNull(framer.acceptLine("data:{\"content\":\"ok\"}"))
        assertEquals("{\"content\":\"ok\"}", framer.acceptLine(""))
    }

    @Test fun reasoningAndAnswerChunksMatchFlutterThinkingTags() {
        val formatter = QwenDeltaFormatter()
        assertEquals("", formatter.accept(null, null))
        assertEquals("<thinking>\n思考", formatter.accept("思考", null))
        assertEquals("继续", formatter.accept("继续", ""))
        assertEquals("\n</thinking>\n\n答案", formatter.accept(null, "答案"))
        assertEquals("", formatter.finish())
    }

    @Test fun openThinkingBlockClosesAtEndOfStream() {
        val formatter = QwenDeltaFormatter()
        assertEquals("<thinking>\n分析", formatter.accept("分析", null))
        assertEquals("\n</thinking>\n\n", formatter.finish())
        assertEquals("", formatter.finish())
    }

    @Test fun ordinaryAnswerPassesThroughWithoutThinkingTags() {
        val formatter = QwenDeltaFormatter()
        assertEquals("你好", formatter.accept(null, "你好"))
        assertEquals("，世界", formatter.accept("", "，世界"))
        assertEquals("", formatter.finish())
    }
}
