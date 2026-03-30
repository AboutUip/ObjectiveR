package com.kitepromiss.obr.parse;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.BlockStmt;
import com.kitepromiss.obr.ast.CallExpr;
import com.kitepromiss.obr.ast.Expr;
import com.kitepromiss.obr.ast.MrFile;
import com.kitepromiss.obr.ast.MrItem;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.ast.ObrItem;
import com.kitepromiss.obr.ast.ParamDecl;
import com.kitepromiss.obr.ast.QualifiedName;
import com.kitepromiss.obr.ast.Stmt;
import com.kitepromiss.obr.ast.TypeRef;
import com.kitepromiss.obr.ast.Attribute;
import com.kitepromiss.obr.ast.VarDeclarator;
import com.kitepromiss.obr.ast.VarVisibility;
import com.kitepromiss.obr.lex.Token;
import com.kitepromiss.obr.lex.TokenKind;

import java.util.ArrayList;
import java.util.List;

public final class Parser {

    private final String fileName;
    private final TokenCursor cur;

    public Parser(String fileName, List<Token> tokens) {
        this.fileName = fileName;
        this.cur = new TokenCursor(tokens);
    }

    public ObrFile parseObrFile() {
        List<ObrItem> items = new ArrayList<>();
        while (!cur.eof()) {
            if (cur.check(TokenKind.PREPROCESSOR_LINE)) {
                items.add(new ObrItem.Preproc(cur.advance().lexeme()));
            } else if (cur.check(TokenKind.IMPORT)) {
                items.add(parseImport());
            } else if (cur.check(TokenKind.DE_RFUN)) {
                items.add(parseObrDeRfunDef());
            } else {
                throw err("期望预处理、import 或 deRfun，实际为 " + cur.peek().kind());
            }
        }
        cur.expect(TokenKind.EOF, fileName);
        return new ObrFile(items);
    }

    public MrFile parseMrFile() {
        List<MrItem> items = new ArrayList<>();
        while (!cur.eof()) {
            if (cur.check(TokenKind.NAMESPACE)) {
                items.add(parseMrNamespace(new ArrayList<>()));
            } else {
                List<Attribute> attrs = parseAttributes();
                expect(TokenKind.DE_RFUN);
                items.add(parseMrDeRfunDecl(attrs, List.of()));
            }
        }
        cur.expect(TokenKind.EOF, fileName);
        return new MrFile(items);
    }

    private ObrItem.Import parseImport() {
        expect(TokenKind.IMPORT);
        String mod = expectIdent();
        expect(TokenKind.SEMICOLON);
        return new ObrItem.Import(mod);
    }

    private ObrItem.DeRfunDef parseObrDeRfunDef() {
        expect(TokenKind.DE_RFUN);
        QualifiedName name = parseQualifiedName();
        expect(TokenKind.LPAREN);
        List<ParamDecl> params = parseParamList();
        expect(TokenKind.RPAREN);
        expect(TokenKind.COLON);
        TypeRef ret = parseType();
        BlockStmt body = parseBlock(ret);
        return new ObrItem.DeRfunDef(name, params, ret, body);
    }

    private MrItem.Namespace parseMrNamespace(List<String> stack) {
        expect(TokenKind.NAMESPACE);
        String ns = expectIdent();
        List<String> next = new ArrayList<>(stack);
        next.add(ns);
        expect(TokenKind.LBRACE);
        List<MrItem> inner = new ArrayList<>();
        while (!cur.check(TokenKind.RBRACE) && !cur.eof()) {
            if (cur.check(TokenKind.NAMESPACE)) {
                inner.add(parseMrNamespace(next));
            } else {
                List<Attribute> attrs = parseAttributes();
                expect(TokenKind.DE_RFUN);
                inner.add(parseMrDeRfunDecl(attrs, next));
            }
        }
        expect(TokenKind.RBRACE);
        return new MrItem.Namespace(ns, inner);
    }

    private MrItem.DeRfunDecl parseMrDeRfunDecl(List<Attribute> attrs, List<String> prefixStack) {
        QualifiedName q = parseQualifiedName();
        QualifiedName full = QualifiedName.join(prefixStack, q);
        expect(TokenKind.LPAREN);
        List<ParamDecl> params = parseParamList();
        expect(TokenKind.RPAREN);
        expect(TokenKind.COLON);
        TypeRef ret = parseType();
        expect(TokenKind.SEMICOLON);
        return new MrItem.DeRfunDecl(attrs, full, params, ret);
    }

    private List<Attribute> parseAttributes() {
        List<Attribute> list = new ArrayList<>();
        while (cur.check(TokenKind.AT)) {
            list.add(parseAttribute());
        }
        return list;
    }

    private Attribute parseAttribute() {
        expect(TokenKind.AT);
        String name = expectIdent();
        expect(TokenKind.LPAREN);
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            Token t = cur.peek();
            if (t.kind() == TokenKind.LPAREN) {
                depth++;
                sb.append(cur.advance().lexeme());
            } else if (t.kind() == TokenKind.RPAREN) {
                depth--;
                cur.advance();
                if (depth == 0) {
                    break;
                }
                sb.append(')');
            } else {
                sb.append(cur.advance().lexeme());
            }
        }
        return new Attribute(name, sb.toString());
    }

    private QualifiedName parseQualifiedName() {
        List<String> segs = new ArrayList<>();
        segs.add(expectIdent());
        while (cur.check(TokenKind.DOUBLE_COLON)) {
            cur.advance();
            segs.add(expectIdent());
        }
        return new QualifiedName(List.copyOf(segs));
    }

    private List<ParamDecl> parseParamList() {
        if (cur.check(TokenKind.RPAREN)) {
            return List.of();
        }
        List<ParamDecl> list = new ArrayList<>();
        while (true) {
            list.add(parseParamDecl());
            if (cur.check(TokenKind.COMMA)) {
                cur.advance();
                continue;
            }
            break;
        }
        return list;
    }

    private ParamDecl parseParamDecl() {
        expect(TokenKind.LBRACKET);
        TypeRef ty = parseType();
        expect(TokenKind.RBRACKET);
        String pname = expectIdent();
        return new ParamDecl(ty, pname);
    }

    private TypeRef parseType() {
        TokenKind k = cur.peek().kind();
        if (isTypeKeyword(k)) {
            return new TypeRef(cur.advance().lexeme());
        }
        throw err("期望类型关键字，实际 " + k);
    }

    private static boolean isTypeKeyword(TokenKind k) {
        return switch (k) {
            case VOID, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, CHAR, STRING_KW, BOOLEAN -> true;
            default -> false;
        };
    }

    private BlockStmt parseBlock(TypeRef fnReturn) {
        expect(TokenKind.LBRACE);
        List<Stmt> stmts = new ArrayList<>();
        while (!cur.check(TokenKind.RBRACE) && !cur.eof()) {
            stmts.add(parseStmt(fnReturn));
        }
        expect(TokenKind.RBRACE);
        return new BlockStmt(stmts);
    }

    private Stmt parseStmt(TypeRef fnReturn) {
        if (cur.check(TokenKind.RETURN)) {
            cur.advance();
            boolean voidFn = "void".equals(fnReturn.keywordLexeme());
            if (voidFn) {
                throw err("void 函数不能使用 return");
            }
            if (cur.check(TokenKind.SEMICOLON)) {
                throw err("return 须带表达式（禁止 return;）");
            }
            Expr e = parseExpr();
            expect(TokenKind.SEMICOLON);
            return new Stmt.Return(e);
        }
        if (cur.check(TokenKind.IF)) {
            cur.advance();
            expect(TokenKind.LPAREN);
            Expr cond = parseExpr();
            expect(TokenKind.RPAREN);
            Stmt then = parseStmt(fnReturn);
            Stmt els = null;
            if (cur.check(TokenKind.ELSE)) {
                cur.advance();
                els = parseStmt(fnReturn);
            }
            return new Stmt.If(cond, then, els);
        }
        if (cur.check(TokenKind.WHILE)) {
            cur.advance();
            expect(TokenKind.LPAREN);
            Expr cond = parseExpr();
            expect(TokenKind.RPAREN);
            Stmt body = parseStmt(fnReturn);
            return new Stmt.While(cond, body);
        }
        if (cur.check(TokenKind.BREAK)) {
            cur.advance();
            expect(TokenKind.SEMICOLON);
            return new Stmt.Break();
        }
        if (cur.check(TokenKind.CONTINUE)) {
            cur.advance();
            expect(TokenKind.SEMICOLON);
            return new Stmt.Continue();
        }
        if (cur.check(TokenKind.SEMICOLON)) {
            cur.advance();
            return new Stmt.Nop();
        }
        if (cur.check(TokenKind.LBRACE)) {
            return new Stmt.Block(parseBlock(fnReturn));
        }
        if (cur.check(TokenKind.PUBLIC)) {
            cur.advance();
            expect(TokenKind.STATIC);
            return parseVarDecl(VarVisibility.PUBLIC_STATIC);
        }
        if (cur.check(TokenKind.PRIVATE)) {
            cur.advance();
            expect(TokenKind.STATIC);
            return parseVarDecl(VarVisibility.PRIVATE_STATIC);
        }
        if (cur.check(TokenKind.STATIC)) {
            cur.advance();
            if (cur.check(TokenKind.VAR)) {
                return parseVarDecl(VarVisibility.PUBLIC_STATIC);
            }
            String staticName = expectIdent();
            expect(TokenKind.SEMICOLON);
            return new Stmt.StaticMark(staticName);
        }
        if (cur.check(TokenKind.VAR)) {
            return parseVarDecl(VarVisibility.LOCAL);
        }
        if (cur.check(TokenKind.PLUS_PLUS)) {
            cur.advance();
            String n = expectIdent();
            expect(TokenKind.SEMICOLON);
            return new Stmt.Update(n, Stmt.UpdateKind.PREFIX_INCR);
        }
        if (cur.check(TokenKind.MINUS_MINUS)) {
            cur.advance();
            String n = expectIdent();
            expect(TokenKind.SEMICOLON);
            return new Stmt.Update(n, Stmt.UpdateKind.PREFIX_DECR);
        }
        if (cur.check(TokenKind.IDENT)) {
            TokenKind k1 = cur.peekToken(1).kind();
            if (isAssignOperator(k1)) {
                String lhs = expectIdent();
                Stmt.AssignOp op = parseAssignOp();
                Expr rhs = parseExpr();
                expect(TokenKind.SEMICOLON);
                return new Stmt.Assign(lhs, op, rhs);
            }
            if (k1 == TokenKind.PLUS_PLUS && cur.peekToken(2).kind() == TokenKind.SEMICOLON) {
                String lhs = expectIdent();
                expect(TokenKind.PLUS_PLUS);
                expect(TokenKind.SEMICOLON);
                return new Stmt.Update(lhs, Stmt.UpdateKind.POSTFIX_INCR);
            }
            if (k1 == TokenKind.MINUS_MINUS && cur.peekToken(2).kind() == TokenKind.SEMICOLON) {
                String lhs = expectIdent();
                expect(TokenKind.MINUS_MINUS);
                expect(TokenKind.SEMICOLON);
                return new Stmt.Update(lhs, Stmt.UpdateKind.POSTFIX_DECR);
            }
        }
        Expr expr = parseExpr();
        expect(TokenKind.SEMICOLON);
        return new Stmt.Expression(expr);
    }

    private static boolean isAssignOperator(TokenKind k) {
        return switch (k) {
            case EQ, PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ, PERCENT_EQ -> true;
            default -> false;
        };
    }

    private Stmt.AssignOp parseAssignOp() {
        if (cur.check(TokenKind.EQ)) {
            cur.advance();
            return Stmt.AssignOp.ASSIGN;
        }
        if (cur.check(TokenKind.PLUS_EQ)) {
            cur.advance();
            return Stmt.AssignOp.ADD_ASSIGN;
        }
        if (cur.check(TokenKind.MINUS_EQ)) {
            cur.advance();
            return Stmt.AssignOp.SUB_ASSIGN;
        }
        if (cur.check(TokenKind.STAR_EQ)) {
            cur.advance();
            return Stmt.AssignOp.MUL_ASSIGN;
        }
        if (cur.check(TokenKind.SLASH_EQ)) {
            cur.advance();
            return Stmt.AssignOp.DIV_ASSIGN;
        }
        if (cur.check(TokenKind.PERCENT_EQ)) {
            cur.advance();
            return Stmt.AssignOp.MOD_ASSIGN;
        }
        throw err("期望 = 或复合赋值运算符");
    }

    private Stmt parseVarDecl(VarVisibility visibility) {
        expect(TokenKind.VAR);
        expect(TokenKind.LBRACKET);
        TypeRef ty = parseType();
        expect(TokenKind.RBRACKET);
        List<VarDeclarator> decls = parseVarDeclarators();
        expect(TokenKind.SEMICOLON);
        return new Stmt.VarDecl(visibility, ty, decls);
    }

    private List<VarDeclarator> parseVarDeclarators() {
        List<VarDeclarator> list = new ArrayList<>();
        list.add(parseOneVarDeclarator());
        while (cur.check(TokenKind.COMMA)) {
            cur.advance();
            list.add(parseOneVarDeclarator());
        }
        boolean anyInit = false;
        boolean allInit = true;
        for (VarDeclarator d : list) {
            boolean has = d.initOrNull() != null;
            anyInit |= has;
            allInit &= has;
        }
        if (anyInit && !allInit) {
            throw err("var 声明须要么全部带初值，要么全部不带初值");
        }
        return list;
    }

    private VarDeclarator parseOneVarDeclarator() {
        String name = expectIdent();
        Expr init = null;
        if (cur.check(TokenKind.EQ)) {
            cur.advance();
            init = parseExpr();
        }
        return new VarDeclarator(name, init);
    }

    private CallExpr parseCallExpr() {
        QualifiedName callee = parseQualifiedName();
        expect(TokenKind.LPAREN);
        List<Expr> args = parseArgList();
        expect(TokenKind.RPAREN);
        return new CallExpr(callee, args);
    }

    private List<Expr> parseArgList() {
        if (cur.check(TokenKind.RPAREN)) {
            return List.of();
        }
        List<Expr> list = new ArrayList<>();
        while (true) {
            list.add(parseExpr());
            if (cur.check(TokenKind.COMMA)) {
                cur.advance();
                continue;
            }
            break;
        }
        return list;
    }

    /**
     * 表达式（与 {@code docs/obr/operators.md} §9 优先级对齐：赋值/复合赋值右结合且低于 {@code ?:}；
     * {@code ||} / {@code &&}；{@code |} / {@code ^} / {@code &}；相等/关系；移位；加减；{@code **} 右结合等）。
     */
    private Expr parseExpr() {
        return parseAssignment();
    }

    /** 赋值表达式；左侧须为单段标识符（与语句级赋值一致）。 */
    private Expr parseAssignment() {
        if (cur.check(TokenKind.IDENT)) {
            TokenKind k1 = cur.peekToken(1).kind();
            if (isAssignOperator(k1)) {
                String lhs = expectIdent();
                Stmt.AssignOp op = parseAssignOp();
                Expr rhs = parseAssignment();
                return new Expr.Assign(lhs, op, rhs);
            }
        }
        return parseConditional();
    }

    private Expr parseConditional() {
        Expr e = parseLogicalOr();
        if (cur.check(TokenKind.QUESTION)) {
            cur.advance();
            Expr then = parseExpr();
            expect(TokenKind.COLON);
            // else 须为 assignment-expression（含嵌套 `?:`）：`a?b:c=d`、`a?b:c?d:e` 等；若用 parseConditional 则 `b+=1` 会只解析成 `b`
            Expr els = parseAssignment();
            return new Expr.Conditional(e, then, els);
        }
        return e;
    }

    private Expr parseLogicalOr() {
        Expr e = parseLogicalAnd();
        while (cur.check(TokenKind.OR_OR)) {
            cur.advance();
            e = new Expr.Binary(e, Expr.BinaryOp.OR, parseLogicalAnd());
        }
        return e;
    }

    private Expr parseLogicalAnd() {
        Expr e = parseBitwiseOr();
        while (cur.check(TokenKind.AND_AND)) {
            cur.advance();
            e = new Expr.Binary(e, Expr.BinaryOp.AND, parseBitwiseOr());
        }
        return e;
    }

    /** {@code |}（低于 {@code ^}） */
    private Expr parseBitwiseOr() {
        Expr e = parseBitwiseXor();
        while (cur.check(TokenKind.PIPE)) {
            cur.advance();
            e = new Expr.Binary(e, Expr.BinaryOp.BIT_OR, parseBitwiseXor());
        }
        return e;
    }

    private Expr parseBitwiseXor() {
        Expr e = parseBitwiseAnd();
        while (cur.check(TokenKind.CARET)) {
            cur.advance();
            e = new Expr.Binary(e, Expr.BinaryOp.BIT_XOR, parseBitwiseAnd());
        }
        return e;
    }

    private Expr parseBitwiseAnd() {
        Expr e = parseEquality();
        while (cur.check(TokenKind.AMP)) {
            cur.advance();
            e = new Expr.Binary(e, Expr.BinaryOp.BIT_AND, parseEquality());
        }
        return e;
    }

    private Expr parseEquality() {
        Expr e = parseRelational();
        while (cur.check(TokenKind.EQ_EQ) || cur.check(TokenKind.NE)) {
            boolean eq = cur.check(TokenKind.EQ_EQ);
            cur.advance();
            e = new Expr.Binary(e, eq ? Expr.BinaryOp.EQ : Expr.BinaryOp.NE, parseRelational());
        }
        return e;
    }

    private Expr parseRelational() {
        Expr e = parseShift();
        while (cur.check(TokenKind.LT)
                || cur.check(TokenKind.LE)
                || cur.check(TokenKind.GT)
                || cur.check(TokenKind.GE)) {
            Expr.BinaryOp op;
            if (cur.check(TokenKind.LT)) {
                cur.advance();
                op = Expr.BinaryOp.LT;
            } else if (cur.check(TokenKind.LE)) {
                cur.advance();
                op = Expr.BinaryOp.LE;
            } else if (cur.check(TokenKind.GT)) {
                cur.advance();
                op = Expr.BinaryOp.GT;
            } else {
                cur.advance();
                op = Expr.BinaryOp.GE;
            }
            e = new Expr.Binary(e, op, parseShift());
        }
        return e;
    }

    private Expr parseShift() {
        Expr left = parseAdditive();
        while (cur.check(TokenKind.LT_LT)
                || cur.check(TokenKind.GT_GT)
                || cur.check(TokenKind.GT_GT_GT)) {
            Expr.BinaryOp op;
            if (cur.check(TokenKind.GT_GT_GT)) {
                cur.advance();
                op = Expr.BinaryOp.USHR;
            } else if (cur.check(TokenKind.GT_GT)) {
                cur.advance();
                op = Expr.BinaryOp.SHR;
            } else {
                cur.advance();
                op = Expr.BinaryOp.SHL;
            }
            left = new Expr.Binary(left, op, parseAdditive());
        }
        return left;
    }

    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (cur.check(TokenKind.PLUS) || cur.check(TokenKind.MINUS)) {
            boolean add = cur.check(TokenKind.PLUS);
            cur.advance();
            Expr right = parseMultiplicative();
            left = new Expr.Binary(left, add ? Expr.BinaryOp.ADD : Expr.BinaryOp.SUB, right);
        }
        return left;
    }

    private Expr parseMultiplicative() {
        Expr left = parseExponent();
        while (cur.check(TokenKind.STAR) || cur.check(TokenKind.SLASH) || cur.check(TokenKind.PERCENT)) {
            Expr.BinaryOp op;
            if (cur.check(TokenKind.STAR)) {
                op = Expr.BinaryOp.MUL;
            } else if (cur.check(TokenKind.SLASH)) {
                op = Expr.BinaryOp.DIV;
            } else {
                op = Expr.BinaryOp.MOD;
            }
            cur.advance();
            Expr right = parseExponent();
            left = new Expr.Binary(left, op, right);
        }
        return left;
    }

    /** 幂运算符 {@code **}，右结合。 */
    private Expr parseExponent() {
        Expr left = parseUnary();
        if (cur.check(TokenKind.STAR_STAR)) {
            cur.advance();
            Expr right = parseExponent();
            return new Expr.Binary(left, Expr.BinaryOp.POW, right);
        }
        return left;
    }

    private Expr parseUnary() {
        if (cur.check(TokenKind.PLUS_PLUS)) {
            cur.advance();
            Expr inner = parseUnary();
            if (!(inner instanceof Expr.NameRef)) {
                throw err("前缀 ++ 仅适用于变量");
            }
            return new Expr.PrefixUpdate(inner, Expr.PostfixOp.INCR);
        }
        if (cur.check(TokenKind.MINUS_MINUS)) {
            cur.advance();
            Expr inner = parseUnary();
            if (!(inner instanceof Expr.NameRef)) {
                throw err("前缀 -- 仅适用于变量");
            }
            return new Expr.PrefixUpdate(inner, Expr.PostfixOp.DECR);
        }
        if (cur.check(TokenKind.PLUS)) {
            cur.advance();
            return new Expr.Unary(Expr.UnaryOp.POS, parseUnary());
        }
        if (cur.check(TokenKind.MINUS)) {
            cur.advance();
            return new Expr.Unary(Expr.UnaryOp.NEG, parseUnary());
        }
        if (cur.check(TokenKind.BANG)) {
            cur.advance();
            return new Expr.Unary(Expr.UnaryOp.LNOT, parseUnary());
        }
        if (cur.check(TokenKind.TILDE)) {
            cur.advance();
            return new Expr.Unary(Expr.UnaryOp.BITNOT, parseUnary());
        }
        return parsePostfix(parsePrimary());
    }

    /** 后缀 {@code x++}/{@code x--}，绑定于 primary（含 {@code (expr)++}）。 */
    private Expr parsePostfix(Expr expr) {
        while (cur.check(TokenKind.PLUS_PLUS) || cur.check(TokenKind.MINUS_MINUS)) {
            boolean incr = cur.check(TokenKind.PLUS_PLUS);
            cur.advance();
            if (!(expr instanceof Expr.NameRef)) {
                throw err("++/-- 仅适用于变量");
            }
            expr =
                    new Expr.Postfix(
                            expr, incr ? Expr.PostfixOp.INCR : Expr.PostfixOp.DECR);
        }
        return expr;
    }

    private Expr parsePrimary() {
        if (cur.check(TokenKind.LPAREN)) {
            cur.advance();
            Expr inner = parseExpr();
            expect(TokenKind.RPAREN);
            return inner;
        }
        TokenKind k = cur.peek().kind();
        if (k == TokenKind.NUMBER || k == TokenKind.STRING_LITERAL || k == TokenKind.CHAR_LITERAL) {
            return new Expr.Literal(cur.advance().lexeme());
        }
        if (k == TokenKind.TRUE
                || k == TokenKind.FALSE
                || k == TokenKind.NULL
                || k == TokenKind.UNDEFINED) {
            return new Expr.Literal(cur.advance().lexeme());
        }
        if (k == TokenKind.IDENT) {
            QualifiedName qn = parseQualifiedName();
            if (cur.check(TokenKind.LPAREN)) {
                expect(TokenKind.LPAREN);
                List<Expr> args = parseArgList();
                expect(TokenKind.RPAREN);
                return new Expr.Invoke(new CallExpr(qn, args));
            }
            if (qn.segments().size() == 1) {
                return new Expr.NameRef(qn.segments().getFirst());
            }
            throw err("限定名须以 '(' 调用为表达式，实际 " + k);
        }
        throw err("期望字面量、标识符或 '('，实际 " + k);
    }

    private String expectIdent() {
        if (!cur.check(TokenKind.IDENT)) {
            throw err("期望标识符，实际 " + cur.peek().kind());
        }
        return cur.advance().lexeme();
    }

    private void expect(TokenKind k) {
        cur.expect(k, fileName);
    }

    private ObrException err(String msg) {
        Token t = cur.peek();
        return new ObrException(fileName + ":" + t.line() + ":" + t.column() + ": " + msg);
    }
}
