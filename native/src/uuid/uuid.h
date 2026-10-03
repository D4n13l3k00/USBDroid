#ifndef USBDROID_UUID_H
#define USBDROID_UUID_H
#include <stdlib.h>
typedef unsigned char uuid_t[16];
/* Only uuid_generate is used by Syslinux. Android's arc4random_buf supplies entropy. */
static inline void uuid_generate(uuid_t out) {
 arc4random_buf(out, 16);
 out[6] = (out[6] & 0x0f) | 0x40;
 out[8] = (out[8] & 0x3f) | 0x80;
}
#endif
