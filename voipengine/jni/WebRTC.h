#ifndef VOIP_WEBRTC_H
#define VOIP_WEBRTC_H

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
#include "webrtc/modules/video_render/video_render_defines.h"
#include "webrtc/modules/video_render/video_render.h"



namespace webrtc {
    class VideoEngine;
    class ViEBase;
    class ViECapture;
    class ViERender;
    class ViERTP_RTCP;
    class ViECodec;
    class ViENetwork;
    class ViEImageProcess;
    class ViEEncryption;
    class VoiceEngine;
    class VoEBase;
    class VoECodec;
    class VoEHardware;
    class VoENetwork;
    class VoEAudioProcessing;
}


class WebRTC {
public:
	static WebRTC* sharedWebRTC();
	WebRTC();
public:
	webrtc::VideoEngine* video_engine;
	webrtc::ViEBase* base;
	webrtc::ViECapture* capture;
	webrtc::ViERender* render;
	webrtc::ViERTP_RTCP* rtp_rtcp;
	webrtc::ViECodec* codec;
	webrtc::ViENetwork* network;
	webrtc::ViEImageProcess* image_process;
	 
	 
	 
	webrtc::VoiceEngine *voice_engine;
	webrtc::VoEBase* voe_base;
	webrtc::VoECodec* voe_codec;
	webrtc::VoEHardware* voe_hardware;
	webrtc::VoENetwork* voe_network;
	webrtc::VoEAudioProcessing* voe_apm;
};
#endif
