/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <jni.h>
#include <assert.h>
#include <stdint.h>
#include "webrtc/modules/video_capture/video_capture_internal.h"
//#include "webrtc/modules/video_render/video_render_internal.h"
#include "webrtc/modules/utility/include/jvm_android.h"
#include "webrtc/voice_engine/include/voe_base.h"
#include "voip_jni.h"
#include "jni_helpers.h"
#include "classreferenceholder.h"

static JavaVM* g_vm = NULL;

JavaVM* getJavaVM() {
    return g_vm;
}

extern "C" jint JNIEXPORT JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOG("on load...\n");
    g_vm = vm;

    jint ret = webrtc_jni::InitGlobalJniVariables(vm);
    if (ret < 0)
        return -1;

    webrtc_jni::LoadGlobalClassReferenceHolder();
    return ret;
}

extern "C" void JNIEXPORT JNICALL JNI_OnUnLoad(JavaVM *jvm, void *reserved) {
    webrtc_jni::FreeGlobalClassReferenceHolder();
}


JOWW(void, NativeWebRtcContextRegistry_register)(
    JNIEnv* jni,
    jclass,
    jobject context) {
    LOG("webrtc register");
    //webrtc::JVM::Initialize(g_vm, context);
    webrtc::SetCaptureAndroidVM(g_vm, context);
    //webrtc::SetRenderAndroidVM(g_vm);
    webrtc::VoiceEngine::SetAndroidObjects(g_vm, context);
}

JOWW(void, NativeWebRtcContextRegistry_unRegister)(
    JNIEnv* jni,
    jclass) {
    LOG("webrtc unregister");
    //webrtc::JVM::Uninitialize();
    webrtc::SetCaptureAndroidVM(NULL, NULL);
    //webrtc::SetRenderAndroidVM(NULL);
    webrtc::VoiceEngine::SetAndroidObjects(NULL, NULL);
}
