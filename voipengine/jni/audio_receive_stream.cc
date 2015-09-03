#include "audio_receive_stream.h"

#include "webrtc/voice_engine/include/voe_network.h"
#include "webrtc/voice_engine/include/voe_base.h"
#include "webrtc/voice_engine/include/voe_audio_processing.h"
#include "webrtc/voice_engine/include/voe_dtmf.h"
#include "webrtc/voice_engine/include/voe_codec.h"
#include "webrtc/voice_engine/include/voe_errors.h"
#include "webrtc/voice_engine/include/voe_neteq_stats.h"
#include "webrtc/voice_engine/include/voe_file.h"
#include "webrtc/voice_engine/include/voe_rtp_rtcp.h"
#include "webrtc/voice_engine/include/voe_hardware.h"

#include "ChannelTransport.h"
#include "AVTransport.h"
#include "WebRTC.h"

AudioReceiveStream::AudioReceiveStream(VoiceTransport *t):
    voiceTransport(t), voiceChannel(-1), voiceChannelTransport(NULL) {
}

void AudioReceiveStream::startSend() {
}

void AudioReceiveStream::startReceive() {
    WebRTC *rtc = WebRTC::sharedWebRTC();
    rtc->voe_base->StartReceive(this->voiceChannel);
}

void AudioReceiveStream::start() {
    WebRTC *rtc = WebRTC::sharedWebRTC();
    
    this->voiceChannel = rtc->voe_base->CreateChannel();

    this->voiceChannelTransport = new VoiceChannelTransport(rtc->voe_network, this->voiceChannel, this->voiceTransport, false);
    
    startReceive();
    startSend();
    rtc->voe_base->StartPlayout(this->voiceChannel);
}

void AudioReceiveStream::stop() {
    WebRTC *rtc = WebRTC::sharedWebRTC();

    rtc->voe_base->StopReceive(this->voiceChannel);
    rtc->voe_base->StopPlayout(this->voiceChannel);
    rtc->voe_base->DeleteChannel(this->voiceChannel);
    delete this->voiceChannelTransport;
    this->voiceChannelTransport = NULL;
}

