/*
 * Copyright (C) 2021 ByteDance Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <fcntl.h>
#include <jni.h>
#include <sys/mman.h>
#include <atomic>
#include <unistd.h>
#include <utils/timers.h>
#include <debug.h>
#include <cerrno>
#include <cstdio>
#include <string>

static int bufferFd_;
static uint64_t *buffer_;
static off_t bufferSize_;
static uint32_t bufferItemCount_;
static std::atomic_int64_t next_(0);

static jboolean JNI_TraceMethod(JNIEnv *env, jclass, jlong mid, jlong type) {
    LOG_ALWAYS_FATAL_IF(mid < 0 || type < 0, "TraceMethod invalid args mid:%ld type:%ld", mid, type);
    int64_t next = next_.fetch_add(2, std::memory_order_relaxed);
    if (0 == (next % 1000000)) {
        ALOGI("put item %ld id=%ld type=%ld limit=%u", next, mid, type, bufferItemCount_);
    }
    if (next < bufferItemCount_) {
        int64_t tid = gettid();
        uint64_t left = (type << 62L) | (tid << 46L) | mid;
        buffer_[next] = left;
        buffer_[next + 1] = elapsedRealtimeNanos();
        return true;
    } else {
        return false;
    }
}

static jlong JNI_GetMagic(JNIEnv *env, jclass, jlong position) {
    if (position > bufferItemCount_) {
        ALOGE("rhea getmagic overflow %ld", position);
    }
    return (jlong) buffer_[position];
}

static JNINativeMethod methods[] = {
        {
                "nativeTraceMethod",
                "(JJ)Z",
                (void *) JNI_TraceMethod
        },
        {
                "nativeGetMagic",
                "(J)J",
                (void *) JNI_GetMagic
        }
};

static int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods, int numMethods) {

    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == nullptr) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_bytedance_rheatrace_atrace_BinaryTrace_nativeInit(JNIEnv *env, jclass clazz, jint maxMethodCount, jstring filepath) {
    auto *path = env->GetStringUTFChars(filepath, nullptr);
    bufferFd_ = open(path, O_RDWR | O_CREAT, (mode_t) 0600);
    LOG_ALWAYS_FATAL_IF(bufferFd_ < 0, "open file error %s %s", path, strerror(errno));
    env->ReleaseStringUTFChars(filepath, path);

    bufferItemCount_ = maxMethodCount;
    bufferSize_ = (off_t) (maxMethodCount * 2 * sizeof(uint64_t));
    ftruncate(bufferFd_, bufferSize_);
    buffer_ = (uint64_t *) mmap(nullptr, bufferSize_, PROT_READ | PROT_WRITE, MAP_SHARED, bufferFd_, 0);
    LOG_ALWAYS_FATAL_IF(buffer_ == MAP_FAILED, "mmap failed %s", strerror(errno));
    next_.fetch_add(2);
    buffer_[0] = 1; // version
    buffer_[1] = getpid();
    ALOGI("Binary Trace init with size %ld(%u) %p", bufferSize_, bufferItemCount_, buffer_);

    // TODO make nativeTrace critical
    registerNativeMethods(env, "com/bytedance/rheatrace/atrace/BinaryTrace", methods, 2);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_bytedance_rheatrace_atrace_BinaryTrace_nativeStop(JNIEnv *env, jclass clazz) {
    int64_t size = next_.load(std::memory_order_relaxed);
    size_t writeSize = size * sizeof(int64_t);
    msync(buffer_, writeSize, MS_SYNC);
    munmap(buffer_, bufferSize_);
    ftruncate(bufferFd_, (off_t) writeSize);
    close(bufferFd_);
    ALOGD("Binary Trace stop with size %ld", size);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_bytedance_rheatrace_atrace_BinaryTrace_nativeGetSize(JNIEnv *env, jclass clazz) {
    jlong size = next_.load(std::memory_order_relaxed);
    ALOGI("bin rhea size: %ld", size);
    return size;
}
