#include "AVSendStream.h"
#include "webrtc/voice_engine/include/voe_base.h"
#include "webrtc/common_types.h"
#include "webrtc/system_wrappers/interface/constructor_magic.h"
#include "webrtc/video_engine/include/vie_base.h"
#include "webrtc/video_engine/include/vie_capture.h"
#include "webrtc/video_engine/include/vie_codec.h"
#include "webrtc/video_engine/include/vie_image_process.h"
#include "webrtc/video_engine/include/vie_network.h"
#include "webrtc/video_engine/include/vie_render.h"
#include "webrtc/video_engine/include/vie_rtp_rtcp.h"
#include "webrtc/video_engine/vie_defines.h"
#include "webrtc/video_engine/include/vie_errors.h"
#include "webrtc/video_engine/include/vie_render.h"

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


#include "webrtc/engine_configurations.h"
#include "webrtc/modules/video_render/include/video_render_defines.h"
#include "webrtc/modules/video_render/include/video_render.h"
#include "webrtc/modules/video_capture/include/video_capture_factory.h"
#include "webrtc/system_wrappers/interface/tick_util.h"
#include <string>
#include "WebRTC.h"
#include "ChannelTransport.h"




#define EXPECT_EQ(a, b) do {if ((a)!=(b)) {LOG("eeeeeee");assert(0);}} while(0)
#define EXPECT_TRUE(a) do {bool c = (a); if (!c) {LOG("eeeeeeee2");assert(c);(void)c;}} while(0)
#define EXPECT_NE(a, b) do {if ((a)==(b)) {LOG("eeeeeeee3");assert(0);}} while(0)

#define DEFAULT_AUDIO_CODEC                             "ILBC"//"ISAC"


AVSendStream::AVSendStream() {
  codec = DEFAULT_AUDIO_CODEC;
  recordDeviceIndex = 0;
  voiceTransport = NULL;
  voiceChannel = -1;
}


void AVSendStream::startSend()
{
    WebRTC *rtc = WebRTC::sharedWebRTC();
    rtc->voe_base->StartSend(voiceChannel);
}

void AVSendStream::startReceive()
{
    WebRTC *rtc = WebRTC::sharedWebRTC();
    rtc->voe_base->StartReceive(voiceChannel);
}

void AVSendStream::setSendVoiceCodec() {
    int error;
    WebRTC *rtc = WebRTC::sharedWebRTC();
    EXPECT_TRUE(rtc->voe_codec->NumOfCodecs());
    webrtc::CodecInst audio_codec;
    memset(&audio_codec, 0, sizeof(webrtc::CodecInst));
    for (int codec_idx = 0; codec_idx < rtc->voe_codec->NumOfCodecs(); codec_idx++) {
        error = rtc->voe_codec->GetCodec(codec_idx, audio_codec);
        
        if (strcmp(audio_codec.plname, this->codec.c_str()) == 0) {
            break;
        }
    }
    
    error = rtc->voe_codec->SetSendCodec(voiceChannel, audio_codec);
    EXPECT_EQ(0, error);
    LOG("codec:%s\n", audio_codec.plname);
}



void AVSendStream::start()
{
  WebRTC *rtc = WebRTC::sharedWebRTC();
  voiceChannel = rtc->voe_base->CreateChannel();

  voiceChannelTransport = new VoiceChannelTransport(rtc->voe_network, voiceChannel, this->voiceTransport, true);
  int error = 0;
  int audio_capture_device_index = recordDeviceIndex;
  error = rtc->voe_hardware->SetRecordingDevice(audio_capture_device_index);
  setSendVoiceCodec();
  error = rtc->voe_apm->SetAgcStatus(true, webrtc::kAgcDefault);
  error = rtc->voe_apm->SetNsStatus(true, webrtc::kNsHighSuppression);

  EXPECT_EQ(0, error);

  startReceive();
  startSend();

}

void AVSendStream::stop() {
    
    WebRTC *rtc = WebRTC::sharedWebRTC();
    
    rtc->voe_base->StopReceive(voiceChannel);
    rtc->voe_base->StopSend(voiceChannel);
    rtc->voe_base->DeleteChannel(voiceChannel);
    rtc->base->DisconnectAudioChannel(voiceChannel);
    delete voiceChannelTransport;
    voiceChannelTransport = NULL;
}





