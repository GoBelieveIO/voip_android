/*
 * libjingle
 * Copyright 2015 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

// This file contain convenience functions and classes for JNI.
// Before using any of the methods, InitGlobalJniVariables must be called.

#ifndef TALK_APP_WEBRTC_JAVA_JNI_JNI_HELPERS_H_
#define TALK_APP_WEBRTC_JAVA_JNI_JNI_HELPERS_H_

#include <jni.h>
#include <string>

#include "webrtc/base/checks.h"

// Abort the process if |jni| has a Java exception pending.
// This macros uses the comma operator to execute ExceptionDescribe
// and ExceptionClear ignoring their return values and sending ""
// to the error stream.
#define CHECK_EXCEPTION(jni)    \
  CHECK(!jni->ExceptionCheck()) \
      << (jni->ExceptionDescribe(), jni->ExceptionClear(), "")

// Helper that calls ptr->Release() and aborts the process with a useful
// message if that didn't actually delete *ptr because of extra refcounts.
#define CHECK_RELEASE(ptr) \
  CHECK_EQ(0, (ptr)->Release()) << "Unexpected refcount."

namespace webrtc_jni {

jint InitGlobalJniVariables(JavaVM *jvm);

// Return a |JNIEnv*| usable on this thread or NULL if this thread is detached.
JNIEnv* GetEnv();

JavaVM *GetJVM();

// Return a |JNIEnv*| usable on this thread.  Attaches to |g_jvm| if necessary.
JNIEnv* AttachCurrentThreadIfNeeded();

// Return a |jlong| that will correctly convert back to |ptr|.  This is needed
// because the alternative (of silently passing a 32-bit pointer to a vararg
// function expecting a 64-bit param) picks up garbage in the high 32 bits.
jlong jlongFromPointer(void* ptr);

// JNIEnv-helper methods that CHECK success: no Java exception thrown and found
// object/class/method/field is non-null.
jmethodID GetMethodID(
    JNIEnv* jni, jclass c, const std::string& name, const char* signature);

jmethodID GetStaticMethodID(
    JNIEnv* jni, jclass c, const char* name, const char* signature);

jfieldID GetFieldID(JNIEnv* jni, jclass c, const char* name,
                    const char* signature);

jclass GetObjectClass(JNIEnv* jni, jobject object);

jobject GetObjectField(JNIEnv* jni, jobject object, jfieldID id);

jstring GetStringField(JNIEnv* jni, jobject object, jfieldID id);

jlong GetLongField(JNIEnv* jni, jobject object, jfieldID id);

jint GetIntField(JNIEnv* jni, jobject object, jfieldID id);

bool GetBooleanField(JNIEnv* jni, jobject object, jfieldID id);

// Java references to "null" can only be distinguished as such in C++ by
// creating a local reference, so this helper wraps that logic.
bool IsNull(JNIEnv* jni, jobject obj);

// Given a UTF-8 encoded |native| string return a new (UTF-16) jstring.
jstring JavaStringFromStdString(JNIEnv* jni, const std::string& native);

// Given a (UTF-16) jstring return a new UTF-8 native string.
std::string JavaToStdString(JNIEnv* jni, const jstring& j_string);

// Return the (singleton) Java Enum object corresponding to |index|;
jobject JavaEnumFromIndex(JNIEnv* jni, jclass state_class,
                          const std::string& state_class_name, int index);

jobject NewGlobalRef(JNIEnv* jni, jobject o);

void DeleteGlobalRef(JNIEnv* jni, jobject o);

// Scope Java local references to the lifetime of this object.  Use in all C++
// callbacks (i.e. entry points that don't originate in a Java callstack
// through a "native" method call).
class ScopedLocalRefFrame {
 public:
  explicit ScopedLocalRefFrame(JNIEnv* jni);
  ~ScopedLocalRefFrame();

 private:
  JNIEnv* jni_;
};

// Scoped holder for global Java refs.
template<class T>  // T is jclass, jobject, jintArray, etc.
class ScopedGlobalRef {
 public:
  ScopedGlobalRef(JNIEnv* jni, T obj)
      : obj_(static_cast<T>(jni->NewGlobalRef(obj))) {}
  ~ScopedGlobalRef() {
    DeleteGlobalRef(AttachCurrentThreadIfNeeded(), obj_);
  }
  T operator*() const {
    return obj_;
  }
 private:
  T obj_;
};

}  // namespace webrtc_jni



#include <android/log.h>
#include <assert.h>

#define DEBUG
#ifdef DEBUG
#define  LOG(fmt, ...)  __android_log_print(ANDROID_LOG_INFO,"beetle",\
                                            "file:%s line:%d " fmt, __FILE__,  __LINE__, \
                                              ##__VA_ARGS__)
#else
#define LOG(fmt, ...) voip_log("file:%s line:%d " fmt, __FILE__,  __LINE__, \
                                              ##__VA_ARGS__)

#endif

static inline int voip_log(const char * , ...) {
    return 0;
}
// Macro for native functions that can be found by way of jni-auto discovery.
// Note extern "C" is needed for "discovery" of native methods to work.
#define JOWW(rettype, name)                                             \
  extern "C" rettype JNIEXPORT JNICALL Java_com_beetle_##name


#define EXPECT_EQ(a, b) do {if ((a)!=(b)) {\
            LOG("eeeeeeeee");              \
            assert(0);                     \
        }                                  \
    } while(0)

#define EXPECT_TRUE(a) do {\
        bool c = (a); assert(c);                                \
        if (!c) {                                               \
            LOG("TTTTTTEEEEEEEEEEE");    \
        }                                                       \
        (void)c;                                                \
    } while(0)

#define EXPECT_NE(a, b) do {if ((a)==(b)) {\
            LOG("NNNNNNNNNEEEEEEEE");      \
            assert(0);                     \
        }                                  \
    } while(0)


#endif  // TALK_APP_WEBRTC_JAVA_JNI_JNI_HELPERS_H_
