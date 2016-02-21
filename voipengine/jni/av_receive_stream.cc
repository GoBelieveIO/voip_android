#include "av_receive_stream.h"
#include "webrtc/common_types.h"
#include "webrtc/audio/audio_receive_stream.h"
#include "webrtc/video/video_receive_stream.h"
#include "webrtc/video/video_send_stream.h"

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
#include "androidmediadecoder_jni.h"
#include "androidmediaencoder_jni.h"
#include "ChannelTransport.h"
#undef LOG
#include "voip_jni.h"

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

AVReceiveStream::AVReceiveStream(int32_t lssrc, int32_t rssrc, int32_t rtxSSRC, VoiceTransport *t, webrtc::Transport *transport):
    voiceTransport(t), localSSRC(lssrc), remoteSSRC(rssrc), rtxSSRC(rtxSSRC), 
    voiceChannel(-1), voiceChannelTransport(NULL), transport_(transport),
    call_(NULL), stream_(NULL), audioStream_(NULL),
    decoder_(NULL), renderFrames_(0), frameWidth_(0), frameHeight_(0) {
    
}

void AVReceiveStream::start() {
    webrtc::VideoReceiveStream::Config config(transport_);

    webrtc::VideoCodecType type;
    const char *codec_name;
    int pl_type;
    type = webrtc::kVideoCodecVP8;
    codec_name = kVp8CodecName;
    pl_type = kDefaultVp8PlType;

    // type = webrtc::kVideoCodecH264;
    // codec_name = kH264CodecName;
    // pl_type = kDefaultH264PlType;

    webrtc::VideoDecoder *video_decoder = NULL;
    
    webrtc_jni::MediaCodecVideoDecoderFactory *f = new webrtc_jni::MediaCodecVideoDecoderFactory();

    video_decoder = f->CreateVideoDecoder(type);

    if (video_decoder == NULL) {
        if (type == webrtc::kVideoCodecVP8) {
            video_decoder = webrtc::VideoDecoder::Create(webrtc::VideoDecoder::kVp8);
        } else if (type == webrtc::kVideoCodecVP9) {
            video_decoder = webrtc::VideoDecoder::Create(webrtc::VideoDecoder::kVp9);
        } else if (type == webrtc::kVideoCodecH264) {
            video_decoder = webrtc::VideoDecoder::Create(webrtc::VideoDecoder::kH264);
        }
        assert(video_decoder);
        LOG("software decode:%s", codec_name);
    } else {
        LOG("hardware decode:%s", codec_name);
    }

    delete f;
    
    webrtc::VideoReceiveStream::Decoder decoder;
    decoder.decoder = video_decoder;
    decoder.payload_type = pl_type;
    decoder.payload_name = codec_name;
    config.decoders.push_back(decoder);

    config.rtp.local_ssrc = localSSRC;
    config.rtp.remote_ssrc = remoteSSRC;

    config.rtp.nack.rtp_history_ms = kNackHistoryMs;
    config.rtp.fec.ulpfec_payload_type = kDefaultUlpfecType;
    config.rtp.fec.red_payload_type = kDefaultRedPlType;
    config.rtp.fec.red_rtx_payload_type = kDefaultRtxVp8PlType;
    

    webrtc::VideoReceiveStream::Config::Rtp::Rtx rtx;
    rtx.ssrc = rtxSSRC;
    rtx.payload_type = kDefaultRtxVp8PlType;
    config.rtp.rtx[kDefaultVp8PlType] = rtx;

    
//    config.sync_group = "sync";
    config.renderer = this;
    
    webrtc::VideoReceiveStream *stream = call_->CreateVideoReceiveStream(config);
    stream->Start();
    
    stream_ = stream;
    decoder_ = video_decoder;
    

    startAudioStream();
}

void AVReceiveStream::startAudioStream() {

    WebRTC *rtc = WebRTC::sharedWebRTC();
    
    this->voiceChannel = rtc->voe_base->CreateChannel();

    this->voiceChannelTransport = new VoiceChannelTransport(rtc->voe_network, this->voiceChannel, this->voiceTransport, false);


    rtc->voe_base->StartReceive(this->voiceChannel);
    rtc->voe_base->StartPlayout(this->voiceChannel);
}

void AVReceiveStream::stop() {
    if (stream_ == NULL) {
        return;
    }
    //video
    stream_->Stop();
    call_->DestroyVideoReceiveStream(stream_);
    stream_ = NULL;
    
    delete decoder_;
    decoder_ = NULL;

    WebRTC *rtc = WebRTC::sharedWebRTC();

    delete voiceChannelTransport;
    voiceChannelTransport = NULL;

    rtc->voe_base->StopReceive(voiceChannel);
    rtc->voe_base->StopPlayout(voiceChannel);
    rtc->voe_base->DeleteChannel(voiceChannel);
}

void AVReceiveStream::RenderFrame(const webrtc::VideoFrame& video_frame,
                                          int time_to_render_ms) {
    
    renderFrames_++;

    if (video_frame.width() != frameWidth_ || video_frame.height() != frameHeight_) {
        LOG("render frame:%d %d", video_frame.width(), video_frame.height());       
        frameWidth_ = video_frame.width();
        frameHeight_ = video_frame.height();
    }

    if (render_) {
        render_->RenderFrame(video_frame, time_to_render_ms);
    }
}
    
bool AVReceiveStream::IsTextureSupported() const {
    return true;
}

