#include "util.h"
#include <string.h>
#include <stdlib.h>
#include <arpa/inet.h>

void writeInt32(int32_t v, void *p) {
    v = htonl(v);
    memcpy(p, &v, 4);
}

int32_t readInt32(const void *p) {
    int32_t v;
    memcpy(&v, p, 4);
    return ntohl(v);
}


int64_t hton64(int64_t val )
{
    int64_t high, low;
    low = (int64_t)(val & 0x00000000FFFFFFFF);
    val >>= 32;
    high = (int64_t)(val & 0x00000000FFFFFFFF);
    low = htonl( low );
    high = htonl( high );
    return (int64_t)low << 32 | high;
}
int64_t ntoh64(int64_t val )
{
    int64_t high, low;
    low = (int64_t)(val & 0x00000000FFFFFFFF);
    val>>=32;
    high = (int64_t)(val & 0x00000000FFFFFFFF);
    low = ntohl( low );
    high = ntohl( high );
    return (int64_t)low << 32 | high;
}


void writeInt64(int64_t v, void *p) {
    v = hton64(v);
    memcpy(p, &v, 8);
}
int64_t readInt64(const void *p) {
    int64_t v;
    memcpy(&v, p, 8);
    return ntoh64(v);
}


