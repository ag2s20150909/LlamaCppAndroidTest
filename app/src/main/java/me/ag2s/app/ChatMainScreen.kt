package me.ag2s.app


import android.app.Activity
import android.llama.cpp.LLamaAndroid
import android.llama.cpp.Message
import android.llama.cpp.isUser
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
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun ChatMainScreen(model: MainViewModel, modifier: Modifier) {
    val uiState: ChatUiState by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        val modelFile = context.getExternalFilesDir("model")!!.resolve("test.gguf")
        if (modelFile.exists()) {
            model.load(modelFile.absolutePath)
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
            }) {


            items(uiState.messages) {
                ChatItem(it)
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


}

@Composable
private fun ChatItem(message: Message) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
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
                Text(message.content)
            }

        }
    }

}



@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBox(uiState: ChatUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val pickPictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            viewModel.copyDir(context, imageUri)
        }
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
                            scope.launch(Dispatchers.IO){
                                context.getExternalFilesDir("model")!!.resolve("test.gguf")?.delete()
                                pickPictureLauncher.launch("*/*")
                            }

                        }) {
                            Text("导入")
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
        val context = LocalContext.current


        if (uiState.state == LLamaAndroid.EventState.Idle) {
            Button({
                val modelFile = context.getExternalFilesDir("model")!!.resolve("test.gguf")
                if (modelFile.exists()) {
                    viewModel.load(modelFile.absolutePath)
                } else {
                    pickPictureLauncher.launch("*/*")
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
                        { viewModel.send(input,context);input = "" },
                        enabled = uiState.state == LLamaAndroid.EventState.Loaded
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