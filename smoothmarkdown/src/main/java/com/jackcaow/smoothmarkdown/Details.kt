package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.CustomBlock
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.Paragraph
import com.jackcaow.smoothmarkdown.ast.SourceSpan

internal class DetailsNode(val isOpen: Boolean) : CustomBlock() {
    var summarySource: String = ""
        internal set
    var bodySource: String = ""
        internal set
    var summary: List<Node> = emptyList()
        internal set
    var body: List<Node> = emptyList()
        internal set
}
