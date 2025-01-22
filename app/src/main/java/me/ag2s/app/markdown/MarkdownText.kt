package me.ag2s.app.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import me.ag2s.app.markdown.MarkStyles.parser
import org.intellij.markdown.ast.accept


val SPECIAL="<think>|</think>|<Thought>|</Thought>|<Output>|</Output>".toRegex()


@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {

    Column{
        if (text.contains(SPECIAL)){
            text.split(SPECIAL).forEach {
                //Log.e("MD",">>"+it)
                if (it.isNotBlank()){
                    MDText(it)
                    HorizontalDivider()
                }else{
                    HorizontalDivider()
                }


            }



        }else{
           MDText(text)
        }



    }



}


@Composable
private fun MDText(text: String, modifier: Modifier = Modifier){
    val root = remember(text) { parser.buildMarkdownTreeFromString(text) }
    val result = AnnotatedString.Builder()
    root.accept(ComposeVisitor(result, text))
    Text(result.toAnnotatedString(), modifier)
}

@Composable
private fun MarkDownCodeText(code: String, language: String) {
    Text(code, modifier = Modifier
        .fillMaxWidth()
        .background(Color.Gray))
}

