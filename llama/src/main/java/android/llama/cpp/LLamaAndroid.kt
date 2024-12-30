package android.llama.cpp

import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread


@Keep
object LLamaAndroid {
    private val tag: String? = this::class.simpleName

    init {
        System.loadLibrary("llama-android")
    }


    private val threadLocalState: ThreadLocal<State> = ThreadLocal.withInitial { State.Idle }
    val eventState: StateFlow<EventState> get() = _eventState.asStateFlow()
    private val _eventState: MutableStateFlow<EventState> = MutableStateFlow(EventState.Idle)


    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llm-RunLoop") {
            Log.d(tag, "Dedicated thread for native code: ${Thread.currentThread().name}")

            // No-op if called more than once.
            System.loadLibrary("llama-android")

            // Set llama log handler to Android
            logToAndroid()
            backendInit(false)

            Log.d(tag, systemInfo())

            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
                Log.e(tag, "Unhandled exception", exception)
            }
        }
    }.asCoroutineDispatcher()

    fun getInfo()=systemInfo()

    private val nlen: Int = 1024

    private external fun logToAndroid()
    private external fun loadModel(filename: String): Long
    private external fun freeModel(model: Long)
    private external fun newContext(model: Long): Long
    private external fun freeContext(context: Long)
    private external fun backendInit(numa: Boolean)
    private external fun backendFree()
    private external fun newBatch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun freeBatch(batch: Long)
    private external fun newSampler(): Long
    private external fun freeSampler(sampler: Long)
    private external fun benchModel(context: Long, model: Long, batch: Long, pp: Int, tg: Int, pl: Int, nr: Int): String

    private external fun systemInfo(): String

    private external fun completionInit(context: Long, batch: Long, text: String, nLen: Int): Int

    private external fun applyTemple(context: Long,messages:List<Message>):String?


    private external fun completionLoop(context: Long, batch: Long, sampler: Long, nLen: Int, ncur: IntVar): String?

    private external fun kvCacheClear(context: Long)



    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String {
        return withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    Log.d(tag, "bench(): $state")
                    benchModel(state.context, state.model, state.batch, pp, tg, pl, nr)
                }

                else -> throw IllegalStateException("No model loaded")
            }
        }
    }

    suspend fun load(pathToModel: String) {
        withContext(runLoop) {
            when (threadLocalState.get()) {
                is State.Idle -> {
                    _eventState.emit(EventState.Idle)
                    val model = loadModel(pathToModel)
                    if (model == 0L)  throw IllegalStateException("load_model() failed")

                    val context = newContext(model)
                    if (context == 0L) throw IllegalStateException("new_context() failed")

                    val batch = newBatch(512, 0, 1)
                    if (batch == 0L) throw IllegalStateException("new_batch() failed")

                    val sampler = newSampler()
                    if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

                    Log.i(tag, "Loaded model $pathToModel")
                    threadLocalState.set(State.Loaded(model, context, batch, sampler))
                    _eventState.emit(EventState.Loaded)
                }
                else -> throw IllegalStateException("Model already loaded")
            }
        }
    }

    fun clearContext(){
        CoroutineScope(runLoop).launch {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    kvCacheClear(state.context)
                }

                else -> {}
            }
        }

    }



    fun chat(messages: List<Message>): Flow<String> = flow {

            when(val state = threadLocalState.get()){
                is State.Loaded -> {
                    _eventState.emit(EventState.Busy)
                    applyTemple(state.context,messages)?.let { format->

                        val ncur = IntVar(completionInit(state.context,state.batch,format,nlen))
                        while (ncur.value <= nlen) {
                            val str = completionLoop(state.context, state.batch, state.sampler, nlen, ncur)
                            if (str==null){
                                _eventState.emit(EventState.Loaded)
                                break
                            }
                            _eventState.emit(EventState.Busy)
                            emit(str)
                        }

                        _eventState.emit(EventState.Loaded)
                        emit("")
                        kvCacheClear(state.context)


                    }
                    _eventState.emit(EventState.Loaded)





                }
                else -> {

                }
            }






    }.flowOn(runLoop)

//    fun send(message: ChatTemple): Flow<String> = flow {
//        when (val state = threadLocalState.get()) {
//            is State.Loaded -> {
//                _eventState.emit(EventState.Busy)
//                message.cleanCurrent()
//                val ncur = IntVar(completionInit(state.context, state.batch, message.buildChat(), nlen))
//                while (ncur.value <= nlen) {
//                    val str = completionLoop(state.context, state.batch, state.sampler, nlen, ncur)
//                    if (str==null){
//                        _eventState.emit(EventState.Loaded)
//                        break
//                    }
//                    if (message.isStop(str)){
//                        _eventState.emit(EventState.Loaded)
//                        break
//                    }
//                    _eventState.emit(EventState.Busy)
//                    emit(str)
//                }
//                _eventState.emit(EventState.Loaded)
//                emit("")
//                kvCacheClear(state.context)
//            }
//            else -> {}
//        }
//    }.flowOn(runLoop)

    /**
     * Unloads the model and frees resources.
     *
     * This is a no-op if there's no model loaded.
     */
    suspend fun unload() {
        withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    freeContext(state.context)
                    freeModel(state.model)
                    freeBatch(state.batch)
                    freeSampler(state.sampler);
                    _eventState.emit(EventState.Idle)
                    threadLocalState.set(State.Idle)
                }
                else -> {}
            }
        }
    }

//    companion object {


        @Keep
        private class IntVar(value: Int) {
            @Volatile
            var value: Int = value
                private set

            fun inc() {
                synchronized(this) {
                    value += 1
                }
            }
        }



        private sealed interface State {
            data object Idle: State
            data class Loaded(val model: Long, val context: Long, val batch: Long, val sampler: Long): State
        }

        sealed interface EventState{
            data object Idle:EventState
            data object Loaded:EventState
            data object Busy:EventState
        }


//        // Enforce only one instance of Llm.
//        private val _instance: LLamaAndroid = LLamaAndroid()

        fun instance(): LLamaAndroid = this
    //}
}
