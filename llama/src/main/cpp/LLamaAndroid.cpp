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
#include <android/log.h>
#include <jni.h>
#include <iomanip>
#include <cmath>
#include <string>
#include <unistd.h>
#include "common.h"

#define TAG "llama-android.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

jclass la_int_var;
jmethodID la_int_var_value;
jmethodID la_int_var_inc;

std::string cached_token_chars;


static bool getString(JNIEnv *env, jstring jValue, std::string &vValue) {
    if (jValue == nullptr) {
        return false;
    }
    auto str = env->GetStringUTFChars(jValue, nullptr);
    if (strlen(str) == 0) {
        return false;
    }
    vValue = str;
    env->ReleaseStringUTFChars(jValue, str);
    return true;
}


bool is_valid_utf8(const char *string) {
    if (!string) {
        return true;
    }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }

    return true;
}

static void log_callback(ggml_log_level level, const char *fmt, void *data) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_INFO) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, fmt, data);
    else __android_log_print(ANDROID_LOG_DEFAULT, TAG, fmt, data);
}

jlong loadModel(JNIEnv *env, jobject, jstring filename) {
    llama_model_params model_params = llama_model_default_params();

    model_params.use_mlock = true;
    model_params.n_gpu_layers = 29;

    auto path_to_model = env->GetStringUTFChars(filename, nullptr);
    LOGi("Loading model from %s", path_to_model);

    auto model = llama_load_model_from_file(path_to_model, model_params);
    env->ReleaseStringUTFChars(filename, path_to_model);

    if (!model) {
        LOGe("load_model() failed");
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "load_model() failed");
        return 0;
    }

    return reinterpret_cast<jlong>(model);
}

void freeModel(JNIEnv *, jobject, jlong model) {
    llama_free_model(reinterpret_cast<llama_model *>(model));
}

jlong newContext(JNIEnv *env, jobject, jlong jmodel) {
    auto model = reinterpret_cast<llama_model *>(jmodel);

    if (!model) {
        LOGe("new_context(): model cannot be null");
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Model cannot be null");
        return 0;
    }

    int n_threads = std::max(1, std::min(8, (int) sysconf(_SC_NPROCESSORS_ONLN) - 2));
    LOGi("Using %d threads", n_threads);

    llama_context_params ctx_params = llama_context_default_params();

    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    ctx_params.flash_attn = true;

    llama_context *context = llama_new_context_with_model(model, ctx_params);

    if (!context) {
        LOGe("llama_new_context_with_model() returned null)");
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
                      "llama_new_context_with_model() returned null)");
        return 0;
    }

    return reinterpret_cast<jlong>(context);
}

void freeContext(JNIEnv *, jobject, jlong context) {
    llama_free(reinterpret_cast<llama_context *>(context));
}

void backendFree(JNIEnv *, jobject) {
    llama_backend_free();
}

void logToAndroid(JNIEnv *, jobject) {
    llama_log_set(log_callback, nullptr);
}

jstring benchModel(
        JNIEnv *env,
        jobject,
        jlong context_pointer,
        jlong model_pointer,
        jlong batch_pointer,
        jint pp,
        jint tg,
        jint pl,
        jint nr
) {
    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto model = reinterpret_cast<llama_model *>(model_pointer);
    const auto batch = reinterpret_cast<llama_batch *>(batch_pointer);

    const int n_ctx = llama_n_ctx(context);

    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp)");

        common_batch_clear(*batch);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(*batch, 0, i, {0}, false);
        }

        batch->logits[batch->n_tokens - 1] = true;
        llama_kv_cache_clear(context);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(context, *batch) != 0) {
            LOGi("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg)");

        llama_kv_cache_clear(context);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {

            common_batch_clear(*batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(*batch, 0, i, {j}, true);
            }

            LOGi("llama_decode() text generation: %d", i);
            if (llama_decode(context, *batch) != 0) {
                LOGi("llama_decode() failed during text generation");
            }
        }

        const auto t_tg_end = ggml_time_us();

        llama_kv_cache_clear(context);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(model)) / 1e9;

    const auto backend = "(Android)"; // TODO: What should this be?

    std::stringstream result;
    result << std::setprecision(2);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";

    return env->NewStringUTF(result.str().c_str());
}

jlong newBatch(JNIEnv *, jobject, jint n_tokens, jint embd,
               jint n_seq_max) {

    // Source: Copy of llama.cpp:llama_batch_init but heap-allocated.



    auto *batch = new llama_batch{
            0,
            nullptr,
            nullptr,
            nullptr,
            nullptr,
            nullptr,
            nullptr,
    };

    if (embd) {
        batch->embd = (float *) malloc(sizeof(float) * n_tokens * embd);
    } else {
        batch->token = (llama_token *) malloc(sizeof(llama_token) * n_tokens);
    }

    batch->pos = (llama_pos *) malloc(sizeof(llama_pos) * n_tokens);
    batch->n_seq_id = (int32_t *) malloc(sizeof(int32_t) * n_tokens);
    batch->seq_id = (llama_seq_id **) malloc(sizeof(llama_seq_id *) * n_tokens);
    for (int i = 0; i < n_tokens; ++i) {
        batch->seq_id[i] = (llama_seq_id *) malloc(sizeof(llama_seq_id) * n_seq_max);
    }
    batch->logits = (int8_t *) malloc(sizeof(int8_t) * n_tokens);

    return reinterpret_cast<jlong>(batch);
}

void freeBatch(JNIEnv *, jobject, jlong batch_pointer) {
    llama_batch_free(*reinterpret_cast<llama_batch *>(batch_pointer));
}

jlong newSampler(JNIEnv *, jobject) {
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return reinterpret_cast<jlong>(smpl);
}

void freeSampler(JNIEnv *, jobject, jlong sampler_pointer) {
    llama_sampler_free(reinterpret_cast<llama_sampler *>(sampler_pointer));

}

void backendInit(JNIEnv *, jobject o, jboolean gg) {
    //ggml_backend_load_all();
    llama_backend_init();
}

jstring systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

jint completionInit(
        JNIEnv *env,
        jobject,
        jlong context_pointer,
        jlong batch_pointer,
        jstring jtext,
        jint n_len
) {

    cached_token_chars.clear();

    const auto text = env->GetStringUTFChars(jtext, nullptr);
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto batch = reinterpret_cast<llama_batch *>(batch_pointer);

    LOGe("prompt is:%s", text);

    const auto tokens_list = common_tokenize(context, text, true, true);

    auto n_ctx = llama_n_ctx(context);
    auto n_kv_req = tokens_list.size() + (n_len - tokens_list.size());

    LOGi("n_len = %d, n_ctx = %d, n_kv_req = %zu", n_len, n_ctx, n_kv_req);

    if (n_kv_req > n_ctx) {
        LOGe("error: n_kv_req > n_ctx, the required KV cache size is not big enough");
    }

    for (auto id: tokens_list) {
        LOGi("%s", common_token_to_piece(context, id).c_str());
    }

    common_batch_clear(*batch);

    // evaluate the initial prompt
    for (auto i = 0; i < tokens_list.size(); i++) {
        common_batch_add(*batch, tokens_list[i], i, {0}, false);
    }

    // llama_decode will output logits only for the last token of the prompt
    batch->logits[batch->n_tokens - 1] = true;

    if (llama_decode(context, *batch) != 0) {
        LOGe("llama_decode() failed");
    }

    env->ReleaseStringUTFChars(jtext, text);

    return batch->n_tokens;
}

jstring completionLoop(
        JNIEnv *env,
        jobject,
        jlong context_pointer,
        jlong batch_pointer,
        jlong sampler_pointer,
        jint n_len,
        jobject intvar_ncur
) {
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto batch = reinterpret_cast<llama_batch *>(batch_pointer);
    const auto sampler = reinterpret_cast<llama_sampler *>(sampler_pointer);
    const auto model = llama_get_model(context);

    if (!la_int_var) la_int_var = env->GetObjectClass(intvar_ncur);
    if (!la_int_var_value) la_int_var_value = env->GetMethodID(la_int_var, "getValue", "()I");
    if (!la_int_var_inc) la_int_var_inc = env->GetMethodID(la_int_var, "inc", "()V");

    // sample the most likely token
    const auto new_token_id = llama_sampler_sample(sampler, context, -1);

    const auto n_cur = env->CallIntMethod(intvar_ncur, la_int_var_value);

    if (llama_token_is_eog(model, new_token_id) || n_cur == n_len) {
        LOGi("finished %d/%d", n_cur, n_len);
        return nullptr;
    }

    auto new_token_chars = common_token_to_piece(context, new_token_id);
    cached_token_chars += new_token_chars;

    jstring new_token = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        new_token = env->NewStringUTF(cached_token_chars.c_str());
        LOGi("cached: %s, new_token_chars: `%s`, id: %d", cached_token_chars.c_str(),
             new_token_chars.c_str(), new_token_id);
        cached_token_chars.clear();
    } else {
        new_token = env->NewStringUTF("");
    }

    common_batch_clear(*batch);
    common_batch_add(*batch, new_token_id, n_cur, {0}, true);

    env->CallVoidMethod(intvar_ncur, la_int_var_inc);

    if (llama_decode(context, *batch) != 0) {
        LOGe("llama_decode() returned null");
    }

    return new_token;
}

jstring applyTemple(JNIEnv *env, jobject thiz, jlong context_pointer,
                    jobject jmessages) {
    const auto context = reinterpret_cast<llama_context *>(context_pointer);

    std::vector<llama_chat_message> messages;
    std::vector<char> formatted(llama_n_ctx(context));

    jclass cls_List = env->GetObjectClass(jmessages);


    jmethodID mid_iterator = env->GetMethodID(cls_List, "iterator", "()Ljava/util/Iterator;");

    jobject iterator = env->CallObjectMethod(jmessages, mid_iterator);
    jclass cls_Iterator = env->GetObjectClass(iterator);

    // 'hasNext' and 'next'
    jmethodID mid_hasNext = env->GetMethodID(cls_Iterator, "hasNext", "()Z");
    jmethodID mid_next = env->GetMethodID(cls_Iterator, "next", "()Ljava/lang/Object;");

    jclass cls_message = env->FindClass("android/llama/cpp/Message");
    jclass cls_role = env->FindClass("android/llama/cpp/Role");
    jfieldID fid_content = env->GetFieldID(cls_message, "content", "Ljava/lang/String;");
    jfieldID fid_role = env->GetFieldID(cls_message, "role", "Landroid/llama/cpp/Role;");
    jmethodID mid_ordinal = env->GetMethodID(cls_role, "ordinal", "()I");

    int new_len = 0;

    while (env->CallBooleanMethod(iterator, mid_hasNext)) {
        jobject item = env->CallObjectMethod(iterator, mid_next);
        auto s = (jstring) (env->GetObjectField(item, fid_content));
        const char *c = env->GetStringUTFChars(s, nullptr);
        jobject role = env->GetObjectField(item, fid_role);
        int roleType = env->CallIntMethod(role, mid_ordinal);
        switch (roleType) {
            case 0 :
                messages.push_back({"system", strdup(c)});
                break;
            case 1:
                messages.push_back({"user", strdup(c)});
                break;
            case 2:
                messages.push_back({"assistant", strdup(c)});
                break;
            default:
                messages.push_back({"user", strdup(c)});
                break;
        }
        // 释放资源
        env->ReleaseStringUTFChars(s, c);
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(item);

        new_len = llama_chat_apply_template(llama_get_model(context), nullptr, messages.data(),
                                            messages.size(), true, formatted.data(),
                                            formatted.size());
        if (new_len > (int) formatted.size()) {
            formatted.resize(new_len);
            new_len = llama_chat_apply_template(llama_get_model(context), nullptr, messages.data(),
                                                messages.size(), true, formatted.data(),
                                                formatted.size());
        }
        if (new_len < 0) {
            LOGe("failed to apply the chat template\n");
            return nullptr;
        }


    }



    LOGe("Content is %s", formatted.data());


    // 释放资源
    env->DeleteLocalRef(cls_List);
    env->DeleteLocalRef(iterator);
    env->DeleteLocalRef(cls_Iterator);
    env->DeleteLocalRef(cls_message);
    env->DeleteLocalRef(cls_role);

    return env->NewStringUTF(formatted.data());
}


void kvCacheClear(JNIEnv *, jobject, jlong context) {
    llama_kv_cache_clear(reinterpret_cast<llama_context *>(context));
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    jint ret = vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        LOGe("jni_replace JVM ERROR:GetEnv");
        return -1;
    }

    llama_log_set(log_callback, nullptr);

    JNINativeMethod g_Methods[] = {
            {"logToAndroid",   "()V",                                                             (void *) logToAndroid},
            {"loadModel",      "(Ljava/lang/String;)J",                                           (void *) loadModel},
            {"freeModel",      "(J)V",                                                            (void *) freeModel},
            {"newContext",     "(J)J",                                                            (void *) newContext},
            {"freeContext",    "(J)V",                                                            (void *) freeContext},
            {"backendInit",    "(Z)V",                                                            (void *) backendInit},
            {"backendFree",    "()V",                                                             (void *) backendFree},
            {"newBatch",       "(III)J",                                                          (void *) newBatch},
            {"freeBatch",      "(J)V",                                                            (void *) freeBatch},
            {"newSampler",     "()J",                                                             (void *) newSampler},
            {"freeSampler",    "(J)V",                                                            (void *) freeSampler},

            {"benchModel",     "(JJJIIII)Ljava/lang/String;",                                     (void *) benchModel},
            {"systemInfo",     "()Ljava/lang/String;",                                            (void *) systemInfo},
            {"completionInit", "(JJLjava/lang/String;I)I",                                        (void *) completionInit},
            {"completionLoop", "(JJJILandroid/llama/cpp/LLamaAndroid$IntVar;)Ljava/lang/String;", (void *) completionLoop},
            {"kvCacheClear",   "(J)V",                                                            (void *) kvCacheClear},
            {"applyTemple",    "(JLjava/util/List;)Ljava/lang/String;",                           (void *) applyTemple},


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

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {

}

