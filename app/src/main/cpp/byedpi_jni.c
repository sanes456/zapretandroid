#include <jni.h>
#include <getopt.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <android/log.h>

#include "params.h"

#define LOG_TAG "byedpi-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern struct params params;
extern int server_fd;
extern int main(int argc, char **argv);

static void reset_state(void)
{
    memset(&params, 0, sizeof(params));

    params.await_int = 10;
    params.cache_ttl = 100800;
    params.ipv6 = true;
    params.resolve = true;
    params.udp = true;
    params.max_open = 512;
    params.bfsize = 16384;
    params.baddr.sa.sa_family = AF_INET6;
    params.laddr.in.sin_family = AF_INET;

    optind = 1;
}

JNIEXPORT jint JNICALL
Java_com_zapret_android_core_ByeDpiProxy_nativeRun(JNIEnv *env, jobject thiz, jobjectArray args)
{
    (void) thiz;

    jsize argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    if (!argv) {
        LOGE("out of memory");
        return -2;
    }

    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *cs = (*env)->GetStringUTFChars(env, s, 0);
        argv[i] = strdup(cs);
        (*env)->ReleaseStringUTFChars(env, s, cs);
        (*env)->DeleteLocalRef(env, s);
    }

    reset_state();

    LOGI("starting ciadpi, %d args", (int) argc);
    for (jsize i = 0; i < argc; i++) {
        LOGI("  argv[%d] = %s", (int) i, argv[i]);
    }

    int rc = main((int) argc, argv);
    LOGI("ciadpi exited, rc=%d", rc);

    if (server_fd > 0) {
        close(server_fd);
        server_fd = 0;
    }

    for (jsize i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);

    return rc;
}

JNIEXPORT void JNICALL
Java_com_zapret_android_core_ByeDpiProxy_nativeStop(JNIEnv *env, jobject thiz)
{
    (void) env;
    (void) thiz;

    LOGI("nativeStop: server_fd=%d", server_fd);
    if (server_fd > 0) {
        shutdown(server_fd, SHUT_RDWR);
    }
}
