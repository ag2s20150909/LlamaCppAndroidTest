//
// Created by ag2s on 2025/1/3.
//

#include "llm_inference.h"
#include <common.h>
#include <iostream>


void
LLMInference::load_model(const char *model_path, float min_p, float temperature, bool store) {
    // create an instance of llama_model
    //llama_log_set(log_callback, nullptr);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers=999;
    model_params.use_mmap= true;
    model = llama_model_load_from_file(model_path, model_params);

    if (!model) {
        LOGe("failed to load model from %s", model_path);
        throw std::runtime_error("load_model() failed");
    }

    vocab = llama_model_get_vocab(model);
    tmpl = llama_model_chat_template(model, nullptr);

    LOGe("chat template is %s",tmpl.c_str());

    int n_threads = std::max(1, std::min(8, (int) sysconf(_SC_NPROCESSORS_ONLN) - 2));
    // create an instance of llama_context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 0;            // take context size from the model GGUF file
    ctx_params.no_perf = true;          // disable performance metrics
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    ctx_params.flash_attn = true;
    ctx_params.type_k = GGML_TYPE_Q8_0;
    ctx_params.type_v = GGML_TYPE_Q4_0;
    forceStop = false;

    ctx = llama_init_from_model(model,
                                ctx_params);//llama_new_context_with_model(model, ctx_params);

    if (!ctx) {
        LOGe("llama_new_context_with_model() returned null)");
        throw std::runtime_error("llama_new_context_with_model() returned null");
    }

    // initialize sampler
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;      // disable performance metrics
    sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(min_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
//    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, 1.25, 0.1, 0.1));
//    llama_sampler_chain_add(sampler, llama_sampler_init_mirostat_v2(LLAMA_DEFAULT_SEED, 5.0, 0.1));

    formatted = std::vector<char>(llama_n_ctx(ctx));
    messages.clear();
    this->store_chats = store;
}

void LLMInference::set_chat_temple(const char *temple) {
    tmpl=temple;
}


void LLMInference::add_chat_message(const char *message, const char *role) {
    messages.push_back({strdup(role), strdup(message)});
}

float LLMInference::get_response_generation_time() {
    return (float) (response_num_tokens / (response_generation_time / 1e6));
}

void LLMInference::start_completion(const char *query) {
    if (!store_chats) {
        prev_len = 0;
        formatted.clear();
    }
    this->forceStop = false;
    response_generation_time = 0;
    response_num_tokens = 0;

    add_chat_message(query, "user");
    //const char *tmpl = llama_model_chat_template(model);


    int new_len = llama_chat_apply_template(
            tmpl.c_str(),
            messages.data(),
            messages.size(),
            true,
            formatted.data(),
            (int32_t) formatted.size()
    );
    if (new_len > (int) formatted.size()) {
        formatted.resize(new_len);
        new_len = llama_chat_apply_template(tmpl.c_str(), messages.data(), messages.size(), true,
                                            formatted.data(), (int32_t) formatted.size());
    }
    if (new_len < 0) {
        throw std::runtime_error(
                "llama_chat_apply_template() in LLMInference::start_completion() failed");
    }
    LOGe("messages.size() %zu %d",messages.size(),prev_len);
    std::string prompt(formatted.begin() + prev_len, formatted.begin() + new_len);
    LOGe("messages.size() %s",prompt.c_str());
    std::vector<llama_token> prompt_tokens = common_tokenize(vocab, prompt,llama_get_kv_cache_used_cells(ctx)==0, true);

    // create a llama_batch containing a single sequence
    // see llama_batch_init for more details
    batch = llama_batch_get_one(prompt_tokens.data(), (int32_t) prompt_tokens.size());
}

// taken from:
// https://github.com/ggerganov/llama.cpp/blob/master/examples/llama.android/llama/src/main/cpp/llama-android.cpp#L38
bool LLMInference::is_valid_utf8(const char *response) {
    if (!response) {
        return true;
    }
    const auto *bytes = (const unsigned char *) response;
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


std::string LLMInference::completion_loop() {
    // check if the length of the inputs to the model
    // have exceeded the context size of the model
    if (forceStop) {
        return EOG;
    }

    uint32_t context_size = llama_n_ctx(ctx);
    uint32_t n_ctx_used = llama_get_kv_cache_used_cells(ctx);
    if (n_ctx_used + batch.n_tokens > context_size) {
        std::cerr << "context size exceeded" << '\n';
        exit(0);
    }

    auto start = ggml_time_us();
    // run the model
    if (llama_decode(ctx, batch) < 0) {
        throw std::runtime_error("llama_decode() failed");
    }

    // sample a token and check if it is an EOG (end of generation token)
    // convert the integer token to its correspond word-piece
    curr_token = llama_sampler_sample(sampler, ctx, -1);
    //LOGe("curr_token %d",curr_token);
    if (llama_vocab_is_eog(vocab, curr_token)) {
        return EOG;
    }
    std::string piece = common_token_to_piece(vocab, curr_token, true);
    //LOGe("piece %s",piece.c_str());
    auto end = ggml_time_us();
    response_generation_time += (end - start);
    response_num_tokens += 1;
    cache_response_tokens += piece;

    // re-init the batch with the newly predicted token
    // key, value pairs of all previous tokens have been cached
    // in the KV cache
    batch = llama_batch_get_one(&curr_token, 1);

    if (is_valid_utf8(cache_response_tokens.c_str())) {
        response += cache_response_tokens;
        std::string valid_utf8_piece = cache_response_tokens;
        cache_response_tokens.clear();
        return valid_utf8_piece;
    }

    return "";
}

std::string LLMInference::bench(int pp,int tg,int pl,int nr){
    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;
    batch = llama_batch_init(512, 0,1);

    const int n_ctx = llama_n_ctx(ctx);
    LOGi("n_ctx = %d", n_ctx);
    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp)");

        common_batch_clear(batch);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            common_batch_add(batch, 0, i, { 0 }, false);
        }

        batch.logits[batch.n_tokens - 1] = true;
        llama_kv_cache_clear(ctx);

        const auto t_pp_start = ggml_time_us();
        if (llama_decode(ctx, batch) != 0) {
            LOGi("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg)");

        llama_kv_cache_clear(ctx);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {

            common_batch_clear(batch);
            for (j = 0; j < pl; j++) {
                common_batch_add(batch, 0, i, { j }, true);
            }

            LOGi("llama_decode() text generation: %d", i);
            if (llama_decode(ctx, batch) != 0) {
                LOGi("llama_decode() failed during text generation");
            }
        }

        const auto t_tg_end = ggml_time_us();

        llama_kv_cache_clear(ctx);

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

    const auto model_size     = double(llama_model_size(model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(model)) / 1e9;

    const auto backend    = "(Android)"; // TODO: What should this be?

    std::stringstream result;
    //result << std::setprecision(2);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | " << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | " << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";

    return result.str();




}


void LLMInference::stop_completion() {
    if (store_chats) {
        add_chat_message(strdup(response.c_str()), "assistant");
    }
    response.clear();


    prev_len = llama_chat_apply_template(
            tmpl.c_str(),
            messages.data(),
            messages.size(),
            false,
            nullptr,
            0
    );
    forceStop = true;
    if (prev_len < 0) {
        throw std::runtime_error(
                "llama_chat_apply_template() in LLMInference::stop_completion() failed");
    }
}

void LLMInference::clean_message() {
    prev_len = 0;
    common_batch_clear(batch);
    formatted.clear();
    //formatted = std::vector<char>(llama_n_ctx(ctx));
    messages.clear();
    llama_kv_cache_clear(ctx);
}

LLMInference::~LLMInference() {
    // free memory held by the message text in messages
    // (as we had used strdup() to create a malloc'ed copy)
    for (llama_chat_message &message: messages) {
        delete message.content;
    }
    //delete tmpl;
    llama_kv_cache_clear(ctx);
    llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);




}




