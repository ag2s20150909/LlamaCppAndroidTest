package me.ag2s.app


import android.content.Intent
import android.llama.cpp.EventState
import android.llama.cpp.LLamaMessage
import android.llama.cpp.isUser
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ag2s.app.markdown.MarkdownText


@Composable
fun ChatMainScreen(model: MainViewModel, modifier: Modifier) {
    val uiState: ChatUiState by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val modelFile = context.getExternalFilesDir("model")!!.resolve("test.gguf")
        if (modelFile.exists()) {
            model.load(modelFile.absolutePath)
        }

    }

    if(uiState.state==EventState.Busy){
        KeepScreenOn()
    }


    LaunchedEffect(uiState.key()) {
        listState.animateScrollToItem(uiState.messages.size)
    }

    val showSpeed by remember {
        derivedStateOf {
           uiState.speed>0&&uiState.state==EventState.Loaded&&uiState.messages.isNotEmpty()
        }
    }





    ConstraintLayout(modifier = modifier) {
        val (messages, chatBox) = createRefs()
        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .constrainAs(messages) {
                top.linkTo(parent.top)
                bottom.linkTo(chatBox.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                height = Dimension.fillToConstraints
            },
            state = listState) {


            items(uiState.messages) {
                ChatItem(it)
            }
            if (showSpeed){
                item {
                    Text("${uiState.speed} token/s",modifier=Modifier.padding(8.dp))
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
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp)) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Row(Modifier.align(if (message.isUser()) Alignment.End else Alignment.Start)) {
            Text(message.role.name)

        }
        Box(
            modifier = Modifier
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
                MarkdownText(message.content)
                //Text(message.content)
            }

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


    fun makeIntent():Intent{
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