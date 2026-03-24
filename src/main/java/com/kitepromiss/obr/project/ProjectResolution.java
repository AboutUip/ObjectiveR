package com.kitepromiss.obr.project;

import java.nio.file.Path;

/**
 * 入口 {@code main.obr} 路径与项目根目录（默认 {@code #LINK /} 时根为 {@code main.obr} 所在目录）。
 */
public record ProjectResolution(Path mainObr, Path projectRoot) {}
