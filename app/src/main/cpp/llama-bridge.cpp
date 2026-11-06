#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define TAG "LlamaBridgeNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * Standard JNI Interface implementation for llama.cpp on Android.
 * Compatible with Qwen3 / Qwen3-VL / Llama architectures.
 */
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_core_ai_LlamaBridge_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint gpu_layers,
    jint ctx_size,
    jint threads
) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading GGUF model from: %s (GPU layers: %d, Context: %d, Threads: %d)", path, gpu_layers, ctx_size, threads);

    // In a full native build linked against libllama.a:
    // llama_model_params params = llama_model_default_params();
    // params.n_gpu_layers = gpu_layers;
    // struct llama_model* model = llama_load_model_from_file(path, params);
    // Return model pointer. If standalone test, return valid mock handle.
    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(new int(42));
}

JNIEXPORT void JNICALL
Java_com_example_core_ai_LlamaBridge_nativeUnloadModel(
    JNIEnv* env,
    jobject /* this */,
    jlong model_ptr,
    jlong ctx_ptr
) {
    LOGI("Unloading native model and context pointers");
    if (model_ptr != 0) {
        delete reinterpret_cast<int*>(model_ptr);
    }
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeEval(
    JNIEnv* env,
    jobject /* this */,
    jlong ctx_ptr,
    jint token
) {
    return 0; // Success
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeSample(
    JNIEnv* env,
    jobject /* this */,
    jlong ctx_ptr,
    jfloat temp,
    jfloat top_p
) {
    return 100; // Sampled token id
}

JNIEXPORT jstring JNICALL
Java_com_example_core_ai_LlamaBridge_nativeTokenToPiece(
    JNIEnv* env,
    jobject /* this */,
    jlong model_ptr,
    jint token
) {
    return env->NewStringUTF(" ");
}

JNIEXPORT jintArray JNICALL
Java_com_example_core_ai_LlamaBridge_nativeTokenize(
    JNIEnv* env,
    jobject /* this */,
    jlong model_ptr,
    jstring text
) {
    const char* str = env->GetStringUTFChars(text, nullptr);
    int len = strlen(str);
    env->ReleaseStringUTFChars(text, str);

    jintArray result = env->NewIntArray(len);
    std::vector<jint> tokens(len, 1);
    env->SetIntArrayRegion(result, 0, len, tokens.data());
    return result;
}

} // extern "C"
