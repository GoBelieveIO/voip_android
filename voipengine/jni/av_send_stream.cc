#include "av_send_stream.h"
#include "webrtc/voice_engine/include/voe_base.h"
#include "webrtc/common_types.h"

#include "webrtc/config.h"
#include "webrtc/modules/video_capture/include/video_capture_factory.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/scoped_ptr.h"
#include "webrtc/base/asyncinvoker.h"
#include "webrtc/base/messagehandler.h"
#include "webrtc/base/bind.h"
#include "webrtc/base/helpers.h"
#include "webrtc/base/checks.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/common_video/libyuv/include/webrtc_libyuv.h"
#include "webrtc/modules/video_capture/include/video_capture.h"
#include "webrtc/video/audio_receive_stream.h"
#include "webrtc/video/video_receive_stream.h"
#include "webrtc/video/video_send_stream.h"
#include "webrtc/video_engine/vie_channel_group.h"
#include "webrtc/modules/utility/interface/process_thread.h"
#include "webrtc/modules/video_coding/codecs/h264/include/h264.h"

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

#include "webrtc/video_engine/vie_encoder.h"

#include "webrtc/engine_configurations.h"
#include "webrtc/modules/video_render/include/video_render_defines.h"
#include "webrtc/modules/video_render/include/video_render.h"
#include "webrtc/modules/video_capture/include/video_capture_factory.h"
#include "webrtc/system_wrappers/interface/tick_util.h"
#include <string>
#include <vector>
#include "WebRTC.h"
#include "AVTransport.h"
#include "ChannelTransport.h"

#include "androidmediadecoder_jni.h"
#include "androidmediaencoder_jni.h"

#include "voip_jni.h"

#define DEFAULT_AUDIO_CODEC                             "ILBC"//"ISAC"

#define WIDTH 352
#define HEIGHT 288
#define FPS 30

//portrait
#define STREAM_WIDTH 240
#define STREAM_HEIGHT 320


const char kVp8CodecName[] = "VP8";
const char kVp9CodecName[] = "VP9";
const char kH264CodecName[] = "H264";

const int kDefaultVp8PlType = 100;
const int kDefaultVp9PlType = 101;
const int kDefaultH264PlType = 107;
const int kDefaultRedPlType = 116;
const int kDefaultUlpfecType = 117;
const int kDefaultRtxVp8PlType = 96;

static const int kNackHistoryMs = 1000;

const int kMinVideoBitrate = 30;
const int kStartVideoBitrate = 300;
const int kMaxVideoBitrate = 1000;

const int kMinBandwidthBps = 30000;
const int kStartBandwidthBps = 300000;
const int kMaxBandwidthBps = 2000000;


const int kDefaultVideoMaxFramerate = 30;

static const int kDefaultQpMax = 56;


class WebRtcVcmFactory {
public:
    webrtc::VideoCaptureModule* Create(int id, const char* device) {
        return webrtc::VideoCaptureFactory::Create(id, device);
    }
    webrtc::VideoCaptureModule::DeviceInfo* CreateDeviceInfo(int id) {
        LOG("create device info");
        return webrtc::VideoCaptureFactory::CreateDeviceInfo(id);
    }
    void DestroyDeviceInfo(webrtc::VideoCaptureModule::DeviceInfo* info) {
        delete info;
    }
};

#define ARRAY_SIZE(x) (static_cast<int>(sizeof(x) / sizeof(x[0])))


AVSendStream::AVSendStream(int32_t ssrc, int32_t rtxSSRC, VoiceTransport *t):
    voiceTransport(t), ssrc(ssrc), rtxSSRC(rtxSSRC),
    voiceChannel(-1), voiceChannelTransport(NULL), 
    call_(NULL), stream_(NULL), encoder_(NULL) {
    factory_ =new WebRtcVcmFactory();

    webrtc::VideoCaptureModule::DeviceInfo* info = factory_->CreateDeviceInfo(0);
    if (!info) {
        return;
    }

    LOG("after create device info");
    int num_cams = info->NumberOfDevices();
    LOG("num cams:%d", num_cams);

    char vcm_id[256];
    bool found = false;
    for (int index = 0; index < num_cams; ++index) {
        char vcm_name[256] = {0};
        if (info->GetDeviceName(index, vcm_name, ARRAY_SIZE(vcm_name),
                                vcm_id, ARRAY_SIZE(vcm_id)) != -1) {
                
            LOG("vcm name:%s", vcm_name);
            //"Facing back" or "Facing front"
            if (strstr(vcm_name, "Facing front") != NULL) {
                found = true;
                break;
            }
        }
    }
        
    if (!found) {
        LOG("Failed to find capturer");
        factory_->DestroyDeviceInfo(info);
        return;
    }
        
    int32_t num_caps = info->NumberOfCapabilities(vcm_id);
    for (int32_t i = 0; i < num_caps; ++i) {
        webrtc::VideoCaptureCapability cap;
        if (info->GetCapability(vcm_id, i, cap) != -1) {
            LOG("cap width:%d height:%d raw type:%d max fps:%d", cap.width, cap.height, cap.rawType, cap.maxFPS);
        }
    }
    factory_->DestroyDeviceInfo(info);

        
    module_ = factory_->Create(0, vcm_id);
    if (!module_) {
        LOG("Failed to create capturer");
        return;
    }
        
    // It is safe to change member attributes now.
    module_->AddRef();
}

AVSendStream::~AVSendStream() {
    module_->Release();
    delete factory_;
}

void AVSendStream::sendKeyFrame() {
    if (stream_) {
        stream_->encoder()->SendKeyFrame();
    }
}

void AVSendStream::start() {

    captured_frames_ = 0;
    
    webrtc::VideoCaptureCapability cap;
    cap.width = WIDTH;
    cap.height = HEIGHT;
    cap.maxFPS = FPS;
    cap.rawType = webrtc::kVideoNV21;

    module_->RegisterCaptureDataCallback(*this);
    if (module_->StartCapture(cap) != 0) {
        module_->DeRegisterCaptureDataCallback();
        return;
    }


    startSendStream();

    startAudioStream();
}


void AVSendStream::setSendVoiceCodec() {
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



void AVSendStream::startAudioStream()
{
    WebRTC *rtc = WebRTC::sharedWebRTC();
    voiceChannel = rtc->voe_base->CreateChannel();

    voiceChannelTransport = new VoiceChannelTransport(rtc->voe_network, voiceChannel, this->voiceTransport, true);

    setSendVoiceCodec();

    rtc->voe_base->StartSend(voiceChannel);
}


std::vector<webrtc::VideoStream> CreateVideoStreams() {
    int max_bitrate_bps = kMaxVideoBitrate * 1000;
    
    webrtc::VideoStream stream;
    stream.width = STREAM_WIDTH;
    stream.height = STREAM_HEIGHT;
    stream.max_framerate = 30;
    
    stream.min_bitrate_bps = kMinVideoBitrate * 1000;
    stream.target_bitrate_bps = stream.max_bitrate_bps = max_bitrate_bps;
    
    int max_qp = kDefaultQpMax;
    stream.max_qp = max_qp;
    std::vector<webrtc::VideoStream> streams;
    streams.push_back(stream);
    return streams;
}


webrtc::VideoEncoderConfig CreateVideoEncoderConfig() {
    webrtc::VideoEncoderConfig encoder_config;

    encoder_config.min_transmit_bitrate_bps = 0;
    encoder_config.content_type = webrtc::VideoEncoderConfig::ContentType::kRealtimeVideo;

    encoder_config.streams = CreateVideoStreams();
    return encoder_config;
}

void* AVSendStream::ConfigureVideoEncoderSettings(webrtc::VideoCodecType type) {
    if (type == webrtc::kVideoCodecVP8) {
        encoder_settings_.vp8 = webrtc::VideoEncoder::GetDefaultVp8Settings();
        encoder_settings_.vp8.automaticResizeOn = true;
        encoder_settings_.vp8.denoisingOn = false;
        encoder_settings_.vp8.frameDroppingOn = true;
        return &encoder_settings_.vp8;
    }
    if (type == webrtc::kVideoCodecVP9) {
        encoder_settings_.vp9 = webrtc::VideoEncoder::GetDefaultVp9Settings();
        encoder_settings_.vp9.denoisingOn = false;
        encoder_settings_.vp9.frameDroppingOn = true;
        return &encoder_settings_.vp9;
    }
    return NULL;
}



void AVSendStream::startSendStream() {
    struct webrtc::VideoEncoderConfig encoder_config = CreateVideoEncoderConfig();
    if (encoder_config.streams.empty()) {
        LOG("encode stream empty");
        return;
    }
                                                                        
    webrtc::VideoEncoder *encoder = NULL;
    
    webrtc::VideoCodecType type;
    const char *codec_name;
    int pl_type;
    type = webrtc::kVideoCodecVP8;
    codec_name = kVp8CodecName;
    pl_type = kDefaultVp8PlType;

    // type = webrtc::kVideoCodecH264;
    // codec_name = kH264CodecName;
    // pl_type = kDefaultH264PlType;


    webrtc_jni::MediaCodecVideoEncoderFactory *f = new webrtc_jni::MediaCodecVideoEncoderFactory();
    //encoder = f->CreateVideoEncoder(type);

    if (encoder == NULL) {
        if (type == webrtc::kVideoCodecVP8) {
            encoder = webrtc::VideoEncoder::Create(webrtc::VideoEncoder::kVp8);
        } else if (type == webrtc::kVideoCodecVP9) {
            encoder = webrtc::VideoEncoder::Create(webrtc::VideoEncoder::kVp9);
        } else if (type == webrtc::kVideoCodecH264) {
            encoder = webrtc::VideoEncoder::Create(webrtc::VideoEncoder::kH264);
        }
        assert(encoder);
        LOG("software encode:%s", codec_name);
    } else {
        LOG("hardware encode:%s", codec_name);
    }

    delete f;
        

    webrtc::internal::VideoSendStream::Config config;

    config.encoder_settings.encoder = encoder;
    config.encoder_settings.payload_name = codec_name;
    config.encoder_settings.payload_type = pl_type;

    config.rtp.ssrcs.push_back(ssrc);
    config.rtp.nack.rtp_history_ms = kNackHistoryMs;
    config.rtp.fec.ulpfec_payload_type = kDefaultUlpfecType;
    config.rtp.fec.red_payload_type = kDefaultRedPlType;
    config.rtp.fec.red_rtx_payload_type = kDefaultRtxVp8PlType;
    
    config.rtp.rtx.payload_type = kDefaultRtxVp8PlType;
    config.rtp.rtx.ssrcs.push_back(rtxSSRC);

    
    encoder_config.encoder_specific_settings = ConfigureVideoEncoderSettings(type);
    webrtc::VideoSendStream *stream = call_->CreateVideoSendStream(config, encoder_config);
    encoder_config.encoder_specific_settings = NULL;
    stream->Start();
    stream_ = stream;
    encoder_ = encoder;
}

void AVSendStream::stop() {
    module_->DeRegisterCaptureDataCallback();
    module_->StopCapture();


    stream_->Stop();
    call_->DestroyVideoSendStream(stream_);
    stream_ = NULL;
    
    delete encoder_;
    encoder_ = NULL;


    //audio
    WebRTC *rtc = WebRTC::sharedWebRTC();

    rtc->voe_base->StopSend(voiceChannel);
    rtc->voe_base->DeleteChannel(voiceChannel);
    delete voiceChannelTransport;
    voiceChannelTransport = NULL;
}

// Callback when a frame is captured by camera.
void AVSendStream::OnIncomingCapturedFrame(const int32_t id,
                                         const webrtc::VideoFrame& frame) {
    ++captured_frames_;
    // Log the size and pixel aspect ratio of the first captured frame.
    if (1 == captured_frames_) {
        LOG("frame width:%d heigth:%d rotation:%d", frame.width(), frame.height(), frame.rotation());
    }


    //2帧取1帧
    if (stream_ && captured_frames_%2 == 0) {
        webrtc::VideoCaptureInput *input = stream_->Input();
        input->IncomingCapturedFrame(frame);
    }

    if (render_) {
        render_->RenderFrame(frame, 0);
    }
}

void AVSendStream::OnCaptureDelayChanged(const int32_t id,
                                       const int32_t delay) {
        
}

