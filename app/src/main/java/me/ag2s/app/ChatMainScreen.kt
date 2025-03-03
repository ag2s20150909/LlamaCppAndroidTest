package me.ag2s.app


import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.llama.cpp.EventState
import android.llama.cpp.LLamaMessage
import android.llama.cpp.isUser
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ag2s.app.markdown.ComposeVisitor
import me.ag2s.app.markdown.MdConstants.parser
import me.ag2s.app.markdown.MarkdownText
import org.intellij.markdown.ast.accept
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


@Composable
fun ChatMainScreen(model: MainViewModel, modifier: Modifier) {
    val uiState: ChatUiState by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val modelFile = context.getExternalFilesDir("model")!!.resolve("test.gguf")
        if (modelFile.exists()) {
            model.load(modelFile.absolutePath)
        }

    }

    if (uiState.state == EventState.Busy) {
        KeepScreenOn()
    }


    LaunchedEffect(uiState.key()) {
        listState.animateScrollToItem(uiState.messages.size)
    }

    val showSpeed by remember {
        derivedStateOf {
            uiState.speed > 0 && uiState.state == EventState.Loaded && uiState.messages.isNotEmpty()
        }
    }





    ConstraintLayout(modifier = modifier) {
        val (messages, chatBox) = createRefs()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .constrainAs(messages) {
                    top.linkTo(parent.top)
                    bottom.linkTo(chatBox.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    height = Dimension.fillToConstraints
                },
            state = listState
        ) {


            items(uiState.messages) {
                ChatItem(it)
            }
            if (showSpeed) {
                item {
                    Text("${uiState.speed} token/s", modifier = Modifier.padding(8.dp))
                }
            }


        }
        ChatBox(uiState, model,
            Modifier
                .fillMaxWidth()
                .constrainAs(chatBox) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })


    }


    if (uiState.showDialog) {
        var prompt by remember(uiState.system) { mutableStateOf(uiState.system) }


        Dialog(onDismissRequest = {
            model.closeDialog(prompt, false, context)
        }) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(16.dp)
            ) {
                TextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    minLines = 3,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    label = { Text("系统提示词") },
                )

                Row {

                    Button(onClick = {
                        model.closeDialog(prompt, false, context)
                    }) {
                        Text("取消")
                    }


                    Button(onClick = {
                        model.closeDialog(prompt, true, context)

                    }) {
                        Text("保存")
                    }
                }
            }

        }
    }


}

@Composable
private fun ChatItem(message: LLamaMessage) {
    val clipboardManager = LocalClipboardManager.current
    val focusManger = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current

    var format by remember { mutableStateOf(!message.isUser()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Text(message.role.name)
        Box(
            modifier = Modifier
                .drawWithContent {
                    // call record to capture the content in the graphics layer
                    graphicsLayer.record {
                        // draw the contents of the composable into the graphics layer
                        this@drawWithContent.drawContent()
                    }
                    // draw the graphics layer on the visible canvas
                    drawLayer(graphicsLayer)
                }
                .align(if (message.isUser()) Alignment.End else Alignment.Start)
                .clip(
                    RoundedCornerShape(
                        topStart = 48f,
                        topEnd = 48f,
                        bottomStart = if (message.isUser()) 48f else 0f,
                        bottomEnd = if (message.isUser()) 0f else 48f
                    )
                )
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(16.dp)
        ) {


            SelectionContainer {

                MarkdownText(message.content, format)
                //Text(message.content)
            }


        }
        Row() {

            ClickText("Copy") {
                focusManger.clearFocus()
                val text = if (format) {
                    val root = parser.buildMarkdownTreeFromString(message.content)
                    val result = AnnotatedString.Builder()
                    root.accept(ComposeVisitor(result, message.content))
                    result.toAnnotatedString()
                } else {
                    AnnotatedString(message.content)
                }
                clipboardManager.setText(text)
            }
            ClickText("Format") {
                format = !format
                focusManger.clearFocus()
            }

            ClickText("Save") {
                focusManger.clearFocus()
                coroutineScope.launch {
                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                    bitmap.saveToCache(context)
                }
            }


        }
    }

}

@Composable
private fun ClickText(text: String, fontSize: TextUnit = 12.sp, onClick: () -> Unit) {
    Text(
        text, modifier = Modifier
            .clickable {
                onClick()
            }
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp), fontSize = fontSize
    )
}

fun Bitmap.compress(stream: OutputStream) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        this.compress(Bitmap.CompressFormat.WEBP_LOSSY, 20, stream)
    } else {
        this.compress(Bitmap.CompressFormat.WEBP, 20, stream)
    }
}

@SuppressLint("SdCardPath")
fun Bitmap.saveToCache(context: Context) {
    val timestamp = System.currentTimeMillis()
    //Tell the media scanner about the new file so that it is immediately available to the user.
    val values = ContentValues()
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
    values.put(MediaStore.Images.Media.DATE_ADDED, timestamp)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
        values.put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "Pictures/" + context.getString(R.string.app_name)
        )
        values.put(MediaStore.Images.Media.IS_PENDING, true)
        val uri =
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    this.compress(it)
                }
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)

                Toast.makeText(context, "Saved...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                //Log.e(TAG, "saveBitmapImage: ", e)
            }
        }
    } else {
        val imageFile = File("/sdcard/${Environment.DIRECTORY_PICTURES}/${timestamp}.webp")
        try {
            FileOutputStream(imageFile).use {
                this.compress(it)
            }

            values.put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            Toast.makeText(context, "Saved...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            //Log.e(TAG, "saveBitmapImage: ", e)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBox(uiState: ChatUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val focusManger = LocalFocusManager.current
    val context = LocalContext.current
    val selectModel = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->

        activityResult.data?.let {
            it.data?.let { uri ->
                viewModel.copyDir(context, uri)
            }
        }
    }


    fun makeIntent(): Intent {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                setType("application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        Environment
                            .getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS,
                            ).toUri(),
                    )
                }
            }
        return intent
    }


    fun reset() {
        scope.launch {
            dismissState.reset()
        }
    }


    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                        reset()
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                //从头到尾滑动时读取操作
                AnimatedVisibility(

                    visible = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd,
                    enter = fadeIn()
                ) {

                    FlowRow {

                        Button(onClick = {
                            reset()
                            scope.launch(Dispatchers.IO) {
                                viewModel.closeModel()
                                context.getExternalFilesDir("model")?.resolve("test.gguf")?.delete()

                                selectModel.launch(makeIntent())
                            }

                        }) {
                            Text("导入")
                        }

                        Button(onClick = {
                            reset()
                            viewModel.openDialog()


                        }) {
                            Text("修改")
                        }

                        Button(onClick = {
                            reset()
                            viewModel.closeModel()


                        }) {
                            Text("关闭")
                        }


                        Button(onClick = {
                            reset()
                            viewModel.bench()


                        }) {
                            Text("Bench")
                        }


                    }

                }

                Spacer(modifier = Modifier.weight(1f))
            }
        },
        enableDismissFromEndToStart = false,
        enableDismissFromStartToEnd = true
    ) {
        //val context = LocalContext.current


        if (uiState.state == EventState.Idle) {
            Button({
                val modelFile = context.getExternalFilesDir("model")!!.resolve("test.gguf")
                if (modelFile.exists()) {
                    viewModel.load(modelFile.absolutePath)
                } else {
                    selectModel.launch(makeIntent())
                }


            }) { Text("选择模型") }
        } else {
            var input by remember { mutableStateOf("") }
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                label = { Text("Message") },
                leadingIcon = {
                    Button({ viewModel.clear() }) { Text("C") }
                },
                trailingIcon = {
                    Button(
                        { focusManger.clearFocus();viewModel.send(input, context);input = "" },
                        enabled = uiState.state == EventState.Loaded
                    ) { Text("S") }
                }
            )
        }

    }


}


@Composable
fun ChatLayout(
    modifier: Modifier = Modifier,
    chatBar: @Composable ColumnScope.() -> Unit = {},
    msgContent: @Composable ColumnScope.() -> Unit = {},
) {
    ConstraintLayout(
        modifier = modifier,
    ) {
        val (messages, chatBox) = createRefs()

        Column(modifier = Modifier
            .fillMaxWidth()
            .constrainAs(messages) {
                top.linkTo(parent.top)
                bottom.linkTo(chatBox.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                height = Dimension.fillToConstraints
            }) {
            msgContent()

        }
        Column(modifier = Modifier
            .fillMaxWidth()
            .constrainAs(chatBox) {
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }) {

            chatBar()
        }


    }
}