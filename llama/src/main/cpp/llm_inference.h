//
// Created by ag2s on 2025/1/3.
//

#ifndef LLAMACPPANDROIDTEST_LLM_INFERENCE_H
#define LLAMACPPANDROIDTEST_LLM_INFERENCE_H

#include <llama.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sys/sysconf.h>
#define TAG "llama-android.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define EOG "[EOG]"

 static void log_callback(ggml_log_level level, const char *fmt, void *data) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_INFO) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_DEBUG) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, data);
    else __android_log_print(ANDROID_LOG_DEFAULT, TAG, fmt, data);
}


class LLMInference {
    llama_context *ctx;
    llama_model *model;
    const llama_vocab *vocab;
    std::string tmpl;
    llama_sampler *sampler;
    llama_batch batch;
    std::string response;
    std::vector<llama_chat_message> messages;
    llama_token curr_token;
    std::string cache_response_tokens;

    std::vector<char> formatted;
    int prev_len = 0;
    bool store_chats;
    bool forceStop= false;

    int64_t response_generation_time = 0;
    long response_num_tokens = 0;

    static bool is_valid_utf8(const char *response);

public:

    void load_model(const char *model_path, float min_p, float temperature, bool store);
    void set_chat_temple(const char *temple);

    void add_chat_message(const char *message, const char *role);
    void clean_message();

    float get_response_generation_time();

    void start_completion(const char *query);

    std::string completion_loop();

    std::string bench(int pp,int tg,int pl,int nr);

    void stop_completion();

    ~LLMInference();


};


#endif //LLAMACPPANDROIDTEST_LLM_INFERENCE_H
