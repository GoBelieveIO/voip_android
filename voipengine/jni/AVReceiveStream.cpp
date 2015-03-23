#include "AVReceiveStream.h"
#include "WebRTC.h"
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

#include "ChannelTransport.h"

#define EXPECT_EQ(a, b) do {if ((a)!=(b)) {LOG("eeeeeeeeeeee");assert(0);}} while(0)
#define EXPECT_TRUE(a) do {BOOL c = (a); assert(c);} while(0)
#define EXPECT_NE(a, b) do {if ((a)==(b)) assert(0);} while(0)


AVReceiveStream::AVReceiveStream() {
	playoutDeviceIndex = 0;
	isLoudspeaker = false;
	isHeadphone = false;
	voiceTransport = NULL;
	voiceChannel = -1;
	voiceChannelTransport = NULL;
}

void AVReceiveStream::startSend()
{
//    WebRTC *rtc = WebRTC::sharedWebRTC();
    //EXPECT_EQ(0, rtc->base->StartSend(this->videoChannel));
    //this->voiceChannelTransport->SetSendDestination(ip_address, TEST_SEND_PORT+2);
    //rtc->voe_base->StartSend(this->voiceChannel);
}

void AVReceiveStream::startReceive()
{
    WebRTC *rtc = WebRTC::sharedWebRTC();
    rtc->voe_base->StartReceive(this->voiceChannel);
}

void AVReceiveStream::start()
{
    WebRTC *rtc = WebRTC::sharedWebRTC();
    
    this->voiceChannel = rtc->voe_base->CreateChannel();

    this->voiceChannelTransport = new VoiceChannelTransport(rtc->voe_network, this->voiceChannel, this->voiceTransport, false);
//    int error;
//    int audio_playback_device_index = playoutDeviceIndex;
//    error = rtc->voe_hardware->SetPlayoutDevice(audio_playback_device_index);
//    EXPECT_EQ(0, error);
    rtc->voe_apm->SetAgcStatus(true);
    rtc->voe_apm->SetNsStatus(true);
    if (!this->isHeadphone) {
        rtc->voe_apm->SetEcStatus(true);
    }
    
    startReceive();
    startSend();
    rtc->voe_base->StartPlayout(this->voiceChannel);
   
    if (this->isLoudspeaker)
        rtc->voe_hardware->SetLoudspeakerStatus(true);
}

void AVReceiveStream::stop() {
    WebRTC *rtc = WebRTC::sharedWebRTC();

    rtc->voe_base->StopReceive(this->voiceChannel);
    rtc->voe_base->StopSend(this->voiceChannel);
    rtc->voe_base->StopPlayout(this->voiceChannel);
    rtc->voe_base->DeleteChannel(this->voiceChannel);
    rtc->base->DisconnectAudioChannel(this->voiceChannel);
    delete this->voiceChannelTransport;
    this->voiceChannelTransport = NULL;
}


