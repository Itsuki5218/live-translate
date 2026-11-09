#include <jni.h>
#include <llama.h>

#include <string>
#include <vector>

static std::string jstr(JNIEnv *env, jstring s) {
    if (s == nullptr) return "";
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_livetranslate_Llama_nativeLoad(JNIEnv *env, jclass, jstring path, jint nctx) {
    std::string p = jstr(env, path);
    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(p.c_str(), mp);
    if (model == nullptr) return nullptr;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nctx;
    cp.n_batch = 512;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        llama_model_free(model);
        return nullptr;
    }

    jlong out[2] = {(jlong) (intptr_t) model, (jlong) (intptr_t) ctx};
    jlongArray arr = env->NewLongArray(2);
    env->SetLongArrayRegion(arr, 0, 2, out);
    return arr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_livetranslate_Llama_nativeChat(JNIEnv *env, jclass, jlong ctxHandle,
                                              jstring jSys, jstring jUser, jint maxTokens) {
    auto *ctx = (llama_context *) (intptr_t) ctxHandle;
    if (ctx == nullptr) return env->NewStringUTF("");

    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    std::string sys = jstr(env, jSys);
    std::string user = jstr(env, jUser);

    llama_chat_message msgs[2];
    msgs[0].role = "system";
    msgs[0].content = sys.c_str();
    msgs[1].role = "user";
    msgs[1].content = user.c_str();

    std::vector<char> buf(8192);
    const char *tmpl = llama_model_chat_template(model, nullptr);
    int32_t n = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), (int32_t) buf.size());
    if (n > (int32_t) buf.size()) {
        buf.resize((size_t) n + 1);
        n = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), (int32_t) buf.size());
    }
    if (n <= 0) return env->NewStringUTF("");
    std::string prompt(buf.data(), (size_t) n);

    std::vector<llama_token> toks(prompt.size() + 16);
    int32_t ntok = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(),
                                  toks.data(), (int32_t) toks.size(), true, true);
    if (ntok < 0) {
        toks.resize((size_t) -ntok);
        ntok = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(),
                              toks.data(), (int32_t) toks.size(), true, true);
    }
    if (ntok <= 0) return env->NewStringUTF("");
    toks.resize((size_t) ntok);

    llama_memory_clear(llama_get_memory(ctx), true);

    llama_batch batch = llama_batch_get_one(toks.data(), ntok);
    if (llama_decode(ctx, batch) != 0) return env->NewStringUTF("");

    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string out;
    for (int i = 0; i < maxTokens; i++) {
        llama_token id = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;
        llama_sampler_accept(smpl, id);

        char piece[256];
        int np = llama_token_to_piece(vocab, id, piece, (int32_t) sizeof(piece), 0, false);
        if (np > 0) out.append(piece, (size_t) np);

        llama_batch step = llama_batch_get_one(&id, 1);
        if (llama_decode(ctx, step) != 0) break;
    }
    llama_sampler_free(smpl);

    // 去掉首尾空白和包裹的引号
    size_t b = out.find_first_not_of(" \t\r\n\"“”");
    if (b == std::string::npos) {
        out.clear();
    } else {
        size_t e = out.find_last_not_of(" \t\r\n\"“”");
        out = out.substr(b, e - b + 1);
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_livetranslate_Llama_nativeFree(JNIEnv *, jclass, jlong modelHandle, jlong ctxHandle) {
    auto *ctx = (llama_context *) (intptr_t) ctxHandle;
    auto *model = (llama_model *) (intptr_t) modelHandle;
    if (ctx != nullptr) llama_free(ctx);
    if (model != nullptr) llama_model_free(model);
}
