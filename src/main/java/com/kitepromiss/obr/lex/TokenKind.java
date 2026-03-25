package com.kitepromiss.obr.lex;

/**
 * 词法记号类别（.obr / .mr 共用词法）。
 */
public enum TokenKind {
    EOF,

    IDENT,
    NUMBER,
    STRING_LITERAL,
    /** {@code 'a'}、{@code '\''} 等 char 字面量（含引号） */
    CHAR_LITERAL,
    /** 整行预处理指令文本（含起始 {@code #}，不含换行） */
    PREPROCESSOR_LINE,

    LBRACE,    // {
    RBRACE,    // }
    LPAREN,    // (
    RPAREN,    // )
    LBRACKET,  // [
    RBRACKET,  // ]
    SEMICOLON, // ;
    COMMA,     // ,
    PLUS,      // +
    /** 自增 {@code ++} */
    PLUS_PLUS,
    /** 复合加赋值 {@code +=} */
    PLUS_EQ,
    MINUS,     // -
    /** 自减 {@code --} */
    MINUS_MINUS,
    /** 复合减赋值 {@code -=} */
    MINUS_EQ,
    DOT,       // .
    SLASH,     // /
    /** 复合除赋值 {@code /=} */
    SLASH_EQ,
    STAR,      // *
    /** 复合乘赋值 {@code *=} */
    STAR_EQ,
    /** 幂运算符 {@code **}（双星），与单星 {@link #STAR} 区分 */
    STAR_STAR,
    PERCENT,   // %
    /** 复合取模赋值 {@code %=} */
    PERCENT_EQ,
    TILDE,     // ~
    BANG,      // !
    COLON,     // :
    DOUBLE_COLON, // ::
    AT,        // @
    /** 赋值 {@code =} */
    EQ,
    /** 相等 {@code ==} */
    EQ_EQ,
    /** 不等 {@code !=} */
    NE,
    LT,
    LE,
    GT,
    GE,
    AND_AND,
    OR_OR,
    QUESTION,

    // 关键字（保留字）
    PUBLIC, PRIVATE, STATIC,
    TRUE, FALSE, NULL, UNDEFINED,
    BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, CHAR, STRING_KW, BOOLEAN,
    VOID,
    DE_RFUN, IMPORT, NAMESPACE,
    RETURN,
    VAR,
    IF,
    ELSE,
}
