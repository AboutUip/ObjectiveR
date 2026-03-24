package com.kitepromiss.obr;

import com.kitepromiss.obr.trace.TraceLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令行参数：路径与追踪策略（其余解释器行为见 {@link com.kitepromiss.obr.trace}）。
 */
public record LaunchArgs(Path input, TraceLevel tracePolicy) {

    /**
     * 支持：{@code --trace=verbose}、{@code --trace verbose}、{@code -v}、{@code -q}；首个非选项参数为入口路径。
     * 未指定任何 trace 相关选项时，策略为 {@link TraceLevel#OFF}（无解释过程审计输出；错误仍由 stderr 处理）。
     */
    public static LaunchArgs parse(String[] args) {
        TraceLevel trace = TraceLevel.OFF;
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--trace".equals(a) && i + 1 < args.length) {
                trace = TraceLevel.parse(args[++i]);
            } else if (a.startsWith("--trace=")) {
                trace = TraceLevel.parse(a.substring("--trace=".length()));
            } else if ("-v".equals(a) || "--verbose".equals(a)) {
                trace = TraceLevel.VERBOSE;
            } else if ("-q".equals(a) || "--quiet".equals(a)) {
                trace = TraceLevel.OFF;
            } else if (a.startsWith("-")) {
                throw new ObrException("未知参数: " + a + "（可用 --trace=off|summary|normal|verbose|all，或 -v / -q）");
            } else {
                positional.add(a);
            }
        }
        Path input = positional.isEmpty() ? Path.of(".") : Path.of(positional.get(0));
        return new LaunchArgs(input, trace);
    }
}
