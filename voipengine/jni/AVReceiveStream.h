#ifndef AVRECEIVE_STREAM_H
#define AVRECEIVE_STREAM_H
#include "webrtc/test/channel_transport/include/channel_transport.h"
#include "AVTransport.h"



class VideoChannelTransport;
class VoiceChannelTransport;


class AVReceiveStream {
public:
  bool isHeadphone;
  int playoutDeviceIndex;
  VoiceTransport *voiceTransport;

private:
  bool isLoudspeaker;

  int voiceChannel;
  VoiceChannelTransport *voiceChannelTransport;


public:
	AVReceiveStream();
    void start();
    void stop();

    int VoiceChannel() {
        return this->voiceChannel;
    }
private:
	void startSend();
	void startReceive();

};
#endif
