package com.bytedance.rheatrace.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by caodongping on 2023/2/8
 *
 * @author caodongping@bytedance.com
 */
public class FrameFixer {
    public static List<Frame> fix(List<Frame> raw) {
        // 逆序遍历，找到落单的 Frame
        for (int i = raw.size() - 1; i >= 0; i--) {
            Frame current = raw.get(i);
            if (current.inPair || current.isBegin()) {
                continue;
            }
            // 我们从没成对的 end 出发，往前回溯
            int depth = 0;
            int j = i - 1;
            while (j >= 0) {
                Frame pre = raw.get(j);
                j--;
                // 如果已经成对
                if (pre.inPair) {
                    continue;
                }
                if (pre.methodId == current.methodId)
                    // 如果是同名 begin，那就是成对了
                    if (pre.isBegin()) {
                        // 判断一下depth
                        if (depth == 0) {
                            pre.inPair = current.inPair = true;
                            break;
                        } else {
                            depth--;
                        }
                    } else {
                        // 如果是同名的 end，那就得。。。多找一个 begin
                        depth++;
                    }
            }
        }
        // 给落单的 Frame 分配`对象
        long firstTime = raw.get(0).time;
        Stack<Frame> head = new Stack<>();
        List<Frame> ret = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Frame current = raw.get(i);
            if (current.inPair) {
                ret.add(current);
            } else if (current.isBegin()) {
                Frame next = i + 1 < raw.size() ? raw.get(i + 1) : null;
                Frame end = current.duplicate();
                end.flag = Frame.E;
                end.time = next == null ? end.time : next.time;
                ret.add(current);
                ret.add(end);
                current.inPair = end.inPair = true;
            } else if (current.isEnd()) {
                Frame begin = current.duplicate();
                begin.flag = Frame.B;
                begin.time = firstTime;
                head.push(begin);
                ret.add(current);
                current.inPair = begin.inPair = true;
            }
        }
        List<Frame> result = new ArrayList<>();
        while (!head.isEmpty()) {
            result.add(head.pop());
        }
        result.addAll(ret);
        return result;
    }
}
