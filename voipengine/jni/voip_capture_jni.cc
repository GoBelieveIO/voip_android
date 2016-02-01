#include <jni.h>
#include <android/log.h>
#include <android/looper.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <limits.h> /* INT_MAX, PATH_MAX */
#include <sys/uio.h> /* writev */
#include <sys/ioctl.h>
#include <errno.h>
#include <netdb.h>
#include <assert.h>

#include "webrtc/common_types.h"

#include "jni_helpers.h"
#include "voip_jni.h"

#include "voip_capture.h"


static VOIPCapture* GetNativeObject(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "nativeVOIP", "J");
  jlong j_p = jni->GetLongField(j_voip, fieldid);
  return reinterpret_cast<VOIPCapture*>(j_p);
}

static void SetNativeObject(JNIEnv *jni, jobject j_voip, VOIPCapture *voip) {
    jclass cls = jni->GetObjectClass(j_voip);
    jfieldID fieldid = jni->GetFieldID(cls, "nativeVOIP", "J");
    jni->SetLongField(j_voip, fieldid, (long)voip);
}

static jboolean GetIsFrontCamera(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "isFrontCamera", "Z");
  jboolean isFrontCamera = jni->GetBooleanField(j_voip, fieldid);
  return isFrontCamera;
}

static webrtc::VideoRenderer* GetRender(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "render", "J");
  jlong j_p = jni->GetLongField(j_voip, fieldid);
  return reinterpret_cast<webrtc::VideoRenderer*>(j_p);
}


JOWW(void, VOIPCapture_nativeInit)(JNIEnv* jni, jobject j_voip) {
    LOG("voip capture init");
    bool isFrontCamera = GetIsFrontCamera(jni, j_voip);
    webrtc::VideoRenderer *localRender = GetRender(jni, j_voip);

    VOIPCapture *voip = new VOIPCapture(localRender, isFrontCamera);
    SetNativeObject(jni, j_voip, voip);
}

JOWW(void, VOIPCapture_destroyNative)(JNIEnv* jni, jobject j_voip) {
    VOIPCapture *voip = GetNativeObject(jni, j_voip);
    if (!voip) {
        LOG("voip capture uninitialize");
        return;
    }
    delete voip;
    SetNativeObject(jni, j_voip, NULL);
}

JOWW(void, VOIPCapture_startCapture)(JNIEnv* jni, jobject j_voip) {
    VOIPCapture *voip = GetNativeObject(jni, j_voip);
    if (!voip) {
        LOG("voip catpure uninitialize");
        return;
    }
    voip->startCapture();
    LOG("voip capture start");
}

JOWW(void, VOIPCapture_stopCapture)(JNIEnv* jni, jobject j_voip) {
    VOIPCapture *voip = GetNativeObject(jni, j_voip);
    if (!voip) {
        LOG("voip capture uninitialize");
        return;
    }
    LOG("voip capture stop");
    voip->stopCapture();
}

JOWW(void, VOIPCapture_switchCamera)(JNIEnv* jni, jobject j_voip) {
    VOIPCapture *voip = GetNativeObject(jni, j_voip);
    if (!voip) {
        LOG("voip catpure uninitialize");
        return;
    }
    LOG("voip capture switch camera");
    voip->switchCamera();
}

