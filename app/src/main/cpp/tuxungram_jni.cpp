// Minimal JNI bridge to libtdjson's C interface (td_json_client).
// The four functions below are TDLib's stable C ABI; they are declared
// here directly so the app does not need TDLib's headers at build time.
//
// All payloads cross the JNI boundary as byte arrays, NOT jstring:
// TDLib speaks standard UTF-8 JSON, while JNI's string functions use
// modified UTF-8, which corrupts 4-byte sequences (most emoji) and
// can mangle CJK text. Kotlin encodes/decodes UTF-8 on its side.
#include <jni.h>
#include <cstring>

extern "C" {
int td_create_client_id();
void td_send(int client_id, const char *request);
const char *td_receive(double timeout);
const char *td_execute(const char *request);
}

static jbyteArray to_jbytes(JNIEnv *env, const char *s) {
    if (s == nullptr) {
        return nullptr;
    }
    const auto len = static_cast<jsize>(std::strlen(s));
    jbyteArray out = env->NewByteArray(len);
    if (out != nullptr && len > 0) {
        env->SetByteArrayRegion(out, 0, len, reinterpret_cast<const jbyte *>(s));
    }
    return out;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tuxungram_android_td_TdNative_createClientId(JNIEnv *env, jobject thiz) {
    return td_create_client_id();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tuxungram_android_td_TdNative_send(JNIEnv *env, jobject thiz, jint client_id, jbyteArray request) {
    const jsize len = env->GetArrayLength(request);
    // td_send copies the request before returning, so a stack buffer
    // freed on scope exit is fine; requests are small JSON documents.
    char *buf = new char[len + 1];
    env->GetByteArrayRegion(request, 0, len, reinterpret_cast<jbyte *>(buf));
    buf[len] = '\0';
    td_send(client_id, buf);
    delete[] buf;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_tuxungram_android_td_TdNative_receive(JNIEnv *env, jobject thiz, jdouble timeout) {
    return to_jbytes(env, td_receive(timeout));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_tuxungram_android_td_TdNative_execute(JNIEnv *env, jobject thiz, jbyteArray request) {
    const jsize len = env->GetArrayLength(request);
    char *buf = new char[len + 1];
    env->GetByteArrayRegion(request, 0, len, reinterpret_cast<jbyte *>(buf));
    buf[len] = '\0';
    const char *res = td_execute(buf);
    delete[] buf;
    return to_jbytes(env, res);
}
