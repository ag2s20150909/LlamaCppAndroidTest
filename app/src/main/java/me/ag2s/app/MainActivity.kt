package me.ag2s.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import me.ag2s.app.ui.theme.NdkTestTheme


class MainActivity(

) : ComponentActivity() {
    private val tag: String? = this::class.simpleName


    private val viewModel: MainViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.initTTs(this)
        viewModel.changePrompt(this)

        enableEdgeToEdge()
        setContent {
            NdkTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->


                    ChatMainScreen(
                        viewModel,
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .imePadding()
                    )


                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.changePrompt(this)
    }

    override fun onStop() {

        super.onStop()
    }

    override fun onDestroy() {
        viewModel.closeModel()
        viewModel.releasesTts()
        super.onDestroy()
    }
}



@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}