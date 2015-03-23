#ifndef AV_TRANSPORT_H
#define AV_TRANSPORT_H


struct VideoTransport {
    virtual ~VideoTransport() {}
    virtual int sendRTPPacketV(const void*data, int length) = 0;
    virtual int sendRTCPPacketV(const void*data, int length, bool STOR) = 0;
};

struct VoiceTransport {
    virtual ~VoiceTransport() {}
    virtual int sendRTPPacketA(const void*data, int length) = 0;
    virtual int sendRTCPPacketA(const void*data, int length, bool STOR) = 0;
};


#endif
