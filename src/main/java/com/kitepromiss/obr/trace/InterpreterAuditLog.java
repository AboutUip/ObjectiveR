package com.kitepromiss.obr.trace;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 解释器可审查日志：单行、键值对、固定字段顺序，便于脚本与人工审计。
 */
public interface InterpreterAuditLog {

    /** 静默（测试或嵌入场景） */
    static InterpreterAuditLog silent() {
        return new InterpreterAuditLog() {
            @Override
            public void event(TraceLevel level, TraceCategory category, String phase, Map<String, String> fields) {
                // no-op
            }
        };
    }

    /** 默认：标准输出，UTF-8，带时间戳与分类 */
    static InterpreterAuditLog toStdout(TraceLevel policy) {
        return new DefaultInterpreterAuditLog(new PrintWriter(System.out, true, StandardCharsets.UTF_8), policy);
    }

    /**
     * @param phase  该步操作名（蛇形或点分，稳定枚举式字符串）
     * @param fields 键值对；值中的换行将被转义，保证单行输出
     */
    void event(TraceLevel level, TraceCategory category, String phase, Map<String, String> fields);

    default void event(TraceLevel level, TraceCategory category, String phase) {
        event(level, category, phase, Map.of());
    }

    final class DefaultInterpreterAuditLog implements InterpreterAuditLog {
        private final PrintWriter out;
        private final TraceLevel policy;

        DefaultInterpreterAuditLog(PrintWriter out, TraceLevel policy) {
            this.out = Objects.requireNonNull(out);
            this.policy = Objects.requireNonNull(policy);
        }

        @Override
        public synchronized void event(TraceLevel level, TraceCategory category, String phase, Map<String, String> fields) {
            if (!policy.emits(level)) {
                return;
            }
            StringBuilder sb = new StringBuilder(256);
            sb.append("ts=").append(Instant.now());
            sb.append(" level=").append(level.name().toLowerCase());
            sb.append(" cat=").append(category.name().toLowerCase());
            sb.append(" phase=").append(escape(phase));
            if (fields != null && !fields.isEmpty()) {
                for (Map.Entry<String, String> e : fields.entrySet()) {
                    sb.append(' ').append(e.getKey()).append('=').append(escape(e.getValue()));
                }
            }
            out.println(sb);
        }

        private static String escape(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("\\", "\\\\")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t");
        }
    }

    /** 便于调用方组装字段 */
    static Map<String, String> fields(String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("fields 须为成对 key,value");
        }
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
