package com.bytedance.rheatrace.processor;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Created by caodongping on 2023/2/8
 *
 * @author caodongping@bytedance.com
 */
public class AdbCommand {
    private static String sAdbPath;

    static {
        String path = System.getenv("PATH");
        String[] paths = path.split(":");
        for (String p : paths) {
            File file = new File(p, "adb");
            if (file.exists()) {
                sAdbPath = file.getAbsolutePath();
                Log.i("Got adb path: " + sAdbPath);
                break;
            }
        }
        if (sAdbPath == null) {
            Log.e("can not find adb from path " + path);
            throw new RuntimeException("adb not found");
        }
    }

    public static String callString(String... cmd) throws IOException, InterruptedException {
        StringWriter writer = new StringWriter();
        call(writer, cmd);
        return writer.toString();
    }

    public static void call(String... cmd) throws IOException, InterruptedException {
        call(null, cmd);
    }

    private static void call(Writer writer, String... cmd) throws IOException, InterruptedException {
        Log.i("Run adb " + StringUtils.join(cmd, " "));
        String[] array = new String[cmd.length + 1];
        array[0] = sAdbPath;
        System.arraycopy(cmd, 0, array, 1, cmd.length);
        Process process = Runtime.getRuntime().exec(array);
        for (String line : IOUtils.readLines(process.getInputStream(), StandardCharsets.UTF_8)) {
            if (writer != null) {
                writer.write(line);
                writer.write('\n');
            } else {
                Log.i(line);
            }
        }
        for (String error : IOUtils.readLines(process.getErrorStream(), StandardCharsets.UTF_8)) {
            Log.e(error);
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new RuntimeException("adb " + StringUtils.join(cmd, " ") + " return " + code);
        }
    }
}
