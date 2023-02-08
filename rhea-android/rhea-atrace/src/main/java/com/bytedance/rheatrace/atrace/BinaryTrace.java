package com.bytedance.rheatrace.atrace;

import android.content.Context;

import androidx.annotation.Keep;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by caodongping on 2022/9/27
 *
 * @author caodongping@bytedance.com
 */
@Keep
public class BinaryTrace {
    private static final String TAG = "BinaryTrace";
    private static volatile boolean on;

    public static void init(int size, File file) {
        on = true;
        nativeInit(size, file.getAbsolutePath());
    }


    private static boolean traceMethod(long mid, long type) {
        if (!on) {
            return false;
        }
        return nativeTraceMethod(mid, type);
    }

    public static void beginSection(int mid) {
        on = traceMethod(mid, 1);
    }


    public static void endSection(int mid) {
        on = traceMethod(mid, 0);
    }

    public static void interruptSection(int mid) {
        on = traceMethod(mid, 2);
    }

    public static void stop() {
        on = false;
        nativeStop();
        // TODO save bin and parse via python
        // TraceMaker.makeTrace(context, mappingProvider);
    }


    static native long nativeGetSize();

    static native long nativeGetMagic(long position);

    private static native void nativeInit(int size, String filepath);

    private static native void nativeStop();

    private static native boolean nativeTraceMethod(long mid, long type);
}
