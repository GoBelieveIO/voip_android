#ifndef BEETLE_VOIP_CAPTURE_H
#define BEETLE_VOIP_CAPTURE_H

#include "webrtc/common_types.h"
#include "webrtc/modules/video_capture/video_capture.h"

namespace webrtc {
    class VideoCaptureModule;
    class VideoFrame;
    class Call;
    class VideoSendStream;
    class VideoEncoder;
    class VideoRenderer;
}

class VOIPCaptureDataCallback {
public:
    virtual  ~VOIPCaptureDataCallback() {}
    virtual void onCapturedFrame(const webrtc::VideoFrame& videoFrame) = 0;
};

class VOIPCapture : public webrtc::VideoCaptureDataCallback {
public:
    VOIPCapture(webrtc::VideoRenderer *render, bool front);
    virtual ~VOIPCapture();

    void setCallback(VOIPCaptureDataCallback *callback) {
        callback_ = callback;
    }

    void switchCamera();
    void startCapture();
    void stopCapture();


    //implement VideoCaptureDataCallback
    virtual void OnIncomingCapturedFrame(const int32_t id,
                                         const webrtc::VideoFrame& videoFrame);
    virtual void OnCaptureDelayChanged(const int32_t id,
                                       const int32_t delay);

private:
    webrtc::VideoCaptureModule* module_;
    int captured_frames_;
    bool front_;    
    webrtc::VideoRenderer *render_;
    VOIPCaptureDataCallback *callback_;
};
#endif
