#include <jni.h>
#include <android/log.h>
#include "whisper.h"
#include <algorithm>
#include <string>

#define TAG "HumaiWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_core_ai_WhisperAudioEngine_nativeTranscribe(
    JNIEnv * env, jobject, jstring model_path, jfloatArray pcm, jstring language, jint threads
) {
    if (model_path == nullptr || pcm == nullptr) return nullptr;
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return nullptr;
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(model_path, path);
    if (ctx == nullptr) return nullptr;

    const jsize count_samples = env->GetArrayLength(pcm);
    if (count_samples <= 0) {
        whisper_free(ctx);
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }
    jfloat * samples = env->GetFloatArrayElements(pcm, nullptr);
    if (samples == nullptr) {
        whisper_free(ctx);
        return nullptr;
    }

    std::string language_code = "auto";
    if (language != nullptr) {
        const char * value = env->GetStringUTFChars(language, nullptr);
        if (value != nullptr) {
            if (value[0] != 0) language_code = value;
            env->ReleaseStringUTFChars(language, value);
        }
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = language_code.c_str();
    params.n_threads = std::max(1, std::min(static_cast<int>(threads), 6));
    params.no_context = true;
    params.single_segment = false;
    params.suppress_blank = true;

    const int result = whisper_full(ctx, params, samples, count_samples);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        whisper_free(ctx);
        return nullptr;
    }

    const int segment_count = whisper_full_n_segments(ctx);
    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray output = env->NewObjectArray(segment_count, string_class, nullptr);
    for (int i = 0; i < segment_count; ++i) {
        const int64_t start_ms = whisper_full_get_segment_t0(ctx, i) * 10;
        const int64_t end_ms = whisper_full_get_segment_t1(ctx, i) * 10;
        const char * segment_text = whisper_full_get_segment_text(ctx, i);
        const std::string row = std::to_string(start_ms) + "\t" +
            std::to_string(end_ms) + "\t" + (segment_text == nullptr ? "" : segment_text);
        jstring value = env->NewStringUTF(row.c_str());
        env->SetObjectArrayElement(output, i, value);
        env->DeleteLocalRef(value);
    }
    whisper_free(ctx);
    return output;
}
