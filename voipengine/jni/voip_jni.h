#ifndef BEETLE_VOIP_JNI_H
#define BEETLE_VOIP_JNI_H

#include <android/log.h>
#include <assert.h>

#define DEBUG
#ifdef DEBUG
#define  LOG(fmt, ...)  __android_log_print(ANDROID_LOG_INFO,"beetle",\
                                            "file:%s line:%d " fmt, __FILE__,  __LINE__, \
                                              ##__VA_ARGS__)
#else
#define LOG(fmt, ...) voip_log("file:%s line:%d " fmt, __FILE__,  __LINE__, \
                                              ##__VA_ARGS__)

#endif

static inline int voip_log(const char * , ...) {
    return 0;
}
// Macro for native functions that can be found by way of jni-auto discovery.
// Note extern "C" is needed for "discovery" of native methods to work.
#define JOWW(rettype, name)                                             \
  extern "C" rettype JNIEXPORT JNICALL Java_com_beetle_##name


#define EXPECT_EQ(a, b) do {if ((a)!=(b)) {\
            LOG("eeeeeeeee");              \
            assert(0);                     \
        }                                  \
    } while(0)

#define EXPECT_TRUE(a) do {\
        bool c = (a); assert(c);                                \
        if (!c) {                                               \
            LOG("TTTTTTEEEEEEEEEEE");    \
        }                                                       \
        (void)c;                                                \
    } while(0)

#define EXPECT_NE(a, b) do {if ((a)==(b)) {\
            LOG("NNNNNNNNNEEEEEEEE");      \
            assert(0);                     \
        }                                  \
    } while(0)


#endif
