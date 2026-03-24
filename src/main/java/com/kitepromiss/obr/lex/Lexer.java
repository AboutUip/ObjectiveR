package com.kitepromiss.obr.lex;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.trace.InterpreterAuditLog;
import com.kitepromiss.obr.trace.TraceCategory;
import com.kitepromiss.obr.trace.TraceLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单遍扫描词法器；.obr 与 .mr 共用（语法阶段再区分）。
 */
public final class Lexer {

    private static final Map<String, TokenKind> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("public", TokenKind.PUBLIC);
        KEYWORDS.put("private", TokenKind.PRIVATE);
        KEYWORDS.put("static", TokenKind.STATIC);
        KEYWORDS.put("true", TokenKind.TRUE);
        KEYWORDS.put("false", TokenKind.FALSE);
        KEYWORDS.put("null", TokenKind.NULL);
        KEYWORDS.put("undefined", TokenKind.UNDEFINED);
        KEYWORDS.put("byte", TokenKind.BYTE);
        KEYWORDS.put("short", TokenKind.SHORT);
        KEYWORDS.put("int", TokenKind.INT);
        KEYWORDS.put("long", TokenKind.LONG);
        KEYWORDS.put("float", TokenKind.FLOAT);
        KEYWORDS.put("double", TokenKind.DOUBLE);
        KEYWORDS.put("char", TokenKind.CHAR);
        KEYWORDS.put("string", TokenKind.STRING_KW);
        KEYWORDS.put("boolean", TokenKind.BOOLEAN);
        KEYWORDS.put("void", TokenKind.VOID);
        KEYWORDS.put("deRfun", TokenKind.DE_RFUN);
        KEYWORDS.put("import", TokenKind.IMPORT);
        KEYWORDS.put("namespace", TokenKind.NAMESPACE);
        KEYWORDS.put("return", TokenKind.RETURN);
        KEYWORDS.put("var", TokenKind.VAR);
    }

    private final String source;
    private final String fileName;
    private final int len;
    private int pos;
    private int line = 1;
    private int column = 1;
    /** 下一有效记号是否位于「行首非空白之前」语义：刚越过换行或文件开头 */
    private boolean lineBegin = true;

    private final InterpreterAuditLog audit;

    public Lexer(String source, String fileName) {
        this(source, fileName, InterpreterAuditLog.silent());
    }

    public Lexer(String source, String fileName, InterpreterAuditLog audit) {
        this.source = source;
        this.fileName = fileName;
        this.len = source.length();
        this.audit = audit;
    }

    /** 扫描至 EOF，供语法阶段使用（避免重复实现词法状态机）。 */
    public static List<Token> readAllTokens(String source, String fileName, InterpreterAuditLog audit) {
        Lexer lexer = new Lexer(source, fileName, audit);
        List<Token> list = new ArrayList<>();
        while (true) {
            Token t = lexer.nextToken();
            list.add(t);
            if (t.kind() == TokenKind.EOF) {
                break;
            }
        }
        return List.copyOf(list);
    }

    public Token nextToken() {
        while (true) {
            skipWhitespace();
            if (pos >= len) {
                return emit(token(TokenKind.EOF, "", line, column));
            }
            if (lineBegin && peek() == '#') {
                return emit(readPreprocessorLine());
            }
            if (peek() == '/' && peekAhead(1) == '/') {
                skipLineComment();
                continue;
            }
            if (peek() == '/' && peekAhead(1) == '*') {
                skipBlockComment();
                continue;
            }
            break;
        }

        markTokenStart();
        char c = peek();
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            throw new AssertionError("skipWhitespace left whitespace");
        }

        lineBegin = false;

        if (isIdentStart(c)) {
            return emit(readIdentifierOrKeyword());
        }
        if (isDigit(c)) {
            return emit(readNumber());
        }
        if (c == '"') {
            return emit(readString());
        }
        if (c == '\'') {
            return emit(readCharLiteral());
        }
        if (c == '@') {
            advance();
            return emit(token(TokenKind.AT, "@", tokenLine, tokenColumn));
        }

        return switch (c) {
            case '{' -> {
                advance();
                yield emit(token(TokenKind.LBRACE, "{", tokenLine, tokenColumn));
            }
            case '}' -> {
                advance();
                yield emit(token(TokenKind.RBRACE, "}", tokenLine, tokenColumn));
            }
            case '(' -> {
                advance();
                yield emit(token(TokenKind.LPAREN, "(", tokenLine, tokenColumn));
            }
            case ')' -> {
                advance();
                yield emit(token(TokenKind.RPAREN, ")", tokenLine, tokenColumn));
            }
            case '[' -> {
                advance();
                yield emit(token(TokenKind.LBRACKET, "[", tokenLine, tokenColumn));
            }
            case ']' -> {
                advance();
                yield emit(token(TokenKind.RBRACKET, "]", tokenLine, tokenColumn));
            }
            case ';' -> {
                advance();
                yield emit(token(TokenKind.SEMICOLON, ";", tokenLine, tokenColumn));
            }
            case ',' -> {
                advance();
                yield emit(token(TokenKind.COMMA, ",", tokenLine, tokenColumn));
            }
            case '+' -> {
                advance();
                if (peek() == '+') {
                    advance();
                    yield emit(token(TokenKind.PLUS_PLUS, "++", tokenLine, tokenColumn));
                }
                if (peek() == '=') {
                    advance();
                    yield emit(token(TokenKind.PLUS_EQ, "+=", tokenLine, tokenColumn));
                }
                yield emit(token(TokenKind.PLUS, "+", tokenLine, tokenColumn));
            }
            case '-' -> {
                advance();
                if (peek() == '-') {
                    advance();
                    yield emit(token(TokenKind.MINUS_MINUS, "--", tokenLine, tokenColumn));
                }
                if (peek() == '=') {
                    advance();
                    yield emit(token(TokenKind.MINUS_EQ, "-=", tokenLine, tokenColumn));
                }
                yield emit(token(TokenKind.MINUS, "-", tokenLine, tokenColumn));
            }
            case '%' -> {
                advance();
                if (peek() == '=') {
                    advance();
                    yield emit(token(TokenKind.PERCENT_EQ, "%=", tokenLine, tokenColumn));
                }
                yield emit(token(TokenKind.PERCENT, "%", tokenLine, tokenColumn));
            }
            case '~' -> {
                advance();
                yield emit(token(TokenKind.TILDE, "~", tokenLine, tokenColumn));
            }
            case '.' -> {
                advance();
                yield emit(token(TokenKind.DOT, ".", tokenLine, tokenColumn));
            }
            case '/' -> {
                advance();
                if (peek() == '=') {
                    advance();
                    yield emit(token(TokenKind.SLASH_EQ, "/=", tokenLine, tokenColumn));
                }
                yield emit(token(TokenKind.SLASH, "/", tokenLine, tokenColumn));
            }
            case '*' -> {
                advance();
                if (peek() == '*') {
                    advance();
                    yield emit(token(TokenKind.STAR_STAR, "**", tokenLine, tokenColumn));
                }
                if (peek() == '=') {
                    advance();
                    yield emit(token(TokenKind.STAR_EQ, "*=", tokenLine, tokenColumn));
                }
                yield emit(token(TokenKind.STAR, "*", tokenLine, tokenColumn));
            }
            case '!' -> {
                advance();
                yield emit(token(TokenKind.BANG, "!", tokenLine, tokenColumn));
            }
            case ':' -> {
                advance();
                if (peek() == ':') {
                    advance();
                    yield emit(token(TokenKind.DOUBLE_COLON, "::", tokenLine, tokenColumn));
                }
                yield emit(token(TokenKind.COLON, ":", tokenLine, tokenColumn));
            }
            case '=' -> {
                advance();
                yield emit(token(TokenKind.EQ, "=", tokenLine, tokenColumn));
            }
            default -> throw err("无法识别的字符: '" + c + "' (U+" + String.format("%04X", (int) c) + ")");
        };
    }

    private Token emit(Token t) {
        audit.event(
                TraceLevel.VERBOSE,
                TraceCategory.LEX,
                "token",
                InterpreterAuditLog.fields(
                        "file", fileName,
                        "kind", t.kind().name(),
                        "lexeme", t.lexeme(),
                        "line", Integer.toString(t.line()),
                        "col", Integer.toString(t.column())));
        return t;
    }

    private int tokenLine = 1;
    private int tokenColumn = 1;

    private void markTokenStart() {
        tokenLine = line;
        tokenColumn = column;
    }

    private Token readPreprocessorLine() {
        markTokenStart();
        int start = pos;
        while (pos < len) {
            char c = peek();
            if (c == '\r' || c == '\n') {
                break;
            }
            advance();
        }
        String lex = source.substring(start, pos);
        lineBegin = true;
        return token(TokenKind.PREPROCESSOR_LINE, lex, tokenLine, tokenColumn);
    }

    private Token readIdentifierOrKeyword() {
        int start = pos;
        while (pos < len && isIdentPart(peek())) {
            advance();
        }
        String lex = source.substring(start, pos);
        TokenKind k = KEYWORDS.get(lex);
        if (k != null) {
            return token(k, lex, tokenLine, tokenColumn);
        }
        return token(TokenKind.IDENT, lex, tokenLine, tokenColumn);
    }

    private Token readNumber() {
        int start = pos;
        while (pos < len && isDigit(peek())) {
            advance();
        }
        if (peek() == '.') {
            advance();
            if (pos >= len || !isDigit(peek())) {
                throw err("数字字面量中小数点后必须有数字");
            }
            while (pos < len && isDigit(peek())) {
                advance();
            }
        }
        if (peek() == 'F' || peek() == 'f') {
            advance();
        } else if (peek() == 'D' || peek() == 'd') {
            advance();
        } else if (peek() == 'L' || peek() == 'l') {
            advance();
        }
        String lex = source.substring(start, pos);
        return token(TokenKind.NUMBER, lex, tokenLine, tokenColumn);
    }

    private Token readString() {
        advance(); // "
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        while (pos < len) {
            char c = peek();
            if (c == '"') {
                advance();
                sb.append('"');
                String lex = sb.toString();
                return token(TokenKind.STRING_LITERAL, lex, tokenLine, tokenColumn);
            }
            if (c == '\r' || c == '\n') {
                throw err("字符串字面量未闭合");
            }
            advance();
            sb.append(c);
        }
        throw err("字符串字面量未闭合");
    }

    private Token readCharLiteral() {
        markTokenStart();
        int start = pos;
        advance(); // opening '
        if (pos >= len) {
            throw err("char 字面量未闭合");
        }
        if (peek() == '\\') {
            advance();
            if (pos >= len) {
                throw err("char 字面量未闭合");
            }
            advance();
        } else {
            char ch = peek();
            if (ch == '\'' || ch == '\n' || ch == '\r') {
                throw err("无效的 char 字面量");
            }
            advance();
        }
        if (pos >= len || peek() != '\'') {
            throw err("char 字面量过长或未闭合");
        }
        advance();
        String lex = source.substring(start, pos);
        try {
            CharLiteralParser.parseCharLexeme(lex);
        } catch (ObrException e) {
            throw err(e.getMessage());
        }
        return token(TokenKind.CHAR_LITERAL, lex, tokenLine, tokenColumn);
    }

    private void skipLineComment() {
        while (pos < len) {
            char c = peek();
            if (c == '\r' || c == '\n') {
                break;
            }
            advance();
        }
    }

    private void skipBlockComment() {
        advance(); // /
        advance(); // *
        while (pos + 1 < len) {
            if (peek() == '*' && peekAhead(1) == '/') {
                advance();
                advance();
                return;
            }
            if (peek() == '\r' || peek() == '\n') {
                handleNewline();
            } else {
                advance();
            }
        }
        throw err("块注释未闭合");
    }

    private void skipWhitespace() {
        while (pos < len) {
            char c = peek();
            if (c == ' ' || c == '\t') {
                // 行首缩进空格不得清除 lineBegin，否则 "   #VERSION" 无法识别预处理行
                advance();
            } else if (c == '\r' || c == '\n') {
                handleNewline();
            } else {
                break;
            }
        }
    }

    private void handleNewline() {
        if (peek() == '\r') {
            advance();
            if (peek() == '\n') {
                advance();
            }
        } else {
            advance();
        }
        lineBegin = true;
    }

    private char peek() {
        if (pos >= len) {
            return '\0';
        }
        return source.charAt(pos);
    }

    private char peekAhead(int delta) {
        int i = pos + delta;
        if (i >= len) {
            return '\0';
        }
        return source.charAt(i);
    }

    private void advance() {
        if (pos >= len) {
            return;
        }
        char c = source.charAt(pos++);
        if (c == '\n') {
            line++;
            column = 1;
        } else if (c != '\r') {
            column++;
        }
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static Token token(TokenKind kind, String lexeme, int line, int col) {
        return new Token(kind, lexeme, line, col);
    }

    private ObrException err(String msg) {
        return new ObrException(fileName + ":" + line + ":" + column + ": " + msg);
    }
}
