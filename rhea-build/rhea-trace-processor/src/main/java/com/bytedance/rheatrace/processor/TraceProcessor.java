package com.bytedance.rheatrace.processor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import perfetto.protos.Ftrace;
import perfetto.protos.FtraceEventBundleOuterClass;
import perfetto.protos.FtraceEventOuterClass;
import perfetto.protos.TraceOuterClass;
import perfetto.protos.TracePacketOuterClass;

public class TraceProcessor {
    protected final File workspace;
    private final Map<Integer, String> mapping;
    private final String appName;

    public TraceProcessor(File workspace, String appName, String mappingPath) throws IOException {
        this.workspace = workspace;
        mapping = loadMapping(mappingPath);
        this.appName = appName;
    }

    public void process(String baseTracePath) throws IOException, InterruptedException {
        File baseTrace = new File(baseTracePath);
        TraceOuterClass.Trace traceApp = TraceOuterClass.Trace.parseFrom(new FileInputStream(baseTrace));
//        TraceOuterClass.Trace.Builder trace = TraceOuterClass.Trace.newBuilder();
        TraceOuterClass.Trace.Builder trace = traceApp.toBuilder();
        long perfettoCalibrationTime = getPerfettoCalibrationTime(trace);
        // 2279838054080771:B:rhea.time.new
        // 2279838054085615:E:rhea.time.new
        List<Frame> binaryFrame = FrameFixer.fix(processBinary(perfettoCalibrationTime));
        FtraceEventBundleOuterClass.FtraceEventBundle.Builder events = FtraceEventBundleOuterClass.FtraceEventBundle.newBuilder().setCpu(0);
        for (Frame frame : binaryFrame) {
            events.addEvent(
                    FtraceEventOuterClass.FtraceEvent.newBuilder()
                            .setPid(frame.pid)
                            .setTimestamp(frame.time)
                            .setPrint(Ftrace.PrintFtraceEvent.newBuilder()
                                    .setBuf(frame.buf())));
        }
        TracePacketOuterClass.TracePacket.Builder builder = TracePacketOuterClass.TracePacket.newBuilder().setFtraceEvents(events);
        trace.addPacket(builder);
        try (FileOutputStream out = new FileOutputStream(baseTrace)) {
            Log.i("write to:" + baseTrace.getAbsolutePath());
            trace.build().writeTo(out);
        }

    }

    private long getPerfettoCalibrationTime(TraceOuterClass.Trace.Builder trace) {
        // time calibration
        for (TracePacketOuterClass.TracePacket tracePacket : trace.getPacketList()) {
            if (tracePacket.getDataCase() == TracePacketOuterClass.TracePacket.DataCase.FTRACE_EVENTS) {
                for (FtraceEventOuterClass.FtraceEvent ftraceEvent : tracePacket.getFtraceEvents().getEventList()) {
                    Ftrace.PrintFtraceEvent print = ftraceEvent.getPrint();
                    if (print.getBuf().contains("calibration")) {
                        long time = ftraceEvent.getTimestamp();
                        Log.i("System calibration time: " + time);
                        return time;
                    }
                }
            }
        }
        throw new RuntimeException("System calibration time is missing");
    }

    @SuppressWarnings("SdCardPath")
    private List<Frame> processBinary(long calibrationTime) throws IOException, InterruptedException {
        List<Frame> result = new ArrayList<>();
        AdbCommand.call("pull", "/sdcard/rhea-trace/" + appName + "/rhea-atrace.bin", workspace.getAbsolutePath());
        File bin = new File(workspace, "rhea-atrace.bin");
        byte[] bytes = FileUtils.readFileToByteArray(bin);
        LongBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
        buffer.get(); // version
        int pid = (int) buffer.get();
        while (buffer.hasRemaining()) {
            long id = buffer.get();
            if (!buffer.hasRemaining()) {
                break;
            }
            long bar = buffer.get();
            Frame frame = new Frame(id, bar, pid, mapping);
            result.add(frame);
        }
        long appCalibrationTime = -1;
        for (Frame frame : result) {
            if (frame.methodId == 0) {
                appCalibrationTime = frame.time;
                Log.i("App calibration time: " + appCalibrationTime);
                break;
            }
        }
        if (appCalibrationTime == -1) {
            throw new RuntimeException("app calibration time not found");
        }
        long diff = calibrationTime - appCalibrationTime + 1;
        for (Frame frame : result) {
            frame.time += diff;
        }
        Log.i("App process id " + result.get(0).pid);
        return result;
    }

    private static Map<Integer, String> loadMapping(String mappingPath) throws IOException {
        Map<Integer, String> mapping = new HashMap<>();
        for (LineIterator it = FileUtils.lineIterator(new File(mappingPath)); it.hasNext(); ) {
            // 1,17,rhea.sample.android.app.SecondFragment$onViewCreated$1 onClick (Landroid/view/View;)V
            String line = it.next();
            int first = line.indexOf(',');
            int second = line.indexOf(',', first + 1);
            String[] comps  = line.substring(second + 1).split(" ");
            String className = comps[0];
            String methodName = comps[1];
            mapping.put(Integer.parseInt(line.substring(0, first)), className + ":" + methodName);
        }
        return mapping;
    }

}