package me.ag2s.app.markdown

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import ru.noties.jlatexmath.JLatexMathDrawable

@Composable
fun MathView(latex: String, modifier: Modifier = Modifier) {
    if (latex.isBlank()){
        return
    }

  val color=MaterialTheme.colorScheme.onBackground.toArgb()

    val drawable: JLatexMathDrawable? = remember(latex) { parseMath(latex, color) }


    if (drawable==null){
        Text(latex,modifier=modifier)
    }else{
        Image(painter = rememberDrawablePainter(drawable = drawable), contentDescription = null, modifier = modifier)
    }
}

@Composable
fun MathView(drawable: JLatexMathDrawable?, modifier: Modifier = Modifier) {

    if (drawable==null){
        Text("失败",modifier=modifier)
    }else{
        Image(painter = rememberDrawablePainter(drawable = drawable), contentDescription = null, modifier = modifier, contentScale = ContentScale.Inside)
    }
}



fun parseMath(latex: String, color: Int=0):JLatexMathDrawable?{
    return try {
        JLatexMathDrawable.builder(latex)
            .textSize(70f)
            .padding(8)
            .color(color)
            .align(JLatexMathDrawable.ALIGN_RIGHT)
            .build()
    }catch (_:Exception){
        null
    }
}