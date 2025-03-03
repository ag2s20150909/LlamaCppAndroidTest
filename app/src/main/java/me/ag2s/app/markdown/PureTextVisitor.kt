package me.ag2s.app.markdown

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor

/**
 * [PureTextVisitor] is a [RecursiveVisitor] that extracts all plain text content from an Abstract Syntax Tree (AST) of a Markdown document.
 *
 * It traverses the AST, identifies nodes representing plain text (MarkdownTokenTypes.TEXT), and appends their content to a provided StringBuilder.
 * Other node types are recursively visited to ensure that text nested within them is also captured.
 *
 * This class is designed to be used with a Markdown AST, typically obtained through parsing a Markdown document using a [MarkdownParser].
 * It effectively strips away Markdown formatting, leaving only the raw text.
 *
 * @property builder The [StringBuilder] to which the extracted plain text will be appended.
 * @property allText The complete original text of the Markdown document. This is needed to retrieve the text content of a node using [ASTNode.getTextInNode].
 * @constructor Creates a [PureTextVisitor] instance with the specified [StringBuilder] and the original document text.
 */
private class PureTextVisitor(private val builder: StringBuilder, private val allText: String) :RecursiveVisitor(){
    override fun visitNode(node: ASTNode) {
        when(node.type){
            MarkdownTokenTypes.TEXT -> {
                builder.append(node.getTextInNode(allText))
            }
            else -> {
                super.visitNode(node)
            }
        }
    }
}

/**
 * Visitor to extract plain text from a Markdown document.
 *
 * This class traverses the Markdown syntax tree and appends the plain text content
 * of each Text node to a StringBuilder.
 *
 * @property text The original markdown string. The StringBuilder to which the extracted text will be appended.
 * @return The plain text extracted from the markdown document.
 */
fun parseMdText(text:String):String{
    val sb= StringBuilder()
    val node= MdConstants.parser.buildMarkdownTreeFromString(text)
    node.accept(PureTextVisitor(sb,text))
    return sb.toString()
}
