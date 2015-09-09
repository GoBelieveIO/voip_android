#include "WebRTC.h"
#include "voip_jni.h"

WebRTC* WebRTC::sharedWebRTC() {
	static WebRTC *obj = NULL;
	if (!obj) {
		obj = new WebRTC;
	}
	return obj;
}



WebRTC::WebRTC() {
        
    int error = 0;

    webrtc::VoiceEngine* voe = webrtc::VoiceEngine::Create();
	voice_engine = voe;
	voe_base = webrtc::VoEBase::GetInterface(voe);
	
	LOG("voe:%p", voe_base);
	error = voe_base->Init();
	LOG("init voe:%d", error);
	EXPECT_EQ(0, error);
	voe_codec = webrtc::VoECodec::GetInterface(voe);
	
	
	voe_hardware = webrtc::VoEHardware::GetInterface(voe);
	
	voe_network = webrtc::VoENetwork::GetInterface(voe);
	
	
	voe_apm = webrtc::VoEAudioProcessing::GetInterface(voe);


#if 0	
	video_engine = webrtc::VideoEngine::Create();
	EXPECT_TRUE(video_engine != NULL);
	EXPECT_EQ(0, video_engine->SetTraceFilter(webrtc::kTraceInfo));
	
	base = webrtc::ViEBase::GetInterface(video_engine);
	EXPECT_TRUE(base != NULL);
	
	EXPECT_EQ(0, base->Init());
	
	base->SetVoiceEngine(voice_engine);
	
	capture = webrtc::ViECapture::GetInterface(video_engine);
	EXPECT_TRUE(capture != NULL);
	
	
	rtp_rtcp = webrtc::ViERTP_RTCP::GetInterface(video_engine);
	EXPECT_TRUE(rtp_rtcp != NULL);
	render = webrtc::ViERender::GetInterface(video_engine);
	EXPECT_TRUE(render != NULL);
	
	codec = webrtc::ViECodec::GetInterface(video_engine);
	EXPECT_TRUE(codec != NULL);
	
	network = webrtc::ViENetwork::GetInterface(video_engine);
	EXPECT_TRUE(network != NULL);
	
	image_process = webrtc::ViEImageProcess::GetInterface(video_engine);
	EXPECT_TRUE(image_process != NULL);
#endif
}
