#include "webrtc/test/channel_transport/include/channel_transport.h"
#include "AVTransport.h"
#include <string>

namespace webrtc {
class VideoCaptureModule;
}

class VideoChannelTransport;
class VoiceChannelTransport;


class AVSendStream {
public:
  std::string codec;
  int recordDeviceIndex;
  VoiceTransport *voiceTransport;

public:
  AVSendStream();
  void start();
  void stop();
  
  int VoiceChannel() {
    return this->voiceChannel;
  }

private:
  void startSend();
  void startReceive();

  void setSendVoiceCodec();
  void setSendVideoCodec();
  void startCapture();

private:
  int voiceChannel;

  VoiceChannelTransport *voiceChannelTransport;
};


