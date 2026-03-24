package com.kitepromiss.obr.parse;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.lex.Token;
import com.kitepromiss.obr.lex.TokenKind;

import java.util.List;

public final class TokenCursor {

    private final List<Token> tokens;
    private int pos;

    public TokenCursor(List<Token> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public Token peek() {
        if (pos >= tokens.size()) {
            throw new ObrException("词法序列越界");
        }
        return tokens.get(pos);
    }

    /** 向前看 {@code offset} 个记号（0 同 {@link #peek}），不越过 EOF。 */
    public Token peekToken(int offset) {
        int i = pos + offset;
        if (i < 0) {
            throw new ObrException("词法序列越界");
        }
        if (i >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(i);
    }

    public boolean check(TokenKind k) {
        return peek().kind() == k;
    }

    public Token advance() {
        Token t = peek();
        pos++;
        return t;
    }

    public boolean eof() {
        return check(TokenKind.EOF);
    }

    public void expect(TokenKind k, String fileName) {
        if (!check(k)) {
            Token t = peek();
            throw new ObrException(
                    fileName + ":" + t.line() + ":" + t.column() + ": 期望 " + k + "，实际 " + t.kind());
        }
        advance();
    }
}
