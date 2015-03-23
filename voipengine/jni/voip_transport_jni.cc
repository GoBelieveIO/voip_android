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

#define VOIP_AUDIO 1
#define VOIP_VIDEO 2

#define VOIP_RTP 1
#define VOIP_RTCP 2

#define VOIP_PORT 20001

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
    int sendPacket(const void *data, int length, bool rtp) {
        if (this->udpFD == -1) {
            return 0;
        }

        if (length > 60*1024) {
            return 0;
        }

        char buff[64*1024];

        char *p = buff;

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

        struct sockaddr_in addr;
        bzero(&addr, sizeof(addr));
        addr.sin_family = AF_INET;
        if (isP2P) {
            addr.sin_addr.s_addr = inet_addr(peerIP);
            addr.sin_port = htons(peerPort);
            //LOG("send p2p voip data...");
        } else {
            addr.sin_addr.s_addr = inet_addr(hostIP);
            addr.sin_port = htons(VOIP_PORT);
            //LOG("send voip data...");
        }
        int r = sendto(this->udpFD, buff, length + 18, 0, (struct sockaddr*)&addr, sizeof(addr));
        if (r == -1) {
            LOG("send voip data error:%d", errno);
            return 0;
        }
        return length;
    }
    
public:
    VOIP(): _sendStream(NULL), _recvStream(NULL), 
            j_voip(NULL), _is_active(false),
            udpFD(-1), beginTime(0), isPeerConnected(false) {
        loadConf();
        peerIP[0] = '0';
        peerPort = 0;
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

    void handleRead() {
        char buf[64*1024];
        struct sockaddr_in addr;
        socklen_t len = sizeof(addr);
        int n = recvfrom(this->udpFD, buf, 64*1024, 0, (struct sockaddr*)&addr, &len);
        if (n <= 0) {
            LOG("recv udp error:%d", errno);
            return;
        }

        if (n <= 16) {
            LOG("invalid voip data length");
            return;
        }

        int64_t sender, receiver;
        int packet_type;
        int type;
        char *p = buf;

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
            rtc->voe_network->ReceivedRTPPacket(_recvStream->VoiceChannel(), p, n-18);
        } else if (packet_type == VOIP_RTCP) {
            rtc->voe_network->ReceivedRTCPPacket(_recvStream->VoiceChannel(), p, n-18);
        }
    }

    void listenVOIP() {
        struct sockaddr_in addr;
        int udpFD;
        udpFD = socket(AF_INET, SOCK_DGRAM, 0);
        bzero(&addr, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        addr.sin_port = htons(VOIP_PORT);

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
    AVSendStream *_sendStream;
    AVReceiveStream *_recvStream;

    //config
    std::string _codec;
    int _recordDeviceIndex;
    bool _headphone;

    int64_t peerUID;
    int64_t selfUID;

    char hostIP[64];

    char peerIP[64];
    int peerPort;
    struct sockaddr_in peerAddr;
 public:
    jobject j_voip;

 private:
    bool _is_active;
    int udpFD;
    unsigned long beginTime;
    bool isPeerConnected;
};


JOWW(void, VOIP_initNative)(JNIEnv* jni, jobject j_voip, jlong selfUID, jlong peerUID, jstring j_ip, jstring peerIP, jint peerPort, jboolean isHeadphone) {
    LOG("voip init");

    VOIP *voip = new VOIP;
    voip->j_voip = jni->NewWeakGlobalRef(j_voip);
    SetNativeVOIP(jni, j_voip, voip);

    voip->_headphone = isHeadphone;
    voip->selfUID = selfUID;
    voip->peerUID = peerUID;

    const char *ip = jni->GetStringUTFChars(j_ip, NULL);
    assert(ip && strlen(ip) && strlen(ip) < 32);
    strcpy(voip->hostIP, ip);
    ip = jni->GetStringUTFChars(peerIP, NULL);
    assert(ip && strlen(ip) && strlen(ip) < 32);
    strcpy(voip->peerIP, ip);
    voip->peerPort = peerPort;

    bzero(&voip->peerAddr, sizeof(voip->peerAddr));
    voip->peerAddr.sin_family = AF_INET;
    voip->peerAddr.sin_addr.s_addr = inet_addr(voip->peerIP);
    voip->peerAddr.sin_port = htons(voip->peerPort);
    LOG("voip dial:%s, headphone:%d", ip,  isHeadphone);
}

JOWW(void, VOIP_destroyNative)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    delete voip;
    SetNativeVOIP(jni, j_voip, NULL);
}

JOWW(void, VOIP_start)(JNIEnv* jni, jobject j_voip) {
    VOIP *voip = GetNativeVOIP(jni, j_voip);
    if (!voip) {
        LOG("voip uninitialize");
        return;
    }
    voip->start();
    LOG("voip start stream");
}

JOWW(void, VOIP_stop)(JNIEnv* jni, jobject j_voip) {
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


