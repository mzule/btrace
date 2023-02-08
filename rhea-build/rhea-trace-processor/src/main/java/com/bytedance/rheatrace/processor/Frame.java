package com.bytedance.rheatrace.processor;

import java.util.Map;

public class Frame {
    public static final int B = 1;
    public static final int E = 0;

    int flag;
    int methodId;
    String method;
    long time;
    int threadId;
    int pid;
    boolean inPair;

    @Override
    public String toString() {
        return buf().trim() + "|time:" + time;
    }

    public Frame(long foo, long bar, int pid, Map<Integer, String> mapping) {
        flag = (int) (foo >>> 62);
        threadId = (int) ((foo >>> 46) & 0xFFFFL);
        methodId = (int) (foo & 0x3FFFFFFFFFFFL);
        method = mapping.get(methodId);
        time = bar;
        this.pid = pid;
    }

    private Frame() {
    }

    public int getMethodId() {
        return methodId;
    }

    public long getTime() {
        return time;
    }

    public int getThreadId() {
        return threadId;
    }

    public boolean isBegin() {
        return flag == B;
    }

    public boolean isEnd() {
        return flag == E;
    }

    public Frame duplicate() {
        Frame dup = new Frame();
        dup.flag = flag;
        dup.methodId = methodId;
        dup.time = time;
        dup.threadId = threadId;
        dup.method = method;
        dup.inPair = inPair;
        dup.pid = pid;
        Log.e("repair " + this);
        return dup;
    }

    public String buf() {
        return (flag == B ? "B|" : "E|") + threadId + "|" + method + "\n";
    }
}
