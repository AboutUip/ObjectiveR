package com.kitepromiss.obr.lex;

/**
 * @param line   1-based 行号
 * @param column 1-based 列号（记号起始）
 */
public record Token(TokenKind kind, String lexeme, int line, int column) {

    @Override
    public String toString() {
        return kind + " \"" + lexeme + "\" @" + line + ":" + column;
    }
}
