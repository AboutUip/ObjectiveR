package com.kitepromiss.obr.trace;

/**
 * 解释器日志粒度；策略级别越高，输出的解释过程越细。
 */
public enum TraceLevel {
    /** 关闭解释过程输出（错误仍走 stderr） */
    OFF,
    /** 仅阶段边界与结论 */
    SUMMARY,
    /** 常规可审查：启动、路径解析、阶段切换 */
    NORMAL,
    /** 词法逐记号等 */
    VERBOSE,
    /** 实现内部细节（预留） */
    ALL;

    public static TraceLevel parse(String s) {
        if (s == null || s.isBlank()) {
            return NORMAL;
        }
        return switch (s.trim().toLowerCase()) {
            case "off", "none", "0" -> OFF;
            case "summary", "brief", "1" -> SUMMARY;
            case "normal", "2" -> NORMAL;
            case "verbose", "debug", "3" -> VERBOSE;
            case "all", "trace", "4" -> ALL;
            default -> throw new IllegalArgumentException("未知 trace 级别: " + s);
        };
    }

    /** 当前策略是否输出该 {@code event} 级别的事件 */
    public boolean emits(TraceLevel event) {
        if (this == OFF) {
            return false;
        }
        return event.ordinal() <= this.ordinal();
    }
}
