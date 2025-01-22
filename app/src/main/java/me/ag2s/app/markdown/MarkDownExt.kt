package me.ag2s.app.markdown

import androidx.compose.ui.text.SpanStyle
import org.intellij.markdown.MarkdownElementTypes.ATX_1
import org.intellij.markdown.MarkdownElementTypes.ATX_2
import org.intellij.markdown.MarkdownElementTypes.ATX_3
import org.intellij.markdown.MarkdownElementTypes.ATX_4
import org.intellij.markdown.ast.ASTNode

fun ASTNode.depth(): Int {
    var depth = 0;
    var p = this.parent
    while (p != null) {
        depth++
        p = p.parent

    }
    return depth
}

fun ASTNode.atxStyle()=  when (this.type) {
    ATX_1 -> MarkStyles.h1Style
    ATX_2 -> MarkStyles.h2Style
    ATX_3 -> MarkStyles.h3Style
    ATX_4 -> MarkStyles.h4Style
    //ATX_5-> MarkStyles.h5Style
    else -> SpanStyle()
}