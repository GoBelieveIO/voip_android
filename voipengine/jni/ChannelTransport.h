#ifndef CHANNEL_TRANSPORT_H
#define CHANNEL_TRANSPORT_H
#include "AVTransport.h"

class VoiceChannelTransport:public webrtc::Transport{
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
        voe_network_->DeRegisterExternalTransport(channel_);
        transport_ = NULL;
    }
    
public:
    virtual int SendPacket(int channel, const void *data, size_t len) {
      return transport_->sendRTPPacketA(data, len);
    }
    virtual int SendRTCPPacket(int channel, const void *data, size_t len){
      return transport_->sendRTCPPacketA(data, len, STOR_);
    }

private:
    int channel_;
    webrtc::VoENetwork* voe_network_;
    VoiceTransport *transport_;
    bool STOR_;
};

#if 0

class VideoChannelTransport:public webrtc::Transport{
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
    virtual int SendPacket(int channel, const void *data, size_t len){
      return transport_->sendRTPPacketV(data, len);
    }

    virtual int SendRTCPPacket(int channel, const void *data, size_t len){
      return transport_->sendRTCPPacketV(data, len, STOR_);
    }

private:
    int channel_;
    webrtc::ViENetwork* vie_network_;
    VideoTransport *transport_;
    bool STOR_;
};
#endif
#endif
