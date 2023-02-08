package com.bytedance.rheatrace.processor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Created by caodongping on 2023/2/9
 *
 * @author caodongping@bytedance.com
 */
public class Log {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    @SuppressWarnings("SimpleDateFormat")
    private static final DateFormat FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    public static void d(String msg) {
        log('D', ANSI_BLACK, msg);
    }

    public static void i(String msg) {
        log('I', ANSI_BLUE, msg);
    }

    public static void e(String msg) {
        log('E', ANSI_RED, msg);
    }

    public static void w(String msg) {
        log('W', ANSI_RED, msg);
    }

    public static void log(char level, String color, String msg) {
        System.out.println(color + FORMAT.format(System.currentTimeMillis()) + " " + level + " RheaTrace : " + msg + ANSI_RESET);
    }
}
