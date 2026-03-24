package com.kitepromiss.obr.trace;

/**
 * 解释器行为分类，便于检索与审查。
 */
public enum TraceCategory {
    /** CLI、启动参数、策略级别 */
    BOOT,
    /** 项目根、入口文件、路径解析 */
    FILES,
    /** libs/ 托管、.mr 索引、import 加载 */
    MODULES,
    /** 词法记号流 */
    LEX,
    /** 语法树（预留） */
    PARSE,
    /** 语义与绑定（预留） */
    SEMANTIC,
    /** 执行与调用（预留） */
    RUNTIME,
    /** 内部实现细节（预留） */
    INTERNAL
}
