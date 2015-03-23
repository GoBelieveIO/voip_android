
#ifndef BEETLE_VOIP_H
#define BEETLE_VOIP_H

#include <android/log.h>
#include <assert.h>

#define  LOG(fmt, ...)  __android_log_print(ANDROID_LOG_INFO,"beetle",\
                                            "file:%s line:%d "fmt, __FILE__,  __LINE__, \
                                              ##__VA_ARGS__)

// Macro for native functions that can be found by way of jni-auto discovery.
// Note extern "C" is needed for "discovery" of native methods to work.
#define JOWW(rettype, name)                                             \
  extern "C" rettype JNIEXPORT JNICALL Java_com_beetle_##name


#define EXPECT_EQ(a, b) do {if ((a)!=(b)) assert(0);} while(0)
#define EXPECT_TRUE(a) do {bool c = (a); assert(c);(void)c;} while(0)
#define EXPECT_NE(a, b) do {if ((a)==(b)) assert(0);} while(0)

#endif
