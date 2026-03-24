package com.kitepromiss.obr;

/**
 * 解释器可恢复错误（应打印到 stderr 并以非零码退出）。
 */
public final class ObrException extends RuntimeException {

    public ObrException(String message) {
        super(message);
    }

    /**
     * 从错误消息前缀提取标准错误码（如 {@code E_SEM_OVERLOAD_AMBIGUOUS}）；无则返回 {@code UNKNOWN}。
     */
    public static String extractErrorCode(String message) {
        if (message == null || message.isBlank()) {
            return "UNKNOWN";
        }
        int sp = message.indexOf(' ');
        String first = sp < 0 ? message : message.substring(0, sp);
        if (first.matches("^E_[A-Z0-9_]+$")) {
            return first;
        }
        return "UNKNOWN";
    }
}
