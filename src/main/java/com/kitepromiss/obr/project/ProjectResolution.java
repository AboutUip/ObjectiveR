package com.kitepromiss.obr.project;

import java.nio.file.Path;

/**
 * 入口 {@code main.obr} 路径与项目根目录（见 {@link ProjectRootResolver}：默认等价 {@code #LINK /}；若 {@code #LINK}
 * 含 {@code /main/main.obr} 则根为 {@code main.obr} 上两级目录且须与物理路径一致）。
 */
public record ProjectResolution(Path mainObr, Path projectRoot) {}
