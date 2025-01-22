package me.ag2s.app

import android.content.Context
import android.llama.cpp.EventState
import android.llama.cpp.LLamaAndroid
import android.llama.cpp.LLamaMessage
import android.llama.cpp.Role
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class ChatUiState(
    val showDialog: Boolean = false,
    val system: String = "You are a helpful assistant.",
    val state: EventState = EventState.Idle,
    val speed: Float = 0f,
    val messages: List<LLamaMessage> = emptyList()
)


fun ChatUiState.updateMessage(s: String): ChatUiState {
    return this.copy(messages = this.messages.updateMessage(s))
}

fun ChatUiState.key() = if (this.messages.isEmpty()) {
    "empty"
} else {
    this.messages.last().content
}


fun List<LLamaMessage>.updateMessage(s: String): List<LLamaMessage> {

    return if (this.isEmpty()) {
        listOf(LLamaMessage(Role.Assistant, s))
    } else if (this.last().role == Role.Assistant) {
        val mess = this.last()
        this.dropLast(1) + mess.copy(content = mess.content + s)
    } else {
        this + listOf(LLamaMessage(Role.Assistant, s))
    }
}

class MainViewModel(private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance()) :
    ViewModel() {
    companion object {
        private const val TAG = "llama-android.cpp "
    }

    val uiState: StateFlow<ChatUiState> get() = _uiState.asStateFlow()
    private val _uiState: MutableStateFlow<ChatUiState> = MutableStateFlow(ChatUiState())


    fun openDialog() {
        _uiState.update {
            it.copy(showDialog = true)
        }
    }


    fun closeDialog(prompt: String, save: Boolean = false, context: Context) {


        if (save) {
            _uiState.update {
                it.copy(showDialog = false, system = prompt)
            }
            val modelFile = context.getExternalFilesDir("prompt")!!.resolve("system.txt")
            if (modelFile.exists()) {
                modelFile.writeText(prompt)
            }

        } else {
            _uiState.update {
                it.copy(showDialog = false)
            }
        }

    }


    fun changePrompt(context: Context) {
        viewModelScope.launch {
            val modelFile = context.getExternalFilesDir("prompt")!!.resolve("system.txt")
            if (modelFile.exists()) {
                modelFile.readText().let { ss ->
                    if (ss.isNotBlank()) {
                        _uiState.update { it.copy(system = ss) }

                    }

                }
            }


        }
    }


    override fun onCleared() {
        super.onCleared()
        mTts?.let {
            it.stop()
            it.shutdown()
        }
        viewModelScope.launch {
            try {
                closeModel()
                releasesTts()
                //llamaAndroid.unload()
            } catch (exc: IllegalStateException) {
                //messages += exc.message!!
            }
        }
    }

    fun clear() {
        viewModelScope.launch {

            withContext(Dispatchers.IO) {
                if (uiState.value.state == EventState.Busy) {
                    llamaAndroid.stopResponse()
                } else {
                    llamaAndroid.clean()
                    _uiState.update {
                        it.copy(messages = emptyList())
                    }
                }

            }
        }


    }

    private var mTts: TextToSpeech? = null


    fun initTTs(context: Context) {

        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                mTts = TextToSpeech(context) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        mTts?.shutdown()
                        mTts = null
                    }

                }
            }


        }

    }

    fun releasesTts() {
        mTts?.shutdown()
    }

    private fun say(s: String, context: Context) {

        viewModelScope.launch {
            if (mTts == null) {

                initTTs(context)

                delay(1000)
            }

            val speak = mTts?.speak(
                s,
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
            );
            if (speak == TextToSpeech.ERROR) {

                initTTs(context)

            }

        }


    }


    fun send(prompt: String, context: Context) {


        if (prompt.isBlank()) {

            return
        }
        viewModelScope.launch {


            val systemMessage = LLamaMessage(Role.System, uiState.value.system)
            val userMessage = LLamaMessage(Role.User, prompt)

            _uiState.update { it.copy(messages = it.messages + listOf(userMessage)) }

            //useOllana=true


            try {


                if (uiState.value.messages.size <= 2) {
                    llamaAndroid.addSystemPrompt(systemMessage.content)
                }
                _uiState.update { it.copy(state = EventState.Busy) }
                llamaAndroid.getResponse(query = prompt).flowOn(Dispatchers.IO).catch {

                }.onCompletion {
                    _uiState.update {
                        it.copy(
                            state = EventState.Loaded,
                            speed = llamaAndroid.getResponseGenerationSpeed()
                        )
                    }
                    say(uiState.value.messages.last().content, context)

                }.collect { s ->
                    _uiState.update { it.copy(state = EventState.Busy) }
                    _uiState.update { it.updateMessage(s) }

                }


            } catch (e: Exception) {
                e.printStackTrace()
            }


        }
    }


    private fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
        viewModelScope.launch {
//            try {
//                val start = System.nanoTime()
//                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
//                val end = System.nanoTime()
//
//                Log.e(TAG, warmupResult)
//
//                //messages += warmupResult
//
//                val warmup = (end - start).toDouble() / NanosPerSecond
//                //messages += "Warm up time: $warmup seconds, please wait..."
//                Log.e(TAG, "Warm up time: $warmup seconds, please wait...")
//
//                if (warmup > 5.0) {
//                    Log.e(TAG, "Warm up took too long, aborting benchmark")
//                    // messages += "Warm up took too long, aborting benchmark"
//                    return@launch
//                }
//                Log.e(TAG, llamaAndroid.bench(512, 128, 1, 3))
//                //messages += llamaAndroid.bench(512, 128, 1, 3)
//            } catch (exc: IllegalStateException) {
//                Log.e(TAG, "bench() failed", exc)
//                //messages += exc.message!!
//            }
        }
    }

    fun load(pathToModel: String) {
        viewModelScope.launch {
            try {


                withContext(Dispatchers.IO) {
                    llamaAndroid.create(pathToModel, 0.05f, 1.5f, true)

                    //llamaAndroid.setChatTemple(  "{%- if tools %}\n    {{- '<|im_start|>system\\n' }}\n    {%- if messages[0]['role'] == 'system' %}\n        {{- messages[0]['content'] }}\n    {%- else %}\n        {{- 'You are Qwen, created by Alibaba Cloud. You are a helpful assistant.' }}\n    {%- endif %}\n    {{- \"\\n\\n# Tools\\n\\nYou may call one or more functions to assist with the user query.\\n\\nYou are provided with function signatures within <tools></tools> XML tags:\\n<tools>\" }}\n    {%- for tool in tools %}\n        {{- \"\\n\" }}\n        {{- tool | tojson }}\n    {%- endfor %}\n    {{- \"\\n</tools>\\n\\nFor each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:\\n<tool_call>\\n{\\\"name\\\": <function-name>, \\\"arguments\\\": <args-json-object>}\\n</tool_call><|im_end|>\\n\" }}\n{%- else %}\n    {%- if messages[0]['role'] == 'system' %}\n        {{- '<|im_start|>system\\n' + messages[0]['content'] + '<|im_end|>\\n' }}\n    {%- else %}\n        {{- '<|im_start|>system\\nYou are Qwen, created by Alibaba Cloud. You are a helpful assistant.<|im_end|>\\n' }}\n    {%- endif %}\n{%- endif %}\n{%- for message in messages %}\n    {%- if (message.role == \"user\") or (message.role == \"system\" and not loop.first) or (message.role == \"assistant\" and not message.tool_calls) %}\n        {{- '<|im_start|>' + message.role + '\\n' + message.content + '<|im_end|>' + '\\n' }}\n    {%- elif message.role == \"assistant\" %}\n        {{- '<|im_start|>' + message.role }}\n        {%- if message.content %}\n            {{- '\\n' + message.content }}\n        {%- endif %}\n        {%- for tool_call in message.tool_calls %}\n            {%- if tool_call.function is defined %}\n                {%- set tool_call = tool_call.function %}\n            {%- endif %}\n            {{- '\\n<tool_call>\\n{\"name\": \"' }}\n            {{- tool_call.name }}\n            {{- '\", \"arguments\": ' }}\n            {{- tool_call.arguments | tojson }}\n            {{- '}\\n</tool_call>' }}\n        {%- endfor %}\n        {{- '<|im_end|>\\n' }}\n    {%- elif message.role == \"tool\" %}\n        {%- if (loop.index0 == 0) or (messages[loop.index0 - 1].role != \"tool\") %}\n            {{- '<|im_start|>user' }}\n        {%- endif %}\n        {{- '\\n<tool_response>\\n' }}\n        {{- message.content }}\n        {{- '\\n</tool_response>' }}\n        {%- if loop.last or (messages[loop.index0 + 1].role != \"tool\") %}\n            {{- '<|im_end|>\\n' }}\n        {%- endif %}\n    {%- endif %}\n{%- endfor %}\n{%- if add_generation_prompt %}\n    {{- '<|im_start|>assistant\\n' }}\n{%- endif %}\n")


                    _uiState.update { it.copy(state = EventState.Loaded) }
                }

                Log.e(TAG, llamaAndroid.getInfo())
                Log.e(TAG, llamaAndroid.getCPUFeatures())
                //_uiState.update { it.updateMessage(llamaAndroid.getCPUFeatures()) }
                //llamaAndroid.load(pathToModel)
            } catch (exc: IllegalStateException) {
                _uiState.update { it.copy(state = EventState.Idle) }
                Log.e(TAG, "load() failed", exc)
            }
        }
    }


    fun copyDir(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {


            val modelFile = context.getExternalFilesDir("model")!!.resolve("test.gguf")

            if (modelFile.exists()) {
                load(modelFile.absolutePath)
                return@launch
            }



            context.contentResolver.openInputStream(uri)?.use { iss ->
                modelFile.outputStream().use { oss ->
                    iss.copyTo(oss)
                }.also {


                    load(modelFile.absolutePath)
                }

            }

        }
    }


    fun closeModel() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    _uiState.update { it.copy(messages = emptyList()) }
                    llamaAndroid.destroyModel()
                    _uiState.update { it.copy(state = EventState.Idle) }
                }


            } catch (
                exc
                : IllegalStateException
            ) {
                Log.e(TAG, exc.stackTraceToString())
            }
        }
    }


}
