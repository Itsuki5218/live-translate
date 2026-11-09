#include <jni.h>
#include <whisper.h>

#include <string>
#include <vector>

static std::string jstr(JNIEnv *env, jstring s) {
    if (s == nullptr) return "";
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

static void trim(std::string &s) {
    const char *ws = " \t\r\n";
    size_t b = s.find_first_not_of(ws);
    if (b == std::string::npos) {
        s.clear();
        return;
    }
    size_t e = s.find_last_not_of(ws);
    s = s.substr(b, e - b + 1);
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_livetranslate_Whisper_nativeInit(JNIEnv *env, jclass, jstring path) {
    std::string p = jstr(env, path);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p.c_str(), cp);
    return (jlong) (intptr_t) ctx;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_livetranslate_Whisper_nativeTranscribe(JNIEnv *env, jclass, jlong handle,
                                                      jshortArray arr, jint len, jstring lang) {
    auto *ctx = (whisper_context *) (intptr_t) handle;
    if (ctx == nullptr || len <= 0) return env->NewStringUTF("");

    std::vector<float> pcm((size_t) len);
    jshort *buf = env->GetShortArrayElements(arr, nullptr);
    if (buf == nullptr) return env->NewStringUTF("");
    for (int i = 0; i < len; i++) pcm[i] = (float) buf[i] / 32768.0f;
    env->ReleaseShortArrayElements(arr, buf, JNI_ABORT);

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    std::string language = jstr(env, lang);
    wp.language = language.empty() ? "auto" : language.c_str();
    wp.translate = false;
    wp.n_threads = 4;
    wp.print_realtime = false;
    wp.print_progress = false;
    wp.print_timestamps = false;
    wp.print_special = false;
    wp.no_timestamps = true;
    wp.suppress_blank = true;
    wp.no_context = true;
    // 短片段容易陷入复读，抬高熵阈值触发温度回退
    wp.temperature_inc = 0.2f;
    wp.entropy_thold = 2.4f;
    wp.logprob_thold = -1.0f;
    wp.no_speech_thold = 0.6f;
    wp.single_segment = false;

    if (whisper_full(ctx, wp, pcm.data(), (int) pcm.size()) != 0) {
        return env->NewStringUTF("");
    }

    std::string out;
    const int n = whisper_full_n_segments(ctx);
    for (int i = 0; i < n; i++) {
        const char *t = whisper_full_get_segment_text(ctx, i);
        if (t) out += t;
    }
    trim(out);

    // Whisper 对静音/音乐常吐出这些标记，直接丢掉
    if (out == "[BLANK_AUDIO]" || out == "(silence)" || out == "[Music]" || out == "(音乐)") {
        out.clear();
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_livetranslate_Whisper_nativeFree(JNIEnv *, jclass, jlong handle) {
    auto *ctx = (whisper_context *) (intptr_t) handle;
    if (ctx != nullptr) whisper_free(ctx);
}
