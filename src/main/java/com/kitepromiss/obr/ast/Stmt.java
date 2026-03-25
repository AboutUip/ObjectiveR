package com.kitepromiss.obr.ast;

import java.util.List;

public sealed interface Stmt
        permits Stmt.Expression,
                Stmt.Return,
                Stmt.VarDecl,
                Stmt.Block,
                Stmt.Assign,
                Stmt.Update,
                Stmt.StaticMark,
                Stmt.If,
                Stmt.Nop {

    /** 简单 {@code =} 与复合赋值 {@code +=} 等。 */
    enum AssignOp {
        ASSIGN,
        ADD_ASSIGN,
        SUB_ASSIGN,
        MUL_ASSIGN,
        DIV_ASSIGN,
        MOD_ASSIGN
    }

    /** 语句级自增/自减（前缀/后缀仅语法区分，运行时效果相同）。 */
    enum UpdateKind {
        PREFIX_INCR,
        PREFIX_DECR,
        POSTFIX_INCR,
        POSTFIX_DECR
    }

    record Expression(CallExpr call) implements Stmt {}

    /** {@code return expr;}；禁止无表达式的 {@code return;}（解析阶段报错）。 */
    record Return(Expr value) implements Stmt {}

    /**
     * {@code [public|private] static var[type] a, b = ...;}；{@code LOCAL} 时不带 public/private。
     */
    record VarDecl(VarVisibility visibility, TypeRef type, List<VarDeclarator> declarators) implements Stmt {}

    /** 嵌套块 {@code { ... }}。 */
    record Block(BlockStmt body) implements Stmt {}

    /** 赋值 {@code name = expr;} 或 {@code name += expr;} 等（左侧须为单段标识符）。 */
    record Assign(String name, AssignOp op, Expr value) implements Stmt {}

    /** {@code ++x; --x; x++; x--;} */
    record Update(String name, UpdateKind kind) implements Stmt {}

    /** {@code static ident;}，将已存在的局部变量标为 static（运行时迁入函数静态存储）。 */
    record StaticMark(String name) implements Stmt {}

    /** {@code if ( Cond ) StmtOrBlock [ else StmtOrBlock ]} */
    record If(Expr cond, Stmt thenStmt, Stmt elseStmtOrNull) implements Stmt {}

    /** 空语句 {@code ;} */
    record Nop() implements Stmt {}
}
