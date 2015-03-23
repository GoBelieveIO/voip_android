#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include "stun.h"
#include "udp.h"
#include <netinet/in.h>
#include <arpa/inet.h>

#define  LOG(fmt, ...)  __android_log_print(ANDROID_LOG_INFO,"beetle",\
                                            "file:%s line:%d "fmt, __FILE__,  __LINE__, \
                                              ##__VA_ARGS__)


// Macro for native functions that can be found by way of jni-auto discovery.
// Note extern "C" is needed for "discovery" of native methods to work.
#define JOWW(rettype, name)                                             \
  extern "C" rettype JNIEXPORT JNICALL Java_com_beetle_voip_##name

#define VERBOSE true
 
JOWW(int, Stun_getNatType)(JNIEnv *jni, jobject thisObj, jstring stunServer) {
    StunAddress4 stunServerAddr;
    const char *ip = jni->GetStringUTFChars(stunServer, NULL);
    stunParseServerName((char*)ip, stunServerAddr);
    
    bool presPort = false, hairpin = false;
    NatType stype = stunNatType( stunServerAddr, VERBOSE, &presPort, &hairpin,
                                0, NULL);
    LOG("nat type:%d, preserve port:%d hairpin:%d\n", stype, presPort, hairpin);
    return int(stype);
}

JOWW(jobject, Stun_mapPort)(JNIEnv *jni, jobject thisObj, jstring stunServer, jint vport) {
    int fd;
    StunAddress4 stunServerAddr;
    StunAddress4 mappedAddr;
    const char *ip = jni->GetStringUTFChars(stunServer, NULL);
    stunParseServerName((char*)ip, stunServerAddr);

    fd = stunOpenSocket(stunServerAddr, &mappedAddr, vport, NULL, VERBOSE);
    if (fd == -1) {
        return NULL;
    }
    closesocket(fd);

    struct in_addr addr;
    addr.s_addr = htonl(mappedAddr.addr);
    char *p = inet_ntoa(addr);
    if (p == NULL) {
        return NULL;
    }
    LOG("mapped address:%s:%d\n", p, mappedAddr.port);
    jstring host = jni->NewStringUTF(p);
    int port = mappedAddr.port;

    jmethodID constructor;
    jclass c = jni->FindClass("java/net/InetSocketAddress");
    constructor = jni->GetMethodID(c, "<init>", "(Ljava/lang/String;IZ)V");
    
    return jni->NewObject(c, constructor, host, port, JNI_TRUE);
}
