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
#include "webrtc/video_engine/include/vie_base.h"
#include "webrtc/voice_engine/include/voe_base.h"
#include "voip.h"

static JavaVM* g_vm = NULL;

JavaVM* getJavaVM() {
    return g_vm;
}

extern "C" jint JNIEXPORT JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    return JNI_VERSION_1_4;
}


JOWW(void, NativeWebRtcContextRegistry_register)(
    JNIEnv* jni,
    jclass,
    jobject context) {
    webrtc::VoiceEngine::SetAndroidObjects(g_vm, jni, context);
}

JOWW(void, NativeWebRtcContextRegistry_unRegister)(
    JNIEnv* jni,
    jclass) {
    webrtc::VoiceEngine::SetAndroidObjects(NULL, NULL, NULL);
}
