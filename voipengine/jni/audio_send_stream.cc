#include "audio_send_stream.h"
#include "voip_jni.h"

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

#include "WebRTC.h"
#include "AVTransport.h"
#include "ChannelTransport.h"

#define DEFAULT_AUDIO_CODEC                             "ILBC"//"ISAC"

AudioSendStream::AudioSendStream(VoiceTransport *t):
    voiceTransport(t), voiceChannel(-1), voiceChannelTransport(NULL) {

}


void AudioSendStream::startSend()
{
    WebRTC *rtc = WebRTC::sharedWebRTC();
    rtc->voe_base->StartSend(voiceChannel);
}

void AudioSendStream::startReceive()
{
    WebRTC *rtc = WebRTC::sharedWebRTC();
    rtc->voe_base->StartReceive(voiceChannel);
}

void AudioSendStream::setSendVoiceCodec() {
    int error;
    WebRTC *rtc = WebRTC::sharedWebRTC();
    EXPECT_TRUE(rtc->voe_codec->NumOfCodecs());
    webrtc::CodecInst audio_codec;
    memset(&audio_codec, 0, sizeof(webrtc::CodecInst));
    for (int codec_idx = 0; codec_idx < rtc->voe_codec->NumOfCodecs(); codec_idx++) {
        error = rtc->voe_codec->GetCodec(codec_idx, audio_codec);
        
        if (strcmp(audio_codec.plname, DEFAULT_AUDIO_CODEC) == 0) {
            break;
        }
    }
    
    error = rtc->voe_codec->SetSendCodec(voiceChannel, audio_codec);
    EXPECT_EQ(0, error);
    LOG("codec:%s\n", audio_codec.plname);
}



void AudioSendStream::start()
{
  WebRTC *rtc = WebRTC::sharedWebRTC();
  voiceChannel = rtc->voe_base->CreateChannel();

  voiceChannelTransport = new VoiceChannelTransport(rtc->voe_network, voiceChannel, this->voiceTransport, true);
  setSendVoiceCodec();

  startReceive();
  startSend();

}

void AudioSendStream::stop() {
    
    WebRTC *rtc = WebRTC::sharedWebRTC();
    
    rtc->voe_base->StopReceive(voiceChannel);
    rtc->voe_base->StopSend(voiceChannel);
    rtc->voe_base->DeleteChannel(voiceChannel);
    //    rtc->base->DisconnectAudioChannel(voiceChannel);
    delete voiceChannelTransport;
    voiceChannelTransport = NULL;
}


