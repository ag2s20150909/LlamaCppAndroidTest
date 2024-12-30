package me.ag2s.app

import android.content.Context
import android.llama.cpp.LLamaAndroid
import android.llama.cpp.Message
import android.llama.cpp.Role
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class ChatUiState(
    val system:String ="You are a helpful assistant.",
    val state:LLamaAndroid.EventState=LLamaAndroid.EventState.Idle,
    val messages: List<Message> = emptyList()
)



fun ChatUiState.updateMessage(s: String): ChatUiState {
   return this.copy(messages = this.messages.updateMessage(s))
}

fun List<Message>.updateMessage(s: String): List<Message> {

    return if(this.last().role== Role.Assistant){
        val mess=this.last()
        this.dropLast(1)+mess.copy(content = mess.content+s)
    }else{
        this+ listOf(Message(Role.Assistant,s))
    }
}

class MainViewModel(private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance()) :
    ViewModel() {
    companion object {
        @JvmStatic
        private val NanosPerSecond = 1_000_000_000.0
        private const val TAG="llama-android.cpp "
    }



    val uiState: StateFlow<ChatUiState> get() = _uiState.asStateFlow()
    private val _uiState: MutableStateFlow<ChatUiState> = MutableStateFlow(ChatUiState())

    init {
        viewModelScope.launch {

            llamaAndroid.eventState.collect{ss->
                _uiState.update { it.copy(state = ss) }
            }


        }
    }


    fun changePrompt(context: Context){
        viewModelScope.launch {
            val modelFile = context.getExternalFilesDir("prompt")!!.resolve("system.txt")
            if (modelFile.exists()){
                modelFile.readText().let {ss->
                    if (ss.isNotBlank()){
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
                llamaAndroid.unload()
            } catch (exc: IllegalStateException) {
                //messages += exc.message!!
            }
        }
    }

    private var mTts: TextToSpeech? = null


    fun initTTs(context: Context){
        mTts = TextToSpeech(context) { }
    }

    private fun say(s: String, context: Context){
        val speak = mTts?.speak(
            s,
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        );
        if (speak==TextToSpeech.ERROR){
            initTTs(context)
        }


    }


    fun send(prompt: String,context: Context) {



        if (prompt.isBlank()){
            return
        }
        viewModelScope.launch {


            val systemMessage=Message(Role.System,uiState.value.system)
            val userMessage = Message(Role.User, prompt)

            _uiState.update { it.copy(messages = it.messages + listOf(userMessage)) }


            try {
                llamaAndroid.chat(listOf(systemMessage)+uiState.value.messages).catch {
                    Log.e(TAG, "send() failed", it)
                }.collect{s->

                   _uiState.update { it.updateMessage(s) }

                    uiState.value.let {
                        if (it.state==LLamaAndroid.EventState.Loaded){
                            say(it.messages.last().content,context)

                        }


                    }



                }
            }catch (e:Exception){
                e.printStackTrace()
            }


        }
    }

    fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
        viewModelScope.launch {
            try {
                val start = System.nanoTime()
                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
                val end = System.nanoTime()

                Log.e(TAG,warmupResult)

                //messages += warmupResult

                val warmup = (end - start).toDouble() / NanosPerSecond
                //messages += "Warm up time: $warmup seconds, please wait..."
                Log.e(TAG,"Warm up time: $warmup seconds, please wait...")

                if (warmup > 5.0) {
                    Log.e(TAG,"Warm up took too long, aborting benchmark")
                   // messages += "Warm up took too long, aborting benchmark"
                    return@launch
                }
                Log.e(TAG,llamaAndroid.bench(512, 128, 1, 3))
                //messages += llamaAndroid.bench(512, 128, 1, 3)
            } catch (exc: IllegalStateException) {
                Log.e(TAG, "bench() failed", exc)
                //messages += exc.message!!
            }
        }
    }

    fun load(pathToModel: String) {
        viewModelScope.launch {
            try {
                //_uiState.update { it.copy(canSend = llamaAndroid.canSend)}

                llamaAndroid.load(pathToModel)
                //messages += "Loaded $pathToModel"
                //_uiState.update { it.copy(canSend = llamaAndroid.canSend)}
            } catch (exc: IllegalStateException) {
                Log.e(TAG, "load() failed", exc)
                //_uiState.update { it.copy(canSend = llamaAndroid.canSend)}
                //messages += exc.message!!
            }
        }
    }


    fun copyDir(context: Context, uri: Uri) {
        viewModelScope.launch {
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

    fun updateMessage(newMessage: String) {
        //message = newMessage
    }


    fun closeModel(){
        viewModelScope.launch {
            try {
                llamaAndroid.unload()
            } catch (exc: IllegalStateException) {
                Log.e(TAG,exc.stackTraceToString())
            }
        }
    }

    fun clear() {
        llamaAndroid.clearContext();
        _uiState.update {
            //it.temple.clean()
            it.copy(messages = emptyList())
        }
       // messages = listOf()
    }

    fun log(message: String) {

    }
}
