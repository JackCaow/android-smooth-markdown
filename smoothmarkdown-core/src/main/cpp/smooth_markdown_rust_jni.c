#include <jni.h>
#include <stdint.h>
#include <limits.h>
#include "smooth_markdown_rust.h"

JNIEXPORT jint JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_abiVersionNative(JNIEnv *env, jobject self) {
    (void)env; (void)self;
    return (jint)smr_abi_version();
}

JNIEXPORT jbyteArray JNICALL Java_com_jackcaow_smoothmarkdown_nativeparser_RustMarkdownBridge_parseNative(JNIEnv *env, jobject self, jstring source, jint options) {
    (void)self;
    if (source == NULL) return NULL;
    jsize length = (*env)->GetStringLength(env, source);
    const jchar *units = (*env)->GetStringChars(env, source, NULL);
    if (units == NULL) return NULL;
    SmrBuffer output = {0};
    int32_t status = smr_parse_utf16((const uint16_t *)units, (size_t)length, (uint32_t)options, &output);
    (*env)->ReleaseStringChars(env, source, units);
    jbyteArray result = NULL;
    if (status == 0 && output.data != NULL && output.len <= INT_MAX) {
        result = (*env)->NewByteArray(env, (jsize)output.len);
        if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, (jsize)output.len, (const jbyte *)output.data);
    }
    smr_buffer_free(&output);
    return result;
}
