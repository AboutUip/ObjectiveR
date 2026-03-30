package com.kitepromiss.obr.lex;

import com.kitepromiss.obr.ObrException;

/**
 * 将词法阶段产生的 {@code char} 字面量 lexeme（含成对单引号）解析为 Java {@code char}，供语义与运行时共用。
 */
public final class CharLiteralParser {

    private CharLiteralParser() {}

    /**
     * @param lexeme 形如 {@code 'a'} 或 {@code '\''}（与 {@link Lexer} 输出一致）
     */
    public static char parseCharLexeme(String lexeme) {
        if (lexeme == null || lexeme.length() < 2) {
            throw new ObrException("无效的 char 字面量: " + lexeme);
        }
        // 规范：`''` 与 `'\0'` 同值（U+0000），见 docs/obr/types.md、operators.md
        if (lexeme.length() == 2 && lexeme.charAt(0) == '\'' && lexeme.charAt(1) == '\'') {
            return '\0';
        }
        if (lexeme.length() < 3 || lexeme.charAt(0) != '\'' || lexeme.charAt(lexeme.length() - 1) != '\'') {
            throw new ObrException("无效的 char 字面量: " + lexeme);
        }
        String inner = lexeme.substring(1, lexeme.length() - 1);
        if (inner.charAt(0) != '\\') {
            if (inner.length() != 1) {
                throw new ObrException("char 字面量只能包含一个字符: " + lexeme);
            }
            return inner.charAt(0);
        }
        if (inner.length() < 2) {
            throw new ObrException("不完整的转义序列: " + lexeme);
        }
        return switch (inner.charAt(1)) {
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '0' -> '\0';
            default -> throw new ObrException("不支持的转义序列: " + lexeme);
        };
    }
}
