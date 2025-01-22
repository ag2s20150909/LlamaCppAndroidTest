package android.llama.cpp

import android.util.Log
import androidx.annotation.FloatRange
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File


@Keep
object LLamaAndroid {

    init {
        val logTag = "LLamaAndroid"
//        System.loadLibrary("GLES_mali")
//        System.loadLibrary("dmabufheap")
        System.loadLibrary("llama-android")
        val cpuFeatures = getCPUFeatures()
        val hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp")
        val hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp")
        val hasSve = cpuFeatures.contains("sve")
        val hasI8mm = cpuFeatures.contains("i8mm")
        val isAtLeastArmV82 =
            cpuFeatures.contains("asimd") && cpuFeatures.contains("crc32") && cpuFeatures.contains("aes")
        val isAtLeastArmV84 = cpuFeatures.contains("dcpop") && cpuFeatures.contains("uscat")

        Log.d(logTag, "CPU features: $cpuFeatures")
        Log.d(logTag, "- hasFp16: $hasFp16")
        Log.d(logTag, "- hasDotProd: $hasDotProd")
        Log.d(logTag, "- hasSve: $hasSve")
        Log.d(logTag, "- hasI8mm: $hasI8mm")
        Log.d(logTag, "- isAtLeastArmV82: $isAtLeastArmV82")
        Log.d(logTag, "- isAtLeastArmV84: $isAtLeastArmV84")
    }

    // Enforce only one instance of Llm.
    private val _instance: LLamaAndroid = this

    fun instance(): LLamaAndroid = _instance

    fun getInfo() = systemInfo()


    fun getCPUFeatures(): String {
        val cpuInfo = File("/proc/cpuinfo").readText()
        val cpuFeatures =
            cpuInfo
                .substringAfter("Features")
                .substringAfter(":")
                .substringBefore("\n")
                .trim()
        return cpuFeatures
    }

    private var nativePtr = 0L


    fun setChatTemple(temple: String) {
        assert(nativePtr != 0L) { "Model is not loaded. Use LLamaAndroid.create to load the model" }
        setChatTemple(nativePtr, temple)
    }


    suspend fun create(
        modelPath: String,
        @FloatRange(from = 0.0, to = 1.0)
        minP: Float = 0.1f,
        @FloatRange(from = 0.0, to = 2.0)
        temperature: Float,
        storeChats: Boolean,
    ) = withContext(Dispatchers.IO) {
        nativePtr = loadModel(modelPath, minP, temperature, storeChats)
    }

    fun addUserMessage(message: String) {
        assert(nativePtr != 0L) { "Model is not loaded. Use LLamaAndroid.create to load the model" }
        addChatMessage(nativePtr, message, "user")
    }

    fun addSystemPrompt(prompt: String) {
        assert(nativePtr != 0L) { "Model is not loaded. Use LLamaAndroid.create to load the model" }
        addChatMessage(nativePtr, prompt, "system")
    }

    fun addAssistantMessage(message: String) {
        assert(nativePtr != 0L) { "Model is not loaded. Use LLamaAndroid.create to load the model" }
        addChatMessage(nativePtr, message, "assistant")
    }

    fun getResponseGenerationSpeed(): Float {
        assert(nativePtr != 0L) { "Model is not loaded. Use LLamaAndroid.create to load the model" }
        return getResponseGenerationSpeed(nativePtr)
    }

    fun getResponse(query: String): Flow<String> =
        flow {
            assert(nativePtr != 0L) { "Model is not loaded. Use LLamaAndroid.create to load the model" }
            startCompletion(nativePtr, query)
            var piece = completionLoop(nativePtr)
            while (piece != "[EOG]") {
                emit(piece)
                piece = completionLoop(nativePtr)
            }
            stopCompletion(nativePtr)
        }.flowOn(Dispatchers.IO)

    fun stopResponse() {
        if (nativePtr != 0L) {
            stopCompletion(nativePtr)
        }
    }

    fun destroyModel() {
        if (nativePtr != 0L) {
            closeModel(nativePtr)
            nativePtr = 0L
        }

    }

    fun clean() {
        if (nativePtr != 0L) {
            cleanChatMessages(nativePtr)
        }
    }


    private external fun systemInfo(): String


    private external fun loadModel(
        modelPath: String,
        minP: Float,
        temperature: Float,
        storeChats: Boolean,
    ): Long

    private external fun setChatTemple(modelPtr: Long, temple: String)

    private external fun closeModel(modelPtr: Long)

    private external fun addChatMessage(
        modelPtr: Long,
        message: String,
        role: String,
    )

    private external fun getResponseGenerationSpeed(modelPtr: Long): Float


    private external fun startCompletion(
        modelPtr: Long,
        prompt: String,
    )

    private external fun completionLoop(modelPtr: Long): String

    private external fun stopCompletion(modelPtr: Long)
    private external fun cleanChatMessages(modelPtr: Long)
}