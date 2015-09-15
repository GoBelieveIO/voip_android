#ifndef AVRECEIVE_STREAM_H
#define AVRECEIVE_STREAM_H
#include <stdint.h>
#include "webrtc/video_renderer.h"

class VoiceTransport;
class VoiceChannelTransport;

namespace webrtc {
    class VideoRendererInterface;
    class Call;
    class VideoReceiveStream;
    class AudioReceiveStream;
    class VideoDecoder;
    class VideoFrame;
}

class AVReceiveStream : public webrtc::VideoRenderer {

private:
    VoiceTransport *voiceTransport;
    int32_t localSSRC;
    int32_t remoteSSRC;
    int32_t rtxSSRC;

    int voiceChannel;
    VoiceChannelTransport *voiceChannelTransport;

    webrtc::Call *call_;
    webrtc::VideoReceiveStream *stream_;
    webrtc::AudioReceiveStream *audioStream_;
    webrtc::VideoDecoder *decoder_;

    webrtc::VideoRenderer *render_;

    int renderFrames_;

private:
    void startAudioStream();

public:
	AVReceiveStream(int32_t localSSRC, int32_t remoteSSRC, int32_t rtxSSRC, VoiceTransport *t);
    void start();
    void stop();

    int VoiceChannel() {
        return this->voiceChannel;
    }
    
    void setCall(webrtc::Call *c) {
        call_ = c;
    }

    void setRender(webrtc::VideoRenderer *render) {
        render_ = render;
    }

    //implement VideoRenderer
    virtual void RenderFrame(const webrtc::VideoFrame& video_frame,
                             int time_to_render_ms) ;
    virtual bool IsTextureSupported() const;

};

#endif
