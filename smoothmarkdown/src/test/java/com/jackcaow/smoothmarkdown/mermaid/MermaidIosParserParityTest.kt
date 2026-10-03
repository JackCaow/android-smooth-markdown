package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.*
import org.junit.Test

class MermaidIosParserParityTest {
    @Test fun unicodeSequenceWhitespaceCombiningAliasesAndSelfMessages() {
        val source = "sequenceDiagram\nactor 用户 as 客户端 😀\nparticipant 服务器 as Remote Server\n用户 ->> 服务器 : 请求 😀\n服务器 -->> 用户: 返回\n用户 -x 用户: 取消"
        val diagram = MermaidParser.parse(source)!!
        assertEquals(listOf("用户", "服务器"), diagram.nodes.map { it.id })
        assertEquals("客户端 😀", diagram.node("用户")!!.label)
        assertEquals(listOf("请求 😀", "返回", "取消"), diagram.edges.map { it.label })
        assertEquals(MermaidLine.Dotted, diagram.edges[1].line)
        assertEquals(diagram.edges.last().from, diagram.edges.last().to)
        val combining = "e\u0301"
        val late = MermaidSequenceParser().parse(listOf("sequenceDiagram", "$combining ->> B: hi", "actor B as 后声明"))!!
        assertEquals(combining, late.nodes.first().id)
        assertEquals(MermaidParticipantType.Actor, late.node("B")!!.participantType)
        for (body in listOf("", "invalid syntax", "A->>B: valid\nbogus", "A ->> : missing")) {
            assertNull(body, MermaidParser.parse("sequenceDiagram\n$body"))
        }
        assertNotNull(MermaidParser.parse("sequenceDiagram\nparticipant A as Alone"))
        assertNull(MermaidParser.parse("sequenceDiagram\n" + List(501) { "A->>B: hi" }.joinToString("\n")))
    }

    @Test fun pieInlineTitleWorksForDispatcherAndStandaloneParser() {
        for (header in listOf("pie title 浏览器份额", "pie showData title 浏览器份额")) {
            val lines = listOf(header, "\"Chrome\": 65", "\"Safari\": 20", "\"Edge\": 15")
            val direct = MermaidPieParser().parse(lines)!!
            assertEquals(direct, MermaidParser.parse(lines.joinToString("\n")))
            assertEquals("浏览器份额", direct.pie!!.title)
            assertEquals(3, direct.pie!!.slices.size)
            assertEquals(header.contains("showData"), direct.pie!!.showValuesInLegend)
        }
        assertNull(MermaidParser.parse("pie title Empty"))
    }

    @Test fun gitBranchesMergeTagsDirectionsAndCrLf() {
        val source = "gitGraph TB:\ninit\ncommit id:\"base\" tag:\"v1 😀\"\nbranch \"feature branch\"\ncommit id:\"changed\" type:REVERSE\nswitch main\nmerge \"feature branch\" id:\"merged\" type:HIGHLIGHT tag:\"v2\""
        val diagram = MermaidParser.parse(source)!!
        assertEquals(MermaidKind.GitGraph, diagram.kind)
        assertEquals(MermaidDirection.TB, diagram.direction)
        assertEquals(listOf("base", "changed", "merged"), diagram.nodes.map { it.id })
        assertEquals(listOf("main", "feature branch", "main"), diagram.nodes.map { it.compartments[0][0] })
        assertEquals("v1 😀", diagram.nodes[0].compartments[1][0])
        assertEquals(MermaidShape.Rectangle, diagram.nodes.last().shape)
        assertEquals(listOf("base", "changed"), diagram.edges.filter { it.to == "merged" }.map { it.from })
        assertTrue(diagram.edges.all { it.arrow == MermaidArrow.None })
        assertEquals(diagram, MermaidParser.parse(source.replace("\n", "\r\n")))
        for (body in listOf("", "init", "checkout missing", "branch main", "merge main", "commit id:\"same\"\ncommit id:\"same\"", "commit\nmerge missing", "commit\nbranch feature\ncheckout main\nmerge feature", "commit\ninit", "commit id:\"unclosed", "commit type:UNKNOWN", "commit\ncherry-pick id:\"unknown\"")) {
            assertNull(body, MermaidParser.parse("gitGraph\n$body"))
        }
        assertNull(MermaidParser.parse("gitGraph\n" + List(501) { "commit" }.joinToString("\n")))
    }

    @Test fun mindmapIndentationShapesTabsAndCrLf() {
        val source = "mindmap\n  root((项目))\n    前端\n      页面\n      组件\n    后端\n      API\n      数据库"
        val diagram = MermaidParser.parse(source)!!
        assertEquals(MermaidKind.Mindmap, diagram.kind)
        assertEquals(listOf("项目", "前端", "页面", "组件", "后端", "API", "数据库"), diagram.nodes.map { it.label })
        assertEquals(6, diagram.edges.size)
        assertEquals(listOf("root", "mindmap:1", "mindmap:1", "root", "mindmap:4", "mindmap:4"), diagram.edges.map { it.from })
        assertEquals(diagram, MermaidParser.parse(source.replace("\n", "\r\n")))
        val tabs = MermaidParser.parse("mindmap\n\troot((Root))\n\t\tchild[Child]\n\t\t\tleaf(Leaf)\n\t\tother{{Other}}")!!
        assertEquals(listOf(MermaidShape.Circle, MermaidShape.Rectangle, MermaidShape.Rounded, MermaidShape.Hexagon), tabs.nodes.map { it.shape })
        assertEquals(listOf("root", "child", "root"), tabs.edges.map { it.from })
        for (body in listOf("", "root((Root))\nother[Second root]", "root((Root))\n  root[Cycle]", "root((Root))\n  same[Child]\n  same[Second parent]", "root((unclosed)", "root((Root))\n  ::icon(fa fa-book)", "root((Root))\n  A --> B")) {
            assertNull(body, MermaidParser.parse("mindmap\n$body"))
        }
        assertNull(MermaidParser.parse("mindmap\nroot((Root))\n  " + "x".repeat(50_000)))
    }

    @Test fun frontmatterNormalizationPreservesIndentedTreeAndAllDiagramDispatches() {
        val sources = listOf("graph TD\nA --> B", "sequenceDiagram\nA ->> B: hello", "pie title Title\n\"A\": 5", "timeline\n2026 : first", "radar-beta\naxis A, B, C\ncurve c{1,2,3}", "classDiagram\nA <|-- B", "stateDiagram-v2\nA --> B", "erDiagram\nA ||--o{ B : owns", "xychart-beta\nx-axis [A, B]\nbar [1,2]", "gantt\nTask :a, 2024-01-01, 3d", "kanban\ncol[Column]\n  task[Task]", "gitGraph\ncommit", "mindmap\nroot((Root))\n  child")
        for (source in sources) {
            val plain = MermaidParser.parse(source)
            assertNotNull(source, plain)
            assertEquals(source, plain, MermaidParser.parse("---\ntitle: Frontmatter\n---\n$source"))
            assertEquals(source, plain, MermaidParser.parse(source.replace("\n", "\r\n")))
        }
        assertNull(MermaidParser.parse("---\ntitle: Unclosed\ngraph TD\nA --> B"))
    }
}
