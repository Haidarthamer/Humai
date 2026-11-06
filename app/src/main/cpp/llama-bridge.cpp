#include <jni.h>
#include <android/log.h>

#define TAG "LlamaBridgeNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/*
 * Explicitly non-functional placeholder.
 *
 * This translation unit is not a llama.cpp implementation. Returning a fake
 * pointer or synthetic tokens made the Kotlin layer report successful loads
 * and generate meaningless output. Until the real upstream llama.cpp sources
 * and a proper model/context lifecycle are linked by CMake, fail closed.
 */

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_core_ai_LlamaBridge_nativeLoadModel(
    JNIEnv*,
    jobject,
    jstring,
    jint,
    jint,
    jint
) {
    LOGE("Native llama.cpp backend is not linked; refusing to create a fake model handle.");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_core_ai_LlamaBridge_nativeUnloadModel(
    JNIEnv*,
    jobject,
    jlong,
    jlong
) {
    // No native model/context exists in this placeholder translation unit.
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeEval(
    JNIEnv*,
    jobject,
    jlong,
    jint
) {
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_example_core_ai_LlamaBridge_nativeSample(
    JNIEnv*,
    jobject,
    jlong,
    jfloat,
    jfloat
) {
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_example_core_ai_LlamaBridge_nativeTokenToPiece(
    JNIEnv* env,
    jobject,
    jlong,
    jint
) {
    return env->NewStringUTF("");
}

JNIEXPORT jintArray JNICALL
Java_com_example_core_ai_LlamaBridge_nativeTokenize(
    JNIEnv* env,
    jobject,
    jlong,
    jstring
) {
    return env->NewIntArray(0);
}

} // extern "C"
