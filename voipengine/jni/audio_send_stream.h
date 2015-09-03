#ifndef BEETLE_AUDIO_SEND_STREAM_H
#define BEETLE_AUDIO_SEND_STREAM_H
#include <string>


class VoiceChannelTransport;
class VoiceTransport;

class AudioSendStream {
public:
    AudioSendStream(VoiceTransport *t);
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
    VoiceTransport *voiceTransport;

    int voiceChannel;

    VoiceChannelTransport *voiceChannelTransport;
};

#endif
