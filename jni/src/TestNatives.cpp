#include <string>
#include <algorithm>
using std::string;
using std::reverse;

#include <jni.h>

static inline void testNatives1() {

}

static inline string testNatives2(string param) {
  reverse(param.begin(), param.end());
  return param;
}

static inline void testNatives3(float *param) {
  param[1] = 42.0f;
}

static inline void testNatives4(float *param) {
  param[1] = 42.0f;
}

static inline void testNatives5(unsigned char *param) {
  param[1] = 42;
}

#include "TestNatives.inc"

