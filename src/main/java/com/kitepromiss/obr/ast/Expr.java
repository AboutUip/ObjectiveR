package com.kitepromiss.obr.ast;

public sealed interface Expr permits Expr.Literal,
        Expr.NameRef,
        Expr.Binary,
        Expr.Unary,
        Expr.Invoke,
        Expr.Postfix,
        Expr.PrefixUpdate,
        Expr.Conditional,
        Expr.Assign {

    record Literal(String lexeme) implements Expr {}

    record NameRef(String name) implements Expr {}

    /** 作为表达式的函数调用（如 {@code foo()}、{@code std::rout(1)}、{@code return bar();}）。 */
    record Invoke(CallExpr call) implements Expr {}

    /**
     * 赋值/复合赋值表达式（如 {@code a = b}、{@code x += 1}）；右结合，优先级低于 {@code ?:}。
     * 左侧须为单段标识符，与 {@link Stmt.Assign} 一致。
     */
    record Assign(String name, Stmt.AssignOp op, Expr value) implements Expr {}

    enum BinaryOp {
        ADD,
        SUB,
        MUL,
        DIV,
        MOD,
        POW,
        EQ,
        NE,
        LT,
        LE,
        GT,
        GE,
        AND,
        OR,
        /** 位与 {@code &} */
        BIT_AND,
        /** 位或 {@code |} */
        BIT_OR,
        /** 位异或 {@code ^} */
        BIT_XOR,
        SHL,
        SHR,
        USHR
    }

    record Binary(Expr left, BinaryOp op, Expr right) implements Expr {}

    /** 三元 {@code a ? b : c}（右结合）。 */
    record Conditional(Expr cond, Expr thenExpr, Expr elseExpr) implements Expr {}

    /** 后缀自增/自减（操作数须为单标识符或经括号归约为变量）。 */
    enum PostfixOp {
        INCR,
        DECR
    }

    record Postfix(Expr operand, PostfixOp op) implements Expr {}

    /** 前缀自增/自减（操作数须为变量；求值为<strong>新</strong>值，与 {@link #Postfix} 相对）。 */
    record PrefixUpdate(Expr operand, PostfixOp op) implements Expr {}

    /** 前缀一元（{@code + - ! ~}）。 */
    enum UnaryOp {
        /** 一元 {@code +} */
        POS,
        /** 一元 {@code -} */
        NEG,
        /** 逻辑非 {@code !}（结果为 {@code boolean}） */
        LNOT,
        /** 按位取反 {@code ~}（结果为 {@code int}，操作数为 {@code int}/{@code long}） */
        BITNOT
    }

    record Unary(UnaryOp op, Expr operand) implements Expr {}
}
