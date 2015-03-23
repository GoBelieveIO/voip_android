#ifndef VOIP_WEBRTC_H
#define VOIP_WEBRTC_H

#include "webrtc/video_engine/include/vie_capture.h"
#include "webrtc/video_engine/include/vie_base.h"
#include "webrtc/video_engine/include/vie_codec.h"
//#include "webrtc/video_engine/include/vie_encryption.h"
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
