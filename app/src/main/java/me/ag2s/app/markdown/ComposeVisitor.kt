package me.ag2s.app.markdown

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import me.ag2s.app.markdown.MarkStyles.BlankRegex
import me.ag2s.app.markdown.MarkStyles.ItalicStyle
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownElementTypes.ATX_1
import org.intellij.markdown.MarkdownElementTypes.ATX_2
import org.intellij.markdown.MarkdownElementTypes.ATX_3
import org.intellij.markdown.MarkdownElementTypes.ATX_4
import org.intellij.markdown.MarkdownElementTypes.ATX_5
import org.intellij.markdown.MarkdownElementTypes.ATX_6
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes


class ComposeVisitor(private val builder: AnnotatedString.Builder, private val allText: String) :
    RecursiveVisitor() {
    override fun visitNode(node: ASTNode) {

        val type: MarkdownElementType = node.type as MarkdownElementType
        val parentType= node.parent?.type




        when (type) {


            ATX_1 -> {
                builder.withStyle(MarkStyles.headStyle) {
                    super.visitNode(node)
                }
            }

            ATX_2, ATX_3, ATX_4, ATX_5, ATX_6 -> {
                builder.withStyle(MarkStyles.bodyStyle) {
                    super.visitNode(node)
                }
            }

            MarkdownTokenTypes.ATX_CONTENT -> {
                val style = node.parent!!.atxStyle()
                builder.withStyle(style) {
                    super.visitNode(node)
                }


            }

            MarkdownElementTypes.EMPH -> {
                builder.withStyle(MarkStyles.ItalicStyle) {
                    super.visitNode(node)
                }
            }

            MarkdownElementTypes.STRONG -> {
                builder.withStyle(MarkStyles.strongStyle) {
                    super.visitNode(node)
                }
            }

            MarkdownElementTypes.CODE_BLOCK -> {
                builder.withStyle(MarkStyles.CODE_STYLE) {
                    super.visitNode(node)
                }
            }


            MarkdownElementTypes.CODE_SPAN -> {
                builder.withStyle(MarkStyles.CODE_STYLE) {
                    super.visitNode(node)
                }
            }

            GFMElementTypes.STRIKETHROUGH -> {
                builder.withStyle(MarkStyles.LineThroughStyle) {
                    super.visitNode(node)
                }
            }

            MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                builder.withStyle(MarkStyles.superscript) {
                    super.visitNode(node)
                }
            }


            MarkdownElementTypes.LIST_ITEM -> {
                processListItem(node)
            }

            MarkdownElementTypes.ORDERED_LIST,MarkdownElementTypes.UNORDERED_LIST -> {
                super.visitNode(node)
            }


            //link

            MarkdownElementTypes.LINK_DEFINITION -> {

                var label = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)
                    ?.getTextInNode(allText)?.removeSurrounding("[", "]")
                val link = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.getTextInNode(allText)

                if (label.isNullOrBlank()) {
                    label = link
                }

                builder.withLink(
                    LinkAnnotation.Url(
                        link.toString(),
                        TextLinkStyles(style = SpanStyle(color = Color.Blue))
                    )
                ) {
                    builder.append(label)
                }

            }

            MarkdownElementTypes.INLINE_LINK -> {
                val link = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.findChildOfType(MarkdownTokenTypes.TEXT)?.getTextInNode(allText)
                    ?: node.getTextInNode(allText)
                val title = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                    ?.findChildOfType(MarkdownTokenTypes.TEXT)?.getTextInNode(allText) ?: link
                builder.withLink(
                    LinkAnnotation.Url(
                        link.toString(),
                        TextLinkStyles(style = SpanStyle(color = Color.Blue))
                    )
                ) {
                    builder.append(title)
                }

            }

            MarkdownElementTypes.AUTOLINK -> {
                val link = node.getTextInNode(allText)
                builder.withLink(
                    LinkAnnotation.Url(
                        link.toString(),
                        TextLinkStyles(style = SpanStyle(color = Color.Blue))
                    )
                ) {
                    builder.append(link)
                }

            }

            MarkdownTokenTypes.EMAIL_AUTOLINK -> {
                val link = node.getTextInNode(allText)
                val realLink = if (link.startsWith("mailto:")) {
                    link.toString()
                } else {
                    "mailto:$link"
                }
                builder.withLink(
                    LinkAnnotation.Url(
                        realLink,
                        TextLinkStyles(style = SpanStyle(color = Color.Blue))
                    )
                ) {
                    builder.append(link)
                }

            }

            MarkdownElementTypes.LINK_LABEL -> {
                val label =
                    node.findChildOfType(MarkdownTokenTypes.TEXT)?.getTextInNode(allText)
                        ?: node.getTextInNode(allText).removeSurrounding("[", "]")

                if (node.parent == MarkdownElementTypes.SHORT_REFERENCE_LINK) {
                    builder.append(label.removePrefix("^"))
                } else {
                    builder.append(label)
                }
            }

            MarkdownElementTypes.PARAGRAPH -> {

                if (node.parent?.type == MarkdownElementTypes.LIST_ITEM) {
                    super.visitNode(node)

                } else {
                    builder.withStyle(MarkStyles.bodyStyle) {
                        super.visitNode(node)
                    }
                }


            }

            MarkdownTokenTypes.TEXT -> {
                builder.append(node.getTextInNode(allText))
            }

            MarkdownTokenTypes.EOL -> {
                if (node.parent == MarkdownElementTypes.PARAGRAPH) {
                    builder.append('\n')
                }
                //
            }

            MarkdownTokenTypes.HORIZONTAL_RULE -> {
                builder.append('\n')
            }

            GFMTokenTypes.GFM_AUTOLINK -> {
                if (node.parent == MarkdownElementTypes.LINK_TEXT) {
                    builder.append(node.getTextInNode(allText))
                } else {
                    val targetNode = node.children.firstOrNull {
                        it.type.name == MarkdownElementTypes.AUTOLINK.name
                    } ?: node
                    val destination = targetNode.getTextInNode(allText).toString()



                    builder.withLink(
                        LinkAnnotation.Url(
                            destination,
                            TextLinkStyles(style = SpanStyle(color = Color.Blue))
                        )
                    ) {
                        builder.append(destination)
                    }
                }
            }

            GFMTokenTypes.CHECK_BOX -> {
                val checked = node.getTextInNode(allText).contains("x")
                if (checked) {
                    builder.append("❌")
                } else {
                    builder.append("✅")
                }

            }

            MarkdownTokenTypes.LIST_BULLET -> {
                val bullet = node.getTextInNode(allText)
                Log.e("MarkDown", "bullet:$bullet")
                if (bullet.isNotBlank()) {
                    builder.append("◉ ")
                }

            }

            MarkdownTokenTypes.LIST_NUMBER -> {
                val bullet = node.getTextInNode(allText)
                Log.e("MarkDown", "bullet:$bullet")
                if (bullet.isNotEmpty()) {
                    builder.append(bullet)
                }
            }

            MarkdownTokenTypes.WHITE_SPACE -> {
                builder.append(" ")
            }

            MarkdownTokenTypes.CODE_LINE -> {
                builder.append(node.getTextInNode(allText))
                builder.append('\n')

            }

            MarkdownElementTypes.HTML_BLOCK -> {
                super.visitNode(node)
            }

            MarkdownTokenTypes.HTML_TAG -> {
                processHtmlTag(node)
            }

            MarkdownTokenTypes.HTML_BLOCK_CONTENT -> {
                builder.append(node.getTextInNode(allText))
            }

            MarkdownTokenTypes.CODE_FENCE_START->{
                builder.pushStyle(MarkStyles.CODE_STYLE)
            }
            MarkdownTokenTypes.FENCE_LANG->{

            }
            MarkdownTokenTypes.CODE_FENCE_CONTENT->{
                builder.append(node.getTextInNode(allText))
                builder.append('\n')
            }
            MarkdownTokenTypes.CODE_FENCE_END->{

                builder.pop()
            }
            MarkdownTokenTypes.SINGLE_QUOTE -> {builder.append('\'')}
            MarkdownTokenTypes.DOUBLE_QUOTE -> builder.append('\"')
            MarkdownTokenTypes.LPAREN -> builder.append('(')
            MarkdownTokenTypes.RPAREN -> builder.append(')')
            MarkdownTokenTypes.LBRACKET -> builder.append('[')
            MarkdownTokenTypes.RBRACKET -> builder.append(']')
            MarkdownTokenTypes.LT -> builder.append('<')
            MarkdownTokenTypes.GT -> builder.append('>')
            MarkdownTokenTypes.COLON -> builder.append(':')
            MarkdownTokenTypes.EXCLAMATION_MARK -> builder.append('!')
            MarkdownTokenTypes.BACKTICK -> builder.append('`')
            MarkdownTokenTypes.HARD_LINE_BREAK -> builder.append("\n\n")
            MarkdownTokenTypes.EMPH -> if (parentType != MarkdownElementTypes.EMPH && parentType != MarkdownElementTypes.STRONG) builder.append('*')
            MarkdownTokenTypes.EOL -> builder.append('\n')
            else -> {
                Log.e("MarkDown", "type:${type.name}" + " isToken:" + type.isToken)
                super.visitNode(node)
            }
        }
    }


    private fun processListItem(node: ASTNode) {

        builder.append('\n')
        builder.append(" ".repeat(node.depth()))
        when (node.parent?.type) {

            MarkdownElementTypes.UNORDERED_LIST -> {
                super.visitNode(node)
            }

            MarkdownElementTypes.ORDERED_LIST -> {
                super.visitNode(node)
            }

            else -> {
                //super.visitNode(node)
            }
        }
    }


    private fun processHtmlTag(astNode: ASTNode) {
        val tgs = astNode.getTextInNode(allText).trim().toString().lowercase()

        when (tgs.replace(BlankRegex, "")) {
            "<strong>" -> {
                builder.pushStyle(MarkStyles.strongStyle)
            }

            "</strong>" -> {
                builder.pop()
            }

            "<em>" -> {
                builder.pushStyle(ItalicStyle)
            }

            "</em>" -> {
                builder.pop()
            }

            "<del>" -> {
                builder.pushStyle(MarkStyles.LineThroughStyle)
            }

            "</del>" -> {
                builder.pop()
            }

            "<br>" -> {
                builder.append('\n')
            }

            "<thought>" -> {
                builder.append('\n')
                builder.append("思考过程")
                builder.append('\n')
            }

            "</thought>" -> {
                builder.append('\n')
            }

            "<output>" -> {
                builder.append('\n')
                builder.append("输出结果")
                builder.append('\n')
            }

            "</output>" -> {
                builder.append('\n')
            }

        }
    }


}






