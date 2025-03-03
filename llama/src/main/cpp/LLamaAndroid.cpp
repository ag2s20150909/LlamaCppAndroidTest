// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("ndktest");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("ndktest")
//      }
//    }
#include <jni.h>
#include "llm_inference.h"


jstring systemInfo(JNIEnv *env, [[maybe_unused]] jobject thiz) {
    return env->NewStringUTF(llama_print_system_info());
}

jlong loadModel(
        JNIEnv *env,
        [[maybe_unused]] jobject thiz,
        jstring model_path,
        jfloat min_p,
        jfloat temperature,
        jboolean store
) {
    jboolean isCopy = true;
    const char *model_path_cstr = env->GetStringUTFChars(model_path, &isCopy);
    auto *llmInference = new LLMInference();

    try {
        llmInference->load_model(model_path_cstr, min_p, temperature, store);
    }
    catch (std::runtime_error &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }

    env->ReleaseStringUTFChars(model_path, model_path_cstr);
    return reinterpret_cast<jlong>(llmInference);
}


void addChatMessage(JNIEnv *env, [[maybe_unused]] jobject thiz, jlong model_ptr, jstring message,
                    jstring role) {
    jboolean isCopy = true;
    const char *message_cstr = env->GetStringUTFChars(message, &isCopy);
    const char *role_cstr = env->GetStringUTFChars(role, &isCopy);
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    llmInference->add_chat_message(message_cstr, role_cstr);
    env->ReleaseStringUTFChars(message, message_cstr);
    env->ReleaseStringUTFChars(role, role_cstr);
}

jfloat getResponseGenerationSpeed([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz,
                                  jlong model_ptr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    return llmInference->get_response_generation_time();
}

void closeModel([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong model_ptr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    delete llmInference;
}


void startCompletion(JNIEnv *env, [[maybe_unused]] jobject thiz, jlong model_ptr, jstring prompt) {
    jboolean isCopy = true;
    const char *prompt_cstr = env->GetStringUTFChars(prompt, &isCopy);
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    llmInference->start_completion(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
}


jstring completionLoop(JNIEnv *env, [[maybe_unused]] jobject thiz, jlong model_ptr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    try {
        std::string response = llmInference->completion_loop();
        return env->NewStringUTF(response.c_str());
    }
    catch (std::runtime_error &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}


void stopCompletion([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong model_ptr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    llmInference->stop_completion();
}

void cleanChatMessages([[maybe_unused]] JNIEnv *env, [[maybe_unused]] jobject thiz, jlong model_ptr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    llmInference->clean_message();
}

void setChatTemple(JNIEnv *env, [[maybe_unused]] jobject thiz, jlong model_ptr,
                   jstring temple) {
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    jboolean isCopy = false;
    const char *temple_cstr = env->GetStringUTFChars(temple, &isCopy);
    llmInference->set_chat_temple(temple_cstr);
    env->ReleaseStringUTFChars(temple, temple_cstr);

}

jstring benchModel(JNIEnv *env, [[maybe_unused]] jobject thiz,jlong model_ptr, jint pp, jint tg, jint pl, jint nr) {
    auto *llmInference = reinterpret_cast<LLMInference *>(model_ptr);
    try {
        std::string result = llmInference->bench(pp, tg, pl, nr);
        return env->NewStringUTF(result.c_str());
    }
    catch (std::runtime_error &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, [[maybe_unused]] void *reserved) {
    JNIEnv *env = nullptr;
    jint ret = vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        LOGe("jni_replace JVM ERROR:GetEnv");
        return -1;
    }
    llama_log_set(log_callback, nullptr);
    llama_backend_init();

    JNINativeMethod g_Methods[] = {
            {"loadModel",                  "(Ljava/lang/String;FFZ)J",                 (void *) loadModel},
            {"closeModel",                 "(J)V",                                     (void *) closeModel},
            {"setChatTemple",              "(JLjava/lang/String;)V",                   (void *) setChatTemple},
            {"systemInfo",                 "()Ljava/lang/String;",                     (void *) systemInfo},
            {"addChatMessage",             "(JLjava/lang/String;Ljava/lang/String;)V", (void *) addChatMessage},
            {"startCompletion",            "(JLjava/lang/String;)V",                   (void *) startCompletion},
            {"stopCompletion",             "(J)V",                                     (void *) stopCompletion},
            {"cleanChatMessages",          "(J)V",                                     (void *) cleanChatMessages},
            {"completionLoop",             "(J)Ljava/lang/String;",                    (void *) completionLoop},
            {"getResponseGenerationSpeed", "(J)F",                                     (void *) getResponseGenerationSpeed},
            {"benchModel",                 "(JIIII)Ljava/lang/String;",                 (void *) benchModel},


    };

    jclass cls = env->FindClass("android/llama/cpp/LLamaAndroid");
    if (cls == nullptr) {
        LOGe("FindClass Error");
        return -1;
    }
    //动态注册本地方法
    ret = env->RegisterNatives(cls, g_Methods, sizeof(g_Methods) / sizeof(g_Methods[0]));
    if (ret != JNI_OK) {
        LOGe("Register Error");
        return -1;
    }


    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload([[maybe_unused]] JavaVM *vm, [[maybe_unused]] void *reserved) {
    llama_backend_free();
}


