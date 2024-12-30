package me.ag2s.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.llama.cpp.LLamaAndroid
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import me.ag2s.app.ui.theme.NdkTestTheme

class MainActivity(

) : ComponentActivity() {
    private val tag: String? = this::class.simpleName




    private val viewModel: MainViewModel by viewModels()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.initTTs(this)
        viewModel.changePrompt(this)




        viewModel.log("Downloads directory: ${getExternalFilesDir(null)}")
        viewModel.log("Downloads directory: ${LLamaAndroid.instance().getInfo()}")
        enableEdgeToEdge()
        setContent {
            NdkTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatMainScreen(viewModel,Modifier.fillMaxSize().padding(innerPadding).imePadding())
                    //MainCompose(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.changePrompt(this)
    }
}

//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun MainCompose( viewModel: MainViewModel,modifier: Modifier){
//    val clipboardManager = LocalClipboardManager.current
//    val context= LocalContext.current
//
//    val uiState:ChatUiState by viewModel.uiState.collectAsStateWithLifecycle()
//
//    val pickPictureLauncher = rememberLauncherForActivityResult(
//        ActivityResultContracts.GetContent()
//    ) { imageUri ->
//        if (imageUri != null) {
//              viewModel.copyDir(context,imageUri)
//        }
//    }
//    KeepScreenOn()
//
//    Column(modifier = modifier) {
//        val scrollState = rememberLazyListState()
//        Box(modifier = Modifier.weight(1f)) {
//            LazyColumn(state = scrollState) {
//                items(uiState.messages) {
//                    Text(
//                        it.content,
//                        modifier = Modifier.padding(8.dp)
//                    )
//                }
//            }
//        }
//
//        var input by remember { mutableStateOf("") }
//        TextField(
//            value = input,
//            onValueChange = { input=it },
//            modifier = Modifier.fillMaxWidth().padding(8.dp),
//            label = { Text("Message") },
//            leadingIcon = {
//                Button({ viewModel.clear() }) { Text("C") }
//            },
//            trailingIcon = {
//                Button({ viewModel.send(input);input="" }, enabled = uiState.canSend) { Text("S") }
//            }
//        )
//        Row {
//
//
//            Button({ viewModel.bench(8, 4, 1) }) { Text("B") }
//            val cameraPermissionState = rememberPermissionState(
//                android.Manifest.permission.RECORD_AUDIO
//            )
//            Button({
//                val s=uiState.messages.last().content
//                viewModel.say(s,context)
//
//            }) { Text("Copy") }
//
//
//            Button({
//                val modelFile= context.getExternalFilesDir("model")!!.resolve("test.gguf")
//                if (modelFile.exists()){
//                    viewModel.load(modelFile.absolutePath)
//                }else{
//                    pickPictureLauncher.launch("*/*")
//                }
//
//
//            }) { Text("L") }
//        }
//
//    }
//}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NdkTestTheme {
        Greeting("Android")
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