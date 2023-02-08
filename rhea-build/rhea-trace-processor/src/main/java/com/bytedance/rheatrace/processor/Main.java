package com.bytedance.rheatrace.processor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Created by caodongping on 2023/2/8
 *
 * @author caodongping@bytedance.com
 */
public class Main {
    private static File workspace;
    private static String appName;
    private static String outputPath;
    private static int timeInSeconds;
    private static String mappingPath;

    public static void main(String[] args) throws Exception {
        workspace = new File(".rheatrace.workspace");
        FileUtils.cleanDirectory(workspace);
        if (!workspace.exists() && !workspace.mkdirs()) {
            throw new IOException("can not create dir " + workspace);
        }
        Log.i("workspace clear: " + workspace.getAbsolutePath());

        resolveArgs(args);
        Process process = runTrace(args);
        Thread.sleep(500);
        AdbCommand.call("shell", "am", "force-stop", appName);
        AdbCommand.call("shell", "am", "start", "-n", getActivityLauncher());

        Log.d("******");
        for (String line : IOUtils.readLines(process.getInputStream(), StandardCharsets.UTF_8)) {
            Log.i(line);
        }
        for (String line : IOUtils.readLines(process.getErrorStream(), StandardCharsets.UTF_8)) {
            Log.e(line);
        }
        Log.d("******");
        int code = process.waitFor();
        if (code < 0) {
            throw new RuntimeException("bad code " + code);
        }
        AdbCommand.call("shell", "am", "broadcast", "-a", "com.bytedance.rheatrace.switch.stop", appName);
        new TraceProcessor(workspace, appName, mappingPath).process(outputPath);
    }

    private static String getActivityLauncher() throws IOException, InterruptedException {
        String dump = AdbCommand.callString("shell", "dumpsys", "package", appName);
        String launcher = null;
        StringTokenizer tokenizer = new StringTokenizer(dump, "\n");
        while (tokenizer.hasMoreTokens()) {
            String next = tokenizer.nextToken();
            if (next.trim().equals("android.intent.action.MAIN:")) {
                String line = tokenizer.nextToken().trim();
                // b151760 rhea.sample.android/.app.MainActivity filter 95a6419
                int first = line.indexOf(" ");
                int second = line.indexOf(" ", first + 1);
                launcher = line.substring(first, second);
            }
        }
        return launcher;
    }

    private static void resolveArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    outputPath = args[i + 1];
                    break;
                case "-a":
                    appName = args[i + 1];
                    break;
                case "-t":
                    String time = args[i + 1];
                    if (time.endsWith("s")) {
                        timeInSeconds = Integer.parseInt(time.substring(0, time.length() - 1));
                    } else {
                        timeInSeconds = Integer.parseInt(time);
                        args[i + 1] = timeInSeconds + "s";
                    }
                    break;
                case "-m":
                    mappingPath = args[i + 1];
                    break;
            }
        }
        if (appName == null) {
            throw new RuntimeException("missing -a $appName");
        }
        if (outputPath == null) {
            throw new RuntimeException("missing -o $output");
        }
        if (timeInSeconds == 0) {
            throw new RuntimeException("missing -t $seconds");
        }
        if (mappingPath == null) {
            throw new RuntimeException("missing -m $mappingPath");
        }
    }

    private static Process runTrace(String[] args) throws IOException {
        File trace = new File(workspace, "record_android_trace");
        try (InputStream perfetto = TraceProcessor.class.getResourceAsStream("/record_android_trace")) {
            assert perfetto != null;
            IOUtils.copy(perfetto, new FileOutputStream(trace));
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(trace.toPath(), perms);
        }
        Log.i("record_android_trace ready: " + trace.getAbsolutePath());
        List<String> cmd = new ArrayList<>();
        cmd.add(trace.getAbsolutePath());
        cmd.add("-no_open");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-m")) {
                i++;
            } else {
                cmd.add(args[i]);
            }
        }
        Log.i(StringUtils.join(cmd, " "));
        return Runtime.getRuntime().exec(cmd.toArray(new String[0]));
    }
}
