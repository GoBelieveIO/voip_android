#ifndef CHANNEL_TRANSPORT_H
#define CHANNEL_TRANSPORT_H
#include "AVTransport.h"
#include <android/log.h>
#define DEBUG 1
#if DEBUG
#define  LOG(fmt, ...)  __android_log_print(ANDROID_LOG_INFO,"beetle",\
                                            "line:%d "fmt,  __LINE__, \
                                              ##__VA_ARGS__)
#else
#define  LOG(...)  do {} while (0)
#endif

class VoiceChannelTransport:webrtc::Transport{
public:
    VoiceChannelTransport(webrtc::VoENetwork* voe_network, int channel,
                          VoiceTransport *transport, bool STOR): channel_(channel),
    voe_network_(voe_network),
    transport_(transport), STOR_(STOR){
        
        int registered = voe_network_->RegisterExternalTransport(channel,
                                                                 *this);
        
        assert(registered == 0);
        (void)(registered);
        
    }
    
    virtual ~VoiceChannelTransport() {
        transport_ = NULL;
    }
    
public:
    virtual int SendPacket(int channel, const void *data, int len) {
      return transport_->sendRTPPacketA(data, len);
    }
    virtual int SendRTCPPacket(int channel, const void *data, int len){
      return transport_->sendRTCPPacketA(data, len, STOR_);
    }

private:
    int channel_;
    webrtc::VoENetwork* voe_network_;
    VoiceTransport *transport_;
    bool STOR_;
};

class VideoChannelTransport:webrtc::Transport{
public:
    VideoChannelTransport(webrtc::ViENetwork* vie_network, int channel,
                          VideoTransport *transport, bool STOR): channel_(channel),
    vie_network_(vie_network),
    transport_(transport), STOR_(STOR){
        
        
        int registered = vie_network_->RegisterSendTransport(channel,
                                                             *this);
        
        assert(registered == 0);
        (void)(registered);
    }
    virtual ~VideoChannelTransport() {
        transport_ = NULL;
    }
    
public:
    virtual int SendPacket(int channel, const void *data, int len){
      return transport_->sendRTPPacketV(data, len);
    }

    virtual int SendRTCPPacket(int channel, const void *data, int len){
      return transport_->sendRTCPPacketV(data, len, STOR_);
    }

private:
    int channel_;
    webrtc::ViENetwork* vie_network_;
    VideoTransport *transport_;
    bool STOR_;
};




#endif
