#include <jni.h>
#include <android/log.h>
#include "llama.h"

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <new>
#include <string>
#include <vector>

#define TAG "HumaiLlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
std::once_flag g_backend_init_once;

struct HumaiSession {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    llama_batch batch{};
    const llama_vocab * vocab = nullptr;
    int32_t position = 0;
    std::string pending_utf8;
    std::mutex mutex;
};

HumaiSession * session_from(jlong handle) {
    return reinterpret_cast<HumaiSession *>(static_cast<intptr_t>(handle));
}

std::string java_to_utf8(JNIEnv * env, jstring input) {
    if (input == nullptr) return {};
    const jsize length = env->GetStringLength(input);
    const jchar * chars = env->GetStringChars(input, nullptr);
    if (chars == nullptr) return {};
    std::string output;
    output.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            }
        }
        if (cp <= 0x7F) {
            output.push_back(static_cast<char>(cp));
        } else if (cp <= 0x7FF) {
            output.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            output.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            output.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(input, chars);
    return output;
}

jstring utf8_to_java(JNIEnv * env, const std::string & input) {
    std::vector<jchar> out;
    out.reserve(input.size());
    for (size_t i = 0; i < input.size();) {
        const uint8_t b = static_cast<uint8_t>(input[i]);
        uint32_t cp = 0xFFFD;
        size_t width = 1;
        if (b < 0x80) cp = b;
        else if ((b & 0xE0) == 0xC0 && i + 1 < input.size()) {
            cp = ((b & 0x1F) << 6) | (static_cast<uint8_t>(input[i + 1]) & 0x3F); width = 2;
        } else if ((b & 0xF0) == 0xE0 && i + 2 < input.size()) {
            cp = ((b & 0x0F) << 12) | ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 6) |
                 (static_cast<uint8_t>(input[i + 2]) & 0x3F); width = 3;
        } else if ((b & 0xF8) == 0xF0 && i + 3 < input.size()) {
            cp = ((b & 0x07) << 18) | ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 12) |
                 ((static_cast<uint8_t>(input[i + 2]) & 0x3F) << 6) |
                 (static_cast<uint8_t>(input[i + 3]) & 0x3F); width = 4;
        }
        if (cp <= 0xFFFF) out.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            out.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
        i += width;
    }
    return env->NewString(out.data(), static_cast<jsize>(out.size()));
}

size_t complete_utf8_prefix(const std::string & value) {
    size_t i = 0;
    while (i < value.size()) {
        const uint8_t b = static_cast<uint8_t>(value[i]);
        size_t width = b < 0x80 ? 1 : ((b & 0xE0) == 0xC0 ? 2 : ((b & 0xF0) == 0xE0 ? 3 : ((b & 0xF8) == 0xF0 ? 4 : 0)));
        if (width == 0) return i;
        if (i + width > value.size()) return i;
        for (size_t j = 1; j < width; ++j) {
            if ((static_cast<uint8_t>(value[i + j]) & 0xC0) != 0x80) return i;
        }
        i += width;
    }
    return i;
}

void free_session(HumaiSession * session) {
    if (!session) return;
    if (session->sampler) llama_sampler_free(session->sampler);
    if (session->batch.token) llama_batch_free(session->batch);
    if (session->context) llama_free(session->context);
    if (session->model) llama_model_free(session->model);
    delete session;
}
} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_core_ai_LlamaBridge_nativeLoadModel(
    JNIEnv * env, jobject, jstring model_path, jint, jint ctx_size, jint threads
) {
    if (!model_path) return 0;
    const char * path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (!path_chars) return 0;
    const std::string path(path_chars);
    env->ReleaseStringUTFChars(model_path, path_chars);

    std::call_once(g_backend_init_once, [] { llama_backend_init(); });

    auto * s = new (std::nothrow) HumaiSession();
    if (!s) return 0;

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU-only build; never claim Adreno offload here.
    s->model = llama_model_load_from_file(path.c_str(), mp);
    if (!s->model) {
        LOGE("Unable to load GGUF model: %s", path.c_str());
        free_session(s);
        return 0;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(std::max(512, std::min(ctx_size, 8192)));
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = std::max(1, std::min(threads, 8));
    cp.n_threads_batch = cp.n_threads;
    s->context = llama_init_from_model(s->model, cp);
    if (!s->context) {
        LOGE("Unable to create llama_context");
        free_session(s);
        return 0;
    }

    s->vocab = llama_model_get_vocab(s->model);
    if (!s->vocab) {
        free_session(s);
        return 0;
    }

    s->batch = llama_batch_init(1, 0, 1);
    if (!s->batch.token || !s->batch.seq_id) {
        LOGE("Unable to allocate llama batch");
        free_session(s);
        return 0;
    }

    LOGI("Loaded model and context successfully; ctx=%u", llama_n_ctx(s->context));
    return static_cast<jlong>(reinterpret_cast<intptr_t>(s));
}

JNIEXPORT void JNICALL
Java_com_example_core_ai_LlamaBridge_nativeUnloadModel(JNIEnv *, jobject, jlong model_ptr, jlong) {
    // The Kotlin layer serializes model lifecycle operations. Do not lock and
    // then destroy the mutex that the lock guard would subsequently unlock.
    free_session(session_from(model_ptr));
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeResetContext(JNIEnv *, jobject, jlong context_ptr) {
    auto * s = session_from(context_ptr);
    if (!s || !s->context) return -1;
    std::lock_guard<std::mutex> lock(s->mutex);
    llama_memory_clear(llama_get_memory(s->context), true);
    s->position = 0;
    s->pending_utf8.clear();
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeSetSampling(
    JNIEnv *, jobject, jlong context_ptr, jfloat temperature, jfloat top_p
) {
    auto * s = session_from(context_ptr);
    if (!s || !s->context) return -1;
    std::lock_guard<std::mutex> lock(s->mutex);
    if (s->sampler) {
        llama_sampler_free(s->sampler);
        s->sampler = nullptr;
    }

    s->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!s->sampler) return -1;
    if (temperature <= 0.001f) {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(std::max(0.05f, std::min(top_p, 1.0f)), 1));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(std::max(0.05f, std::min(temperature, 2.0f))));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeEval(JNIEnv *, jobject, jlong context_ptr, jint token) {
    auto * s = session_from(context_ptr);
    if (!s || !s->context || !s->batch.token) return -1;
    std::lock_guard<std::mutex> lock(s->mutex);
    if (s->position >= static_cast<int32_t>(llama_n_ctx(s->context))) return -2;

    s->batch.n_tokens = 1;
    s->batch.token[0] = token;
    s->batch.pos[0] = s->position;
    s->batch.n_seq_id[0] = 1;
    s->batch.seq_id[0][0] = 0;
    s->batch.logits[0] = 1;
    const int result = llama_decode(s->context, s->batch);
    if (result == 0) ++s->position;
    else LOGE("llama_decode returned %d", result);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeSample(JNIEnv *, jobject, jlong context_ptr, jfloat, jfloat) {
    auto * s = session_from(context_ptr);
    if (!s || !s->context || !s->sampler) return -1;
    std::lock_guard<std::mutex> lock(s->mutex);
    const llama_token token = llama_sampler_sample(s->sampler, s->context, -1);
    return llama_vocab_is_eog(s->vocab, token) ? -1 : static_cast<jint>(token);
}

JNIEXPORT jstring JNICALL
Java_com_example_core_ai_LlamaBridge_nativeTokenToPiece(JNIEnv * env, jobject, jlong model_ptr, jint token) {
    auto * s = session_from(model_ptr);
    if (!s || !s->vocab) return env->NewStringUTF("");
    std::lock_guard<std::mutex> lock(s->mutex);

    std::vector<char> buffer(128);
    int32_t count = llama_token_to_piece(s->vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(s->vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    }
    if (count < 0) return env->NewStringUTF("");

    s->pending_utf8.append(buffer.data(), static_cast<size_t>(count));
    const size_t complete = complete_utf8_prefix(s->pending_utf8);
    if (complete == 0) return env->NewStringUTF("");
    const std::string ready = s->pending_utf8.substr(0, complete);
    s->pending_utf8.erase(0, complete);
    return utf8_to_java(env, ready);
}

JNIEXPORT jintArray JNICALL
Java_com_example_core_ai_LlamaBridge_nativeTokenize(JNIEnv * env, jobject, jlong model_ptr, jstring text) {
    auto * s = session_from(model_ptr);
    if (!s || !s->vocab || !text) return env->NewIntArray(0);
    const std::string prompt = java_to_utf8(env, text);
    if (prompt.empty() || prompt.size() > 1024 * 1024) return env->NewIntArray(0);

    const int32_t required = llama_tokenize(
        s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (required >= 0 || required == INT32_MIN || -required > 32768) return env->NewIntArray(0);

    std::vector<llama_token> tokens(static_cast<size_t>(-required));
    const int32_t written = llama_tokenize(
        s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(),
        static_cast<int32_t>(tokens.size()), true, true);
    if (written < 0) return env->NewIntArray(0);

    jintArray out = env->NewIntArray(written);
    if (out && written > 0) {
        std::vector<jint> converted(tokens.begin(), tokens.begin() + written);
        env->SetIntArrayRegion(out, 0, written, converted.data());
    }
    return out;
}

} // extern "C"
