package me.ag2s.app.markdown

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import java.util.regex.Matcher


private fun showTree(node: ASTNode, text: String, depth: Int = 0) {
    val type = node.type as MarkdownElementType
    Log.e(
        "MD",
        "" + depth + " ".repeat(depth) + type.name + " " + type.isToken + " " + (node.getTextInNode(
            text
        ).take(10))
    )
    node.children.forEach {
        showTree(it, text, depth + 1)
    }

}

val SPECIAL = "<think>|</think>|<Thought>|</Thought>|<Output>|</Output>".toRegex()




@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkdownText(text: String, format: Boolean=true, modifier: Modifier = Modifier) {

    Column(modifier = modifier) {
        if (format){
            if (text.contains(SPECIAL)) {
                var isToken = false
                text.split(SPECIAL).forEach {
                    if (isToken) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                    }
                    if (it.isNotBlank()) {

                        MdTextL(it)
                        isToken = true
                    } else {
                        isToken = false
                    }


                }


            } else {
                MdTextL(text)
            }
        }else{
            Text(text)
        }



    }


}


val blockMathRegex="\\\\[\\[\\]]".toRegex()
val inlineMathRegex="\\\\[()]".toRegex()



@Composable
private fun MdTextL(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val realText=remember(text){text.replace(blockMathRegex,Matcher.quoteReplacement("$$")).replace(inlineMathRegex,Matcher.quoteReplacement("$"))}
    val root = remember(realText) { MdConstants.parser.buildMarkdownTreeFromString(realText) }
    //showTree(root, realText)
    MDFile(
        root, realText, modifier,
        style = style,
    )
}


@Composable
private fun MDFile(
    node: ASTNode,
    allText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Column(modifier = modifier) {
        node.children.forEach { child ->
            when (child.type) {
                MarkdownElementTypes.MARKDOWN_FILE -> {
                    MDFile(child, allText, style = style)
                }

                MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> {

                }

                MarkdownElementTypes.PARAGRAPH -> {
                    MDParagraph(child, allText, prefix = "", style = style)
                }

                MarkdownElementTypes.BLOCK_QUOTE -> {
                    MDBlockQuote(child, allText, style = style)
                }

                MarkdownElementTypes.CODE_FENCE -> {
                    MDCodeFence(child, allText)
                }

                MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
                    MDHeader(child, allText, style = style)
                }

                MarkdownElementTypes.SETEXT_1, MarkdownElementTypes.SETEXT_2 -> {
                    MDHeader(child, allText, style = style)
                }


                GFMElementTypes.TABLE -> {
                    MDTable(child, allText, style = style)
                }

                MarkdownTokenTypes.HORIZONTAL_RULE -> {
                    HorizontalDivider()
                }

                MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
                    MDList(child, allText, style = style)
                }

                GFMElementTypes.BLOCK_MATH -> {
                    MDBlockMath(child, allText, style = style)

                }

                else -> {
                    MDNodeText(child, allText, style = style)
                }


            }


        }
    }
}

@Composable
private fun MDBlockMath(
    blockMath: ASTNode,
    allText: String,
    style: TextStyle = LocalTextStyle.current,
) {
    val math = remember(blockMath) { blockMath.getTextInNode(allText) }
    MathView(math.toString(), modifier = Modifier.fillMaxWidth())

}


@Composable
private fun MDBlockQuote(
    blockQuote: ASTNode, allText: String, style: TextStyle = LocalTextStyle.current,
) {
    val color = MaterialTheme.colorScheme.inversePrimary
    val pxValue = with(LocalDensity.current) { 12.dp.toPx() }
    val modifier = Modifier
        .drawWithCache {

            onDrawBehind {
                drawRect(
                    color = color,
                    topLeft = Offset.Zero,
                    size = size.copy(width = pxValue)
                )
            }
        }
        .padding(start = 12.dp)


    Column(modifier = modifier) {
        blockQuote.children.forEach { child ->
            when (child.type) {
                MarkdownElementTypes.PARAGRAPH -> {
                    MDNodeText(child, allText, style = style)
                }

                MarkdownElementTypes.BLOCK_QUOTE -> {
                    MDBlockQuote(child, allText, style = style)
                }

            }


        }

    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MDTable(
    node: ASTNode,
    allText: String,
    style: TextStyle = LocalTextStyle.current,
) {
    val header = node.findChildOfType(GFMElementTypes.HEADER) ?: return
    fun cellCountInRow(astNode: ASTNode): Int {
        var count = 0
        astNode.children.forEach {
            if (it.type == GFMTokenTypes.CELL) {
                count++

            }
        }
        return count;
    }

    val rows = remember(node) { node.children.filter { it.type == GFMElementTypes.ROW } }
    val count = remember(node) { derivedStateOf { cellCountInRow(header) } }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            //.wrapContentWidth(align = Alignment.Start,unbounded = true)
            .horizontalScroll(
                rememberScrollState()
            ),
        maxItemsInEachRow = count.value,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        overflow = FlowRowOverflow.Visible
    ) {




        header.children.filter { it.type == GFMTokenTypes.CELL }.forEach {
            TableCell {
                MDNodeText(it, allText, style = style)
            }


        }
        rows.forEach { row ->


            //Row {
            row.children.filter { it.type == GFMTokenTypes.CELL }.forEach {
                TableCell {
                    MDNodeText(it, allText, style = style)

                }


            }

            //}


        }

    }


}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowScope.TableCell(
    weight: Float = 1f,
    content: @Composable (BoxScope.() -> Unit)
) {
    Box(
        modifier = Modifier
            .border(1.dp, Color.DarkGray)
            .weight(weight),
        contentAlignment = Alignment.TopCenter,
        content = content
    )
}


@Composable
private fun MDCodeFence(
    node: ASTNode,
    allText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(allText)?.toString() ?: ""
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val code = remember(node) { node.codeFenceText(allText) }




    Column(modifier = modifier
        .fillMaxWidth()
        .drawWithCache {

            onDrawBehind {
                drawRect(Color.DarkGray)
                //drawRect(gradient)
            }
        }) {

        CompositionLocalProvider(LocalContentColor provides Color.LightGray) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ClickText(language, enable = false) {

                }

                ClickText("复制") {
                    clipboardManager.setText(AnnotatedString(code))
                }

            }
            Text(
                text = code,
                modifier = modifier,
                style = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = style.fontSize
                )
            )
        }


    }


}

@Composable
private fun ClickText(
    text: String,
    fontSize: TextUnit = 15.sp,
    enable: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text, modifier = Modifier
            .clickable(enabled = enable) {
                onClick()
            }
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp), fontSize = fontSize
    )
}

@Composable
private fun MDListItem(
    node: ASTNode,
    allText: String,
    style: TextStyle = LocalTextStyle.current,
) {
//    val result = AnnotatedString.Builder()
    //var pass by remember(node) { mutableIntStateOf(0) }

    val prefix = remember(node) {

        buildString {
            append(" ".repeat(node.depth()))
            if (node.children.first().type == MarkdownTokenTypes.LIST_NUMBER) {
                append(node.children.first().getTextInNode(allText))
            }
            if (node.children.first().type == MarkdownTokenTypes.LIST_BULLET) {
                append("◉ ")
            }
            if (node.children.size > 2 && node.children[1].type == GFMTokenTypes.CHECK_BOX) {
                append(node.children[1].checkedText(allText))
                //pass = 2
            }
        }

    }

    node.children.forEach { child ->
        when (child.type) {
            MarkdownElementTypes.PARAGRAPH -> {

                MDParagraph(child, allText,prefix ,style = style)

                //MDNodeText(child, allText, prefix = prefix, style = style)
            }

            MarkdownElementTypes.CODE_FENCE -> {
                MDCodeFence(child, allText, style = style)
            }

            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
                MDList(child, allText, style = style)
            }

            MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> {

            }
            MarkdownTokenTypes.LIST_NUMBER, MarkdownTokenTypes.LIST_BULLET, GFMTokenTypes.CHECK_BOX -> {

            }

            else -> {
                MDNodeText(child, allText, prefix = prefix, style = style)
            }
        }


    }

}


@Composable
private fun MDList(
    node: ASTNode, allText: String, style: TextStyle = LocalTextStyle.current,
) {
    Column {
        val items=remember(node) {  node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM } }


        items.forEach { item ->

            MDListItem(item, allText, style = style)
        }

    }
}

/**
 *
 */
@Composable
private fun MDHeader(
    node: ASTNode, allText: String, style: TextStyle = LocalTextStyle.current,
) {
    val content =remember(node) { node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT) ?: node.findChildOfType(MarkdownTokenTypes.SETEXT_CONTENT) }
    if (content!=null){
        MDNodeText(content, allText, style = style)
    }


}


@Composable
private fun MDImage(node: ASTNode, allText: String) {


    val result=remember(node) {

        val linkDestination =
            node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(allText)
                ?: ""
        val title =
            node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(allText) ?: ""
        linkDestination to title

    }

    AsyncImage(
        model = result.first.replace("\\s+".toRegex(), ""),
        contentDescription = result.second.toString(),
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        placeholder = ColorPainter(Color.Gray),

        )
}


@Composable
private fun MDParagraph(
    node: ASTNode, allText: String,prefix: String,style: TextStyle = LocalTextStyle.current,
) {


    val nodes = remember(node){node.haveChildOfTypes(listOf(MarkdownElementTypes.IMAGE, GFMElementTypes.BLOCK_MATH))}


    nodes.forEach { child ->
        when (child.type) {
            MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> {

            }

            MarkdownElementTypes.IMAGE -> {
                MDImage(child, allText)
            }

            MarkdownElementTypes.PARAGRAPH -> {
                MDParagraph(child, allText, prefix = "",style = style)
            }

            GFMElementTypes.BLOCK_MATH -> {
                MDBlockMath(child, allText, style = style)

            }
//            GFMElementTypes.INLINE_MATH -> {
//                MDBlockMath(child, allText, style = style)
//
//            }

            else -> {
                MDNodeText(child, allText, prefix=prefix,style = style)
            }

        }
    }



}


@Composable
private fun MDNodeText(
    node: ASTNode,
    allText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    prefix: String = ""
) {

    val color=MaterialTheme.colorScheme.onBackground

    val result = remember(node) {
        val builder = AnnotatedString.Builder(prefix)
        val composeVisitor = ComposeVisitor(builder, allText, textStyle = style, color = color)
        node.accept(composeVisitor)
        composeVisitor
    }


    Text(
        result.value,
        modifier = modifier.padding(bottom = 4.dp),
        style = style,
        inlineContent = result.inlineContent,
        maxLines = maxLines,
        minLines = minLines
    )


}