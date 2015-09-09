#include "util.h"
#include "WebRTC.h"
#include "av_send_stream.h"
#include "av_receive_stream.h"
#include "AVTransport.h"

#include "webrtc/base/checks.h"
#include "jni_helpers.h"
#include "classreferenceholder.h"
#include "webrtc/base/scoped_ptr.h"
#include "webrtc/base/basictypes.h"
#include "voip_jni.h"

using rtc::scoped_ptr;
using namespace webrtc_jni;
//VideoRender native
class JavaVideoRendererWrapper : public webrtc::VideoRenderer {
public:
    JavaVideoRendererWrapper(JNIEnv* jni, jobject j_callbacks)
        : j_callbacks_(jni, j_callbacks),
          j_render_frame_id_(GetMethodID(
                                         jni, GetObjectClass(jni, j_callbacks), "renderFrame",
                                         "(Lorg/webrtc/VideoRenderer$I420Frame;)V")),
          j_frame_class_(jni,
                         FindClass(jni, "org/webrtc/VideoRenderer$I420Frame")),
          j_i420_frame_ctor_id_(GetMethodID(
                                            jni, *j_frame_class_, "<init>", "(III[I[Ljava/nio/ByteBuffer;)V")),
          j_texture_frame_ctor_id_(GetMethodID(
                                               jni, *j_frame_class_, "<init>",
                                               "(IIILjava/lang/Object;I)V")),
        j_byte_buffer_class_(jni, FindClass(jni, "java/nio/ByteBuffer")) {
        CHECK_EXCEPTION(jni);
    }

    virtual ~JavaVideoRendererWrapper() {}

    void RenderFrame(const webrtc::VideoFrame* video_frame) {
        ScopedLocalRefFrame local_ref_frame(jni());
        jobject j_frame =  CricketToJavaI420Frame(video_frame);
        jni()->CallVoidMethod(*j_callbacks_, j_render_frame_id_, j_frame);
        CHECK_EXCEPTION(jni());
    }

    // TODO(guoweis): Report that rotation is supported as RenderFrame calls
    // GetCopyWithRotationApplied.
    virtual bool CanApplyRotation() { return true; }



    virtual void RenderFrame(const webrtc::VideoFrame& video_frame,
                             int time_to_render_ms) {
        RenderFrame(&video_frame);
    }
    
    virtual bool IsTextureSupported() const {
        return true;
    }

private:

    size_t GetChromaHeight(const webrtc::VideoFrame* frame) const { 
        return (frame->height() + 1) / 2; 
    }
    size_t GetChromaSize(const webrtc::VideoFrame* frame) const { 
        return frame->stride(webrtc::kUPlane) * GetChromaHeight(frame); 
    }

    // Return a VideoRenderer.I420Frame referring to the data in |frame|.
    jobject CricketToJavaI420Frame(const webrtc::VideoFrame* frame) {
        jintArray strides = jni()->NewIntArray(3);
        jint* strides_array = jni()->GetIntArrayElements(strides, NULL);
        strides_array[0] = frame->stride(webrtc::kYPlane);
        strides_array[1] = frame->stride(webrtc::kUPlane);
        strides_array[2] = frame->stride(webrtc::kVPlane);
        jni()->ReleaseIntArrayElements(strides, strides_array, 0);
        jobjectArray planes = jni()->NewObjectArray(3, *j_byte_buffer_class_, NULL);
        jobject y_buffer = jni()->NewDirectByteBuffer(
                                                      const_cast<uint8*>(frame->buffer(webrtc::kYPlane)),
                                                      frame->stride(webrtc::kYPlane) * frame->height());
        jobject u_buffer = jni()->NewDirectByteBuffer(
                                                      const_cast<uint8*>(frame->buffer(webrtc::kUPlane)), GetChromaSize(frame));
        jobject v_buffer = jni()->NewDirectByteBuffer(
                                                      const_cast<uint8*>(frame->buffer(webrtc::kVPlane)), GetChromaSize(frame));

        jni()->SetObjectArrayElement(planes, 0, y_buffer);
        jni()->SetObjectArrayElement(planes, 1, u_buffer);
        jni()->SetObjectArrayElement(planes, 2, v_buffer);

        return jni()->NewObject(
                                *j_frame_class_, j_i420_frame_ctor_id_,
                                frame->width(), frame->height(),
                                static_cast<int>(frame->rotation()),
                                strides, planes);
    }

    JNIEnv* jni() {
        return AttachCurrentThreadIfNeeded();
    }

    ScopedGlobalRef<jobject> j_callbacks_;
    jmethodID j_render_frame_id_;
    ScopedGlobalRef<jclass> j_frame_class_;
    jmethodID j_i420_frame_ctor_id_;
    jmethodID j_texture_frame_ctor_id_;
    ScopedGlobalRef<jclass> j_byte_buffer_class_;
};



JOW(jlong, VideoRenderer_nativeCreateGuiVideoRenderer)(
    JNIEnv* jni, jclass, int x, int y) {
  return 0;
}

JOW(jlong, VideoRenderer_nativeWrapVideoRenderer)(
    JNIEnv* jni, jclass, jobject j_callbacks) {
  scoped_ptr<JavaVideoRendererWrapper> renderer(
      new JavaVideoRendererWrapper(jni, j_callbacks));
  return (jlong)renderer.release();
}

JOW(void, VideoRenderer_nativeCopyPlane)(
    JNIEnv *jni, jclass, jobject j_src_buffer, jint width, jint height,
    jint src_stride, jobject j_dst_buffer, jint dst_stride) {
  size_t src_size = jni->GetDirectBufferCapacity(j_src_buffer);
  size_t dst_size = jni->GetDirectBufferCapacity(j_dst_buffer);
  /*CHECK(src_stride >= width) << "Wrong source stride " << src_stride;
  CHECK(dst_stride >= width) << "Wrong destination stride " << dst_stride;
  CHECK(src_size >= src_stride * height)
      << "Insufficient source buffer capacity " << src_size;
  CHECK(dst_size >= dst_stride * height)
  << "Isufficient destination buffer capacity " << dst_size;*/

  if (src_stride < width) {
      LOG("Wrong source stride:%d", src_stride);
  }
  if (dst_stride < width) {
      LOG("wrong destination stride:%d", dst_stride);
  }
  (void)src_size;
  (void)dst_size;

  uint8_t *src =
      reinterpret_cast<uint8_t*>(jni->GetDirectBufferAddress(j_src_buffer));
  uint8_t *dst =
      reinterpret_cast<uint8_t*>(jni->GetDirectBufferAddress(j_dst_buffer));
  if (src_stride == dst_stride) {
    memcpy(dst, src, src_stride * height);
  } else {
    for (int i = 0; i < height; i++) {
      memcpy(dst, src, width);
      src += src_stride;
      dst += dst_stride;
    }
  }
}


JOW(void, VideoRenderer_freeGuiVideoRenderer)(JNIEnv*, jclass, jlong j_p) {
    
}

JOW(void, VideoRenderer_freeWrappedVideoRenderer)(JNIEnv*, jclass, jlong j_p) {
  delete reinterpret_cast<JavaVideoRendererWrapper*>(j_p);
}
