package me.ag2s.app.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser


object MdConstants {

    val h1Style = SpanStyle(fontSize = 22.sp)

    val h2Style = SpanStyle(fontSize = 20.sp)


    val h3Style = SpanStyle(fontSize = 18.sp)

    val h4Style = SpanStyle(fontSize = 16.sp)

    val bodyStyle = ParagraphStyle(textAlign = TextAlign.Start, textIndent = TextIndent(firstLine = 2.sp))
    val headStyle = ParagraphStyle(textAlign = TextAlign.Center)


    val superscript = SpanStyle(
        baselineShift = BaselineShift.Superscript,
        fontSize = 16.sp, // font size of superscript
        color = Color.Red // color
    )

    // create a variable subScript
    // enter the baselineShift to
    // BaselineShift.Subscript for subscript
    val subscript = SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 16.sp, // font size of subscript color = Color.Blue // color
    )


    val strongStyle: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val ItalicStyle: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val UnderlineStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    val LineThroughStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val UAndTStyler: SpanStyle = SpanStyle(textDecoration = TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)))
    val BlankRegex: Regex = Regex("\\s+")


    val CODE_STYLE: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray)





    private val flavour: MarkdownFlavourDescriptor by lazy { GFMFlavourDescriptor() }
    val parser by lazy {
        MarkdownParser(flavour)

    }
}