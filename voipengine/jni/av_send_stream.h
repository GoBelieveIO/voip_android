#ifndef BEETLE_AV_SEND_STREAM_H
#define BEETLE_AV_SEND_STREAM_H
#include <string>

#include "webrtc/common_types.h"
#include "webrtc/modules/video_capture/video_capture.h"


class VoiceChannelTransport;
class VoiceTransport;

namespace webrtc {
    class VideoCaptureModule;
    class VideoFrame;
    class Call;
    class VideoSendStream;
    class VideoEncoder;
    class VideoRenderer;
    class Transport;
}

class AVSendStream : public webrtc::VideoCaptureDataCallback {
public:
    AVSendStream(int32_t ssrc, int32_t rtxSSRC, 
                 VoiceTransport *t, webrtc::Transport *transport);
    virtual ~AVSendStream();

    void start();
    void stop();
  
    int VoiceChannel() {
        return this->voiceChannel;
    }

    void setCall(webrtc::Call *c) {
        call_ = c;
    }

    void switchCamera();

    //implement VideoCaptureDataCallback
    virtual void OnIncomingCapturedFrame(const int32_t id,
                                         const webrtc::VideoFrame& videoFrame);
    virtual void OnCaptureDelayChanged(const int32_t id,
                                       const int32_t delay);
private:
    void startSend();
    void startReceive();

    void setSendVoiceCodec();
    void setSendVideoCodec();
    void startCapture();

    void startSendStream();
    void startAudioStream();

    void startCapture(bool front);
private:

    union VideoEncoderSettings {
        webrtc::VideoCodecVP8 vp8;
        webrtc::VideoCodecVP9 vp9;
    };

    void* ConfigureVideoEncoderSettings(webrtc::VideoCodecType type);

private:
    VoiceTransport *voiceTransport;

    int32_t ssrc;
    int32_t rtxSSRC;

    int voiceChannel;

    VoiceChannelTransport *voiceChannelTransport;
    webrtc::Transport *transport_;

    int captured_frames_;
    std::vector<uint8_t> capture_buffer_;
    
    webrtc::Call *call_;
    VideoEncoderSettings encoder_settings_;
    
    webrtc::VideoSendStream *stream_;
    webrtc::VideoEncoder *encoder_;
};


#endif
