#ifndef BEETLE_AUDIO_RECEIVE_STREAM_H
#define BEETLE_AUDIO_RECEIVE_STREAM_H


class VoiceTransport;
class VoiceChannelTransport;

class AudioReceiveStream {
private:
  VoiceTransport *voiceTransport;
  int voiceChannel;
  VoiceChannelTransport *voiceChannelTransport;


public:
	AudioReceiveStream(VoiceTransport *t);
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
