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

#include "webrtc/base/checks.h"
#include "jni_helpers.h"
#include "classreferenceholder.h"
#include "webrtc/base/scoped_ptr.h"
#include "voip_jni.h"

#define UNUSED(x) (void)(x)

//兼容没有消息头的旧版本协议
#define COMPATIBLE

#define VOIP_AUDIO 1
#define VOIP_VIDEO 2

#define VOIP_RTP 1
#define VOIP_RTCP 2

#define VOIP_AUTH 1
#define VOIP_AUTH_STATUS 2
#define VOIP_DATA 3




const int kMinVideoBitrate = 30;
const int kStartVideoBitrate = 300;
const int kMaxVideoBitrate = 2000;

const int kMinBandwidthBps = 30000;
const int kStartBandwidthBps = 300000;
const int kMaxBandwidthBps = 2000000;

class VOIP;

namespace webrtc {
    class VideoRenderer;
}

VOIP* GetNativeVOIP(JNIEnv* jni, jobject j_voip) ;

void SetNativeVOIP(JNIEnv *jni, jobject j_voip, VOIP *voip) ;

jstring GetToken(JNIEnv* jni, jobject j_voip);
jboolean GetIsCaller(JNIEnv* jni, jobject j_voip);
webrtc::VideoRenderer* GetLocalRender(JNIEnv* jni, jobject j_voip);
webrtc::VideoRenderer* GetRemoteRender(JNIEnv* jni, jobject j_voip);

#include "voip.cc"

JOWW(void, VOIPEngine_nativeSetToken)(JNIEnv* jni, jobject j_voip, jstring j_token) {
    const char *token = jni->GetStringUTFChars(j_token, NULL);
    assert(token && strlen(token) && strlen(token) < 256);
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    voip->setToken(token);
}

JOWW(void, VOIPEngine_nativeInit)(JNIEnv* jni, jobject j_voip, jboolean videoEnabled, jlong selfUID, jlong peerUID, 
                                  jstring j_ip, jint voipPort, jstring j_peerIP, jint peerPort) {
    LOG("voip engine init");

    jstring j_token = GetToken(jni, j_voip);
    const char *token = jni->GetStringUTFChars(j_token, NULL);
    assert(token && strlen(token) && strlen(token) < 256);

    bool isCaller = GetIsCaller(jni, j_voip);
    webrtc::VideoRenderer *localRender = GetLocalRender(jni, j_voip);
    webrtc::VideoRenderer *remoteRender = GetRemoteRender(jni, j_voip);

    const char *hostIP = jni->GetStringUTFChars(j_ip, NULL);
    assert(hostIP && strlen(hostIP) && strlen(hostIP) < 32);
    
    const char *peerIP = jni->GetStringUTFChars(j_peerIP, NULL);
    assert(peerIP && strlen(peerIP) < 32);
    LOG("voip dial:%s token:%s", peerIP, token);

    VOIP *voip = new VOIP(videoEnabled, selfUID, peerUID, token, hostIP, voipPort, peerIP, peerPort, isCaller, localRender, remoteRender);
    voip->j_voip = jni->NewWeakGlobalRef(j_voip);
    SetNativeVOIP(jni, j_voip, voip);
}

JOWW(void, VOIPEngine_destroyNative)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    delete voip;
    SetNativeVOIP(jni, j_voip, NULL);
}

JOWW(void, VOIPEngine_start)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    voip->start();
    LOG("voip start stream");
}

JOWW(void, VOIPEngine_stop)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    LOG("voip stop stream");
    voip->stop();
}

JOWW(void, VOIPEngine_switchCamera)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    LOG("voip switch camera");
    voip->switchCamera();
}


jboolean GetIsCaller(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "isCaller", "Z");
  jboolean isCaller = jni->GetBooleanField(j_voip, fieldid);
  return isCaller;
}


webrtc::VideoRenderer* GetLocalRender(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "localRender", "J");
  jlong j_p = jni->GetLongField(j_voip, fieldid);
  return reinterpret_cast<webrtc::VideoRenderer*>(j_p);
}

webrtc::VideoRenderer* GetRemoteRender(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "remoteRender", "J");
  jlong j_p = jni->GetLongField(j_voip, fieldid);
  return reinterpret_cast<webrtc::VideoRenderer*>(j_p);
}

jstring GetToken(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "token", "Ljava/lang/String;");
  jstring j_token = (jstring)jni->GetObjectField(j_voip, fieldid);
  return j_token;
}

VOIP* GetNativeVOIP(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "nativeVOIP", "J");
  jlong j_p = jni->GetLongField(j_voip, fieldid);
  return reinterpret_cast<VOIP*>(j_p);
}

void SetNativeVOIP(JNIEnv *jni, jobject j_voip, VOIP *voip) {
    jclass cls = jni->GetObjectClass(j_voip);
    jfieldID fieldid = jni->GetFieldID(cls, "nativeVOIP", "J");
    jni->SetLongField(j_voip, fieldid, (long)voip);
}

