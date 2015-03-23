LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

APP_STL:=stlport_static 

LOCAL_MODULE    := stun
LOCAL_SRC_FILES := stun_jni.cc stun.cxx udp.cxx

LOCAL_LDLIBS = -llog

include $(BUILD_SHARED_LIBRARY)
