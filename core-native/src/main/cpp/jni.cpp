#include "engine.hpp"
#include <jni.h>
#include <new>
using omega::Engine;
static Engine* ptr(jlong h){return reinterpret_cast<Engine*>(static_cast<intptr_t>(h));}
extern "C" {
JNIEXPORT jlong JNICALL Java_com_omega_core_NativeEngine_create(JNIEnv*,jobject){try{return static_cast<jlong>(reinterpret_cast<intptr_t>(new Engine()));}catch(...){return 0;}}
JNIEXPORT void JNICALL Java_com_omega_core_NativeEngine_destroy(JNIEnv*,jobject,jlong h){delete ptr(h);}
JNIEXPORT jboolean JNICALL Java_com_omega_core_NativeEngine_startNative(JNIEnv*,jobject,jlong h){return ptr(h)&&ptr(h)->start();}
JNIEXPORT jboolean JNICALL Java_com_omega_core_NativeEngine_observeNative(JNIEnv*,jobject,jlong h){return ptr(h)&&ptr(h)->observe();}
JNIEXPORT void JNICALL Java_com_omega_core_NativeEngine_stopNative(JNIEnv*,jobject,jlong h){if(ptr(h))ptr(h)->stop();}
JNIEXPORT jboolean JNICALL Java_com_omega_core_NativeEngine_submitNative(JNIEnv* e,jobject,jlong h,jobject b,jint w,jint he,jint row,jint pixel,jlong ts){
 if(!ptr(h)||!b)return false;
 auto* data=static_cast<const uint8_t*>(e->GetDirectBufferAddress(b)); auto n=e->GetDirectBufferCapacity(b);
 return n>=0 && ptr(h)->submit(data,static_cast<size_t>(n),w,he,row,pixel,ts);
}
JNIEXPORT jlongArray JNICALL Java_com_omega_core_NativeEngine_statsNative(JNIEnv* e,jobject,jlong h){
 if(!ptr(h))return nullptr; auto s=ptr(h)->stats();
 jlong a[]={static_cast<jlong>(s.state),s.accepted,s.processed,s.dropped,s.rejected,s.last_timestamp,s.last_checksum};
 auto out=e->NewLongArray(7); if(out)e->SetLongArrayRegion(out,0,7,a); return out;
}
}
