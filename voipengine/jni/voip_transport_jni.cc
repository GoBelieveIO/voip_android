#include <jni.h>
#include <android/log.h>
#include <android/looper.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <limits.h> /* INT_MAX, PATH_MAX */
#include <sys/uio.h> /* writev */
#include <sys/ioctl.h>
#include <errno.h>
#include <netdb.h>
#include <assert.h>

#include "voip.h"
#include "util.h"
#include "WebRTC.h"
#include "AVSendStream.h"
#include "AVReceiveStream.h"
#include "AVTransport.h"

#define UNUSED(x) (void)(x)

//兼容没有消息头的旧版本协议
#define COMPATIBLE

#define VOIP_AUDIO 1
#define VOIP_VIDEO 2

#define VOIP_RTP 1
#define VOIP_RTCP 2

#define VOIP_AUTH 1
#define VOIP_AUTH_STATUS 2
#define VOIP_DATA 3

class VOIP;

VOIP* GetNativeVOIP(JNIEnv* jni, jobject j_voip) ;

void SetNativeVOIP(JNIEnv *jni, jobject j_voip, VOIP *voip) ;


JavaVM* getJavaVM();

class AttachThreadScoped {
public:
    AttachThreadScoped(JavaVM *jvm):_jvm(jvm), 
                                    _env(NULL), 
                                    _attached(false) {
        JNIEnv* env = NULL;
        if (jvm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
            jint res = jvm->AttachCurrentThread(&env, NULL);
            if ((res < 0) || !env) {
                LOG("jvm attach thread fail");
                return;
            }
            _attached = true;
        }
        _env = env;
    }

    ~AttachThreadScoped() {
        if (_attached) {
            _jvm->DetachCurrentThread();
        }
    }
    JNIEnv* env() {
        return _env;
    }
private:
    JavaVM *_jvm;
    JNIEnv *_env;
    bool _attached;
};

class VOIP : public VoiceTransport {
public:
    virtual int sendRTPPacketA(const void*data, int length) {
        //LOG("send rtp packet:%d", length);
        return sendPacket(data, length, true);
    }

    virtual int sendRTCPPacketA(const void*data, int length, bool STOR) {
        return sendPacket(data, length, false);
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

    int sendPacket(const void *data, int length, bool rtp) {
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

        *p++ = VOIP_AUDIO;
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
    VOIP(): j_voip(NULL), _sendStream(NULL), 
            _recvStream(NULL), _is_active(false),
            udpFD(-1), beginTime(0), isPeerConnected(false), 
            isPeerNoHeader(false), isAuth(false) {
        loadConf();
        peerIP[0] = 0;
        peerPort = 0;
        token[0] = 0;
    }

    void start() {
        _is_active = true;

        if (_sendStream||_recvStream) {
            return;
        }

        AVReceiveStream *recvStream = new AVReceiveStream();
        recvStream->voiceTransport = this;
        recvStream->isHeadphone = _headphone;
        recvStream->start();

        AVSendStream *sendStream = new AVSendStream();
        sendStream->voiceTransport = this;
        sendStream->codec = _codec;
        sendStream->recordDeviceIndex = _recordDeviceIndex;
        sendStream->start();

        _recvStream = recvStream;
        _sendStream = sendStream;

        listenVOIP();
        beginTime = getNow();
    }

    void stop() {
        _is_active = false;

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

        closeUDP();
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

    void handleVOIPData(char *buff, int len, struct sockaddr_in &addr) {
        int64_t sender, receiver;
        int packet_type;
        int type;
        char *p = buff;

        if (len <= 18) {
            LOG("invalid voip data len:%d", len);
            return;
        }
        sender = readInt64(p);
        p += 8;
        receiver = readInt64(p);
        p += 8;
        type = *p++;
        packet_type = *p++;

        if (_recvStream == NULL) {
            LOG("drop frame sender:%lld receiver:%lld type:%d", sender, receiver, type);
            return;
        }

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

        //LOG("recv packet:%d type:%d", packet_type, n-18);
        WebRTC *rtc = WebRTC::sharedWebRTC();
        if (packet_type == VOIP_RTP) {
            rtc->voe_network->ReceivedRTPPacket(_recvStream->VoiceChannel(), p, len-18);
        } else if (packet_type == VOIP_RTCP) {
            rtc->voe_network->ReceivedRTCPPacket(_recvStream->VoiceChannel(), p, len-18);
        }
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
#ifdef COMPATIBLE
        if (buf[0] == 0) {
            handleVOIPData(buf, n, addr);
            if (!isPeerNoHeader) {
                isPeerNoHeader = true;
                LOG("voip data has't data from peer");
            }
        }
#endif
    }

    void listenVOIP() {
        struct sockaddr_in addr;
        int udpFD;
        udpFD = socket(AF_INET, SOCK_DGRAM, 0);
        bzero(&addr, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        addr.sin_port = htons(voipPort);

        int one = 1; 
        setsockopt(udpFD, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
        bind(udpFD, (struct sockaddr*)&addr, sizeof(addr));

        sock_nonblock(udpFD, 1);

        int events = ALOOPER_EVENT_INPUT;
        ALooper* looper = ALooper_forThread();
        ALooper_addFd(looper, udpFD, ALOOPER_POLL_CALLBACK, events, callback, this);

        this->udpFD = udpFD;
    }
    
    void closeUDP() {
        if (this->udpFD == -1) {
            return;
        }
        
        ALooper* looper = ALooper_forThread();
        ALooper_removeFd(looper, this->udpFD);
        close(this->udpFD);
        this->udpFD = -1;
    }

 private:
    void loadConf() {
        _codec = "ILBC";
        _headphone = false;
        _recordDeviceIndex = 0;
        LOG("config codec:%s, headphone:%s, record device index:%d\n",
            _codec.c_str(), _headphone?"on":"off", _recordDeviceIndex);
    }

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

public:
    AVSendStream *_sendStream;
    AVReceiveStream *_recvStream;

    //config
    std::string _codec;
    int _recordDeviceIndex;
    bool _headphone;

    char token[256];
    int64_t peerUID;
    int64_t selfUID;

    char hostIP[64];
    int voipPort;

private:
    char peerIP[64];
    int peerPort;
    struct sockaddr_in peerAddr;

    bool _is_active;
    int udpFD;
    unsigned long beginTime;
    bool isPeerConnected;
    bool isPeerNoHeader;
    bool isAuth;
};


JOWW(void, VOIPEngine_initNative)(JNIEnv* jni, jobject j_voip, jstring j_token, jlong selfUID, jlong peerUID, jstring j_ip, jint voipPort, jstring peerIP, jint peerPort, jboolean isHeadphone) {
    LOG("voip engine init");

    VOIP *voip = new VOIP;
    voip->j_voip = jni->NewWeakGlobalRef(j_voip);
    SetNativeVOIP(jni, j_voip, voip);

    voip->_headphone = isHeadphone;
    voip->selfUID = selfUID;
    voip->peerUID = peerUID;

    const char *token = jni->GetStringUTFChars(j_token, NULL);
    assert(token && strlen(token) && strlen(token) < 256);
    strcpy(voip->token, token);

    const char *ip = jni->GetStringUTFChars(j_ip, NULL);
    assert(ip && strlen(ip) && strlen(ip) < 32);
    strcpy(voip->hostIP, ip);
    voip->voipPort = voipPort;
    
    ip = jni->GetStringUTFChars(peerIP, NULL);
    assert(ip && strlen(ip) && strlen(ip) < 32);

    voip->setPeerAddr(ip, peerPort);
    LOG("voip dial:%s, headphone:%d", ip,  isHeadphone);
}

JOWW(void, VOIPEngine_destroyNative)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    delete voip;
    SetNativeVOIP(jni, j_voip, NULL);
}

JOWW(void, VOIPEngine_start)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    voip->start();
    LOG("voip start stream");
}

JOWW(void, VOIPEngine_stop)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    LOG("voip stop stream");
    voip->stop();
}

VOIP* GetNativeVOIP(JNIEnv* jni, jobject j_voip) {
  jclass cls = jni->GetObjectClass(j_voip);
  jfieldID fieldid = jni->GetFieldID(cls, "nativeVOIP", "J");
  jlong j_p = jni->GetLongField(j_voip, fieldid);
  return reinterpret_cast<VOIP*>(j_p);
}

void SetNativeVOIP(JNIEnv *jni, jobject j_voip, VOIP *voip) {
    jclass cls = jni->GetObjectClass(j_voip);
    jfieldID fieldid = jni->GetFieldID(cls, "nativeVOIP", "J");
    jni->SetLongField(j_voip, fieldid, (long)voip);
}


