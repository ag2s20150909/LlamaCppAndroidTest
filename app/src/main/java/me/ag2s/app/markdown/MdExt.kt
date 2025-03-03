package me.ag2s.app.markdown

import androidx.compose.ui.text.SpanStyle
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownElementTypes.ATX_1
import org.intellij.markdown.MarkdownElementTypes.ATX_2
import org.intellij.markdown.MarkdownElementTypes.ATX_3
import org.intellij.markdown.MarkdownElementTypes.ATX_4
import org.intellij.markdown.MarkdownElementTypes.SETEXT_1
import org.intellij.markdown.MarkdownElementTypes.SETEXT_2
import org.intellij.markdown.MarkdownTokenTypes.Companion.CODE_FENCE_CONTENT
import org.intellij.markdown.MarkdownTokenTypes.Companion.EOL
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.CompositeASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.DOLLAR

internal fun ASTNode.atxStyle()= when (this.type) {
    ATX_1,SETEXT_1 -> MdConstants.h1Style
    ATX_2,SETEXT_2 -> MdConstants.h2Style
    ATX_3 -> MdConstants.h3Style
    ATX_4 -> MdConstants.h4Style
    else -> SpanStyle()
}
internal fun ASTNode.checkedText(allText:String): String {
    val checked = this.getTextInNode(allText).contains("x")
    return if (checked) {
        "❌"
    } else {
        "✅"
    }
}

fun ASTNode.haveChildOfTypes(types: List<IElementType>):List<ASTNode> {
    val list=mutableListOf<ASTNode>()
    val temp=mutableListOf<ASTNode>()

    this.children.forEach {
        if (types.contains(it.type)) {
            if (!temp.isEmpty()){
                val node= CompositeASTNode(MarkdownElementTypes.MARKDOWN_FILE,temp.toList())

                list.add(node)
                temp.clear()
            }
            list.add(it)
        }else{
            temp.add(it)
        }
    }
    if (!temp.isEmpty()){
        val node= CompositeASTNode(MarkdownElementTypes.MARKDOWN_FILE,temp.toList())

        list.add(node)
        temp.clear()
    }

    return list
}

internal fun ASTNode.blockMathText(allText:String)= buildString {
    this@blockMathText.children.forEach { astNode ->

        when(astNode.type){
            DOLLAR->{
                //append(astNode.getTextInNode(allText))
            }
            EOL->{
                append('\n')
            }else -> {
                append(astNode.getTextInNode(allText))
            }

        }

    }
}


internal fun ASTNode.codeFenceText(allText:String)= buildString {
    this@codeFenceText.children.forEach { astNode ->

        when(astNode.type){
            CODE_FENCE_CONTENT->{
                append(astNode.getTextInNode(allText))
            }
            EOL->{
                append('\n')
            }

        }

    }
}




internal  fun ASTNode.findChildOfTypeRecursive(type: IElementType): ASTNode? {
    children.forEach { it ->
        if (it.type == type) {
            return it
        } else {
            val found = it.findChildOfTypeRecursive(type)
            if (found != null) {
                return found
            }
        }
    }
    return null
}