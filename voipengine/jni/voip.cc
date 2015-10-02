#include "webrtc/call.h"
#include "AVTransport.h"

#include "util.h"
#include "WebRTC.h"
#include "av_send_stream.h"
#include "av_receive_stream.h"
#include "audio_send_stream.h"
#include "audio_receive_stream.h"

#include "AVTransport.h"
#include <list>



class VOIPData {
public:
    VOIPData(void *data, int len) {
        _data = malloc(len);
        memcpy(_data, data, len);
        _length = len;
    }

    ~VOIPData() {
        free(_data);
    }

    void *_data;
    int _length;
};

class VOIP : public VoiceTransport, 
             public webrtc::newapi::Transport, 
             public webrtc::LoadObserver {
public:
    virtual int sendRTPPacketA(const void*data, int length) {
        //LOG("send rtp packet:%d", length);
        return sendPacket(data, length, VOIP_AUDIO, true);
    }

    virtual int sendRTCPPacketA(const void*data, int length, bool STOR) {
        return sendPacket(data, length, VOIP_AUDIO, false);
    }


    //implement webrtc::newapi::Transport
    virtual bool SendRtp(const uint8_t* data, size_t len) {
        //LOG("send rtp:%ud", len);
        sendPacket(data, len, VOIP_VIDEO, true);
        return true;
    }
    virtual bool SendRtcp(const uint8_t* data, size_t len) {
        //LOG("send rtcp:%ud", len);
        sendPacket(data, len, VOIP_VIDEO, false);
        return true;
    }
    
    //implement webrtc::LoadObserver
    void OnLoadUpdate(Load load) {
        LOG("cpu load:%d", load);
    }

private:
    static void* recv_thread(void *arg) {
        VOIP *This = (VOIP*)arg;
        This->recvLoop();
        LOG("recv thread exit");
        return NULL;
    }

    static void* deliver_thread(void *arg) {
        VOIP *This = (VOIP*)arg;
        This->deliverLoop();
        LOG("deliver thread exit");
        return NULL;
    }

    void deliverLoop() {
        while (this->_is_active) {
            pthread_mutex_lock(&_mutex);

            while(_packets.size() == 0 && this->_is_active) {
                struct timeval tv;
                struct timespec ts;
                gettimeofday(&tv, NULL);
                ts.tv_sec = tv.tv_sec;
                ts.tv_nsec = tv.tv_usec*1000 + 1000*1000*100;
                //wait 100ms
                pthread_cond_timedwait(&_cond, &_mutex, &ts);
            }

            std::vector<VOIPData*> packets(_packets);
            _packets.erase(_packets.begin(), _packets.end());

            pthread_mutex_unlock(&_mutex);

            for (int i = 0; i < packets.size(); i++) {
                VOIPData *vdata = packets[i];
                onVOIPData(vdata);
                delete vdata;
            }
        }
    }
    void recvLoop() {
        struct sockaddr_in addr;
        int udpFD;
        udpFD = socket(AF_INET, SOCK_DGRAM, 0);
        bzero(&addr, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        addr.sin_port = htons(voipPort);

        int one = 1; 
        setsockopt(udpFD, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

        int bufSize = 0;
        socklen_t size = sizeof(bufSize);
        getsockopt(udpFD, SOL_SOCKET, SO_RCVBUF, (void*)&bufSize, &size);
        LOG("udp recv buf size:%d", bufSize);
        bufSize = 1024 * 1024;
        if (setsockopt(udpFD, SOL_SOCKET, SO_RCVBUF, &bufSize, sizeof(int)) == -1) {
            LOG("set sock recv buf size error");
        } else {
            LOG("new udp recv buf size:%d", bufSize);
        }

        bind(udpFD, (struct sockaddr*)&addr, sizeof(addr));

        this->udpFD = udpFD;

        sendAuth();

        unsigned long lastAuthTS = getNow();
        while (this->_is_active) {
            fd_set rds;
            struct timeval timeout;
            timeout.tv_sec = 0;
            timeout.tv_usec = 1000*400;//400ms

            FD_ZERO(&rds);
            FD_SET(udpFD, &rds);
            int r = select(udpFD + 1, &rds, NULL, NULL, &timeout);
            if (r == -1) {
                LOG("select error:%s", strerror(errno));
                break;
            } else if (r == 1) {
                handleRead();
            }

            unsigned long now = getNow();
            if (!this->isAuth && (now - lastAuthTS) > 1*1000) {
                sendAuth();
                lastAuthTS = now;
            } else if (this->isAuth && (now - lastAuthTS) > 10*1000) {
                sendAuth();
                lastAuthTS = now;
            }
        }

        this->udpFD = -1;
        close(udpFD);

    }
private:
    void sendAuth() {
        if (strlen(token) == 0) {
            return;
        }

        int len = strlen(token);
        char buff[1024] = {0};
        
        char *p = buff;
        *p = (char)VOIP_AUTH;
        p++;
        
        writeInt16(len, p);
        p += 2;
        memcpy(p, token, len);
        p += len;

        struct sockaddr_in addr;
        bzero(&addr, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = inet_addr(hostIP);
        addr.sin_port = htons(voipPort);

        int r = sendto(this->udpFD, buff, len + 3, 0, (struct sockaddr*)&addr, sizeof(addr));
        if (r == -1) {
            LOG("send voip data error:%d", errno);
        }
    }

    int sendPacket(const void *data, int length, int8_t type, bool rtp) {
        if (this->udpFD == -1) {
            return 0;
        }

        if (length > 60*1024) {
            return 0;
        }


        bool isP2P = false;
        if (strlen(peerIP) > 0 && peerPort > 0) {
            isP2P = true;
        }

        if (isP2P && !isPeerConnected) {
            unsigned long now = getNow();
            if (now - beginTime > 2000) {
                isP2P = false;
            }
        }
        if (!isP2P && !this->isAuth) {
            sendAuth();
        }

        char buff[64*1024];
        char *p = buff;


        if (!isPeerNoHeader) {
            *p = (char)VOIP_DATA;
            p++;
        }

        writeInt64(selfUID, p);
        p += 8;
        writeInt64(peerUID, p);
        p += 8;

        *p++ = type;
        if (rtp) {
            *p++ = VOIP_RTP;
        } else {
            *p++ = VOIP_RTCP;
        }
        memcpy(p, data, length);
        p += length;

        struct sockaddr_in addr;
        bzero(&addr, sizeof(addr));
        addr.sin_family = AF_INET;
        if (isP2P) {
            addr.sin_addr.s_addr = inet_addr(peerIP);
            addr.sin_port = htons(peerPort);
            //LOG("send p2p voip data...");
        } else {
            addr.sin_addr.s_addr = inet_addr(hostIP);
            addr.sin_port = htons(voipPort);
            //LOG("send voip data...");
        }

        int bufLen = 0;
        if (isPeerNoHeader) {
            bufLen = length + 18;
        } else {
            bufLen = length + 19;
        }

        int r = sendto(this->udpFD, buff, bufLen, 0, (struct sockaddr*)&addr, sizeof(addr));
        if (r == -1) {
            LOG("send voip data error:%d", errno);
            return 0;
        }
        return length;
    }
    
public:
    VOIP(bool videoEnabled, int64_t selfUID, int64_t peerUID, const char *token, 
         const char *hostIP, int voipPort, const char *peerIP, int peerPort, bool isCaller,
         webrtc::VideoRenderer *localRender, webrtc::VideoRenderer *remoteRender)
        :j_voip(NULL), _videoEnabled(videoEnabled),
        _sendStream(NULL), 
        _recvStream(NULL), _is_active(false),
        udpFD(-1), beginTime(0), isPeerConnected(false), 
        isPeerNoHeader(false), isAuth(false) {
        
        this->selfUID = selfUID;
        this->peerUID = peerUID;
        strcpy(this->token, token);
        strcpy(this->hostIP, hostIP);
        this->voipPort = voipPort;
        setPeerAddr(peerIP, peerPort);

        this->_isCaller = isCaller;
        this->localRender = localRender;
        this->remoteRender = remoteRender;

        pthread_mutex_init(&_mutex, NULL);
        pthread_cond_init(&_cond, NULL);
    }

    ~VOIP() {
        pthread_mutex_destroy(&_mutex);
        pthread_cond_destroy(&_cond);
    }

    void setToken(const char *token) {
        strcpy(this->token, token);
    }

    void startAudioStream() {
        AudioSendStream *sendStream = new AudioSendStream(this);
        sendStream->start();

        AudioReceiveStream *recvStream = new AudioReceiveStream(this);
        recvStream->start();

        _audioSendStream = sendStream;
        _audioRecvStream = recvStream;
    }
    void startAVStream() {
        if (_sendStream||_recvStream) {
            return;
        }

        WebRTC *rtc = WebRTC::sharedWebRTC();
        int error = 0;
        error = rtc->voe_apm->SetNsStatus(true, webrtc::kNsHighSuppression);
        LOG("error:%d", error);
        error = rtc->voe_apm->SetEcStatus(true);
        LOG("error:%d", error);

        webrtc::Call::Config config(this);
        config.overuse_callback = this;
        config.voice_engine = rtc->voice_engine;
    
        config.bitrate_config.min_bitrate_bps = kMinBandwidthBps;
        config.bitrate_config.start_bitrate_bps = kStartBandwidthBps;
        config.bitrate_config.max_bitrate_bps = kMaxBandwidthBps;
        _call = webrtc::Call::Create(config);

        AVSendStream *sendStream = NULL;
        
        //caller(1:3:101)
        //callee(2:4:102)
        if (_isCaller) {
            sendStream = new AVSendStream(1, 101, this);
        } else {
            sendStream = new AVSendStream(2, 102, this);
        }
        sendStream->setCall(_call);
        sendStream->setRender(localRender);

        sendStream->start();

        _sendStream = sendStream;

        AVReceiveStream *recvStream = NULL;
        if (_isCaller) {
            recvStream = new AVReceiveStream(3, 2, 102, this);
        } else {
            recvStream = new AVReceiveStream(4, 1, 101, this);
        }
        recvStream->setCall(_call);

        recvStream->setRender(remoteRender);
        recvStream->start();
        _recvStream = recvStream;
    }

    void start() {
        if (_is_active) {
            return;
        }
        _is_active = true;


        if (_videoEnabled) {
            startAVStream();
        } else {
            startAudioStream();
        }
        
        pthread_create(&_thread, NULL, VOIP::recv_thread, this);
        pthread_create(&_deliverThread, NULL, VOIP::deliver_thread, this);

        beginTime = getNow();
    }


  
    void stopAVStream() {
        if (_sendStream) {
            _sendStream->stop();
            delete _sendStream;
            _sendStream = NULL;
        }
        if (_recvStream) {
            _recvStream->stop();
            delete _recvStream;
            _recvStream = NULL;
        }

        delete _call;
        _call = NULL;
        
    }

    void stopAudioStream() {
        if (_audioSendStream) {
            _audioSendStream->stop();
            delete _audioSendStream;
            _audioSendStream = NULL;
        }
        if (_audioRecvStream) {
            _audioRecvStream->stop();
            delete _audioRecvStream;
            _audioRecvStream = NULL;
        }
    }

    void stop() {
        if (!_is_active) {
            return;
        }

        _is_active = false;

        if (_videoEnabled) {
            stopAVStream();
        } else {
            stopAudioStream();
        }

        pthread_join(_thread, NULL);
        pthread_join(_deliverThread, NULL);

        for (int i = 0; i < _packets.size(); i++) {
            delete _packets[i];
        }
        _packets.erase(_packets.begin(), _packets.end());
    }

    void switchCamera() {
        if (_sendStream) {
            _sendStream->switchCamera();
        }
    }

    int sock_nonblock(int fd, int set) {
        int r;
        do {
            r = ioctl(fd, FIONBIO, &set);
        } while (r == -1 && errno == EINTR);
        return r;
    }

#define REGISTER_CALLBACK 1

    static int callback(int fd, int events, void* data) {
        VOIP *self = (VOIP*)data;
        self->handleRead();
        return REGISTER_CALLBACK;
    }

    void onVOIPData(VOIPData *data) {
        int64_t sender, receiver;
        int packet_type;
        int type;

        char *p = (char*)data->_data;
        int len = data->_length;

        sender = readInt64(p);
        p += 8;
        receiver = readInt64(p);
        p += 8;
        type = *p++;
        packet_type = *p++;
        if (!_videoEnabled) {

            if (_audioRecvStream == NULL) {
                LOG("drop frame sender:%lld receiver:%lld type:%d", sender, receiver, type);
                return;
            }
            int channel = _audioRecvStream->VoiceChannel();
            WebRTC *rtc = WebRTC::sharedWebRTC();
            if (packet_type == VOIP_RTP) {
                if (type == VOIP_AUDIO) {
                    rtc->voe_network->ReceivedRTPPacket(channel, p, len - 18);
                }
            } else if (packet_type == VOIP_RTCP) {
                if (type == VOIP_AUDIO) {
                    rtc->voe_network->ReceivedRTCPPacket(channel, p, len - 18);
                }
            }
        } else {
            if (_recvStream == NULL) {
                LOG("drop frame sender:%lld receiver:%lld type:%d", sender, receiver, type);
                return;
            }
            
            int channel = _recvStream->VoiceChannel();
            WebRTC *rtc = WebRTC::sharedWebRTC();
            if (packet_type == VOIP_RTP) {
                if (type == VOIP_AUDIO) {
                    rtc->voe_network->ReceivedRTPPacket(channel, p, len-18);
                } else if (type == VOIP_VIDEO && _call) {
                    _call->Receiver()->DeliverPacket(webrtc::MediaType::VIDEO, (const uint8_t*)p, len - 18);
                }
            } else if (packet_type == VOIP_RTCP) {
                if (type == VOIP_AUDIO) {
                    rtc->voe_network->ReceivedRTCPPacket(channel, p, len - 18);
                } else if (type == VOIP_VIDEO && _call) {
                    _call->Receiver()->DeliverPacket(webrtc::MediaType::VIDEO, (const uint8_t*)p, len - 18);
                }
            }
        }
        
    }

    void handleVOIPData(char *buff, int len, struct sockaddr_in &addr) {
        bool isP2P = false;
        if (strlen(peerIP) > 0 && peerPort > 0) {
            isP2P = true;
        }
        
        if (isP2P && !this->isPeerConnected) {
            if (addr.sin_addr.s_addr == peerAddr.sin_addr.s_addr &&
                addr.sin_port == peerAddr.sin_port) {
                this->isPeerConnected = true;
                LOG("peer connected");
            }
        }

        VOIPData *vdata = new VOIPData(buff, len);
        pthread_mutex_lock(&_mutex);
        _packets.push_back(vdata);
        pthread_cond_signal(&_cond);
        pthread_mutex_unlock(&_mutex);
    }

    void handleAuthStatus(char *buff, int len) {
        if (len == 0) {
            return;
        }
        int status = buff[0];
        if (status == 0) {
            this->isAuth = true;
        }
        LOG("voip tunnel auth status:%d", status);
    }

    void handleRead() {
        char buf[64*1024];
        struct sockaddr_in addr;
        socklen_t len = sizeof(addr);
        int n = recvfrom(this->udpFD, buf, 64*1024, 0, (struct sockaddr*)&addr, &len);
        if (n <= 0) {
            LOG("recv udp error:%d", errno);
            return;
        }

        int cmd = buf[0] & 0x0f;
        if (cmd == VOIP_AUTH_STATUS) {
            handleAuthStatus(buf+1, n-1);
        } else if (cmd == VOIP_DATA) {
            handleVOIPData(buf+1, n-1, addr);
        }
    }

 private:

    unsigned long getNow() {
        struct timeval tv;
        gettimeofday(&tv, NULL);
        unsigned long time_in_micros = 1000000 * tv.tv_sec + tv.tv_usec;
        return time_in_micros/1000;
    }

public:
    void setPeerAddr(const char *ip, int port) {
        VOIP *voip = this;
        strcpy(voip->peerIP, ip);
        voip->peerPort = port;

        bzero(&voip->peerAddr, sizeof(voip->peerAddr));
        voip->peerAddr.sin_family = AF_INET;
        voip->peerAddr.sin_addr.s_addr = inet_addr(voip->peerIP);
        voip->peerAddr.sin_port = htons(voip->peerPort);
    }

public:
    jobject j_voip;

private:
    bool _videoEnabled;
    webrtc::VideoRenderer *localRender;
    webrtc::VideoRenderer *remoteRender;

    AudioSendStream *_audioSendStream;
    AudioReceiveStream *_audioRecvStream;
    
    AVSendStream *_sendStream;
    AVReceiveStream *_recvStream;

    bool _isCaller;

    char token[256];
    int64_t peerUID;
    int64_t selfUID;

    char hostIP[64];
    int voipPort;

private:
    char peerIP[64];
    int peerPort;
    struct sockaddr_in peerAddr;

    volatile bool _is_active;
    int udpFD;
    unsigned long beginTime;
    bool isPeerConnected;
    bool isPeerNoHeader;
    bool isAuth;


    webrtc::Call *_call;
    pthread_t _thread;

    pthread_t _deliverThread;
    pthread_mutex_t _mutex;
    pthread_cond_t _cond;

    std::vector<VOIPData*> _packets;
};
