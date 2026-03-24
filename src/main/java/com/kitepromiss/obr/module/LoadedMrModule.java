package com.kitepromiss.obr.module;

import com.kitepromiss.obr.ast.MrFile;

import java.nio.file.Path;

/** 已解析的单个 moduleR 实例（模块名唯一）。 */
public record LoadedMrModule(String moduleName, Path path, MrFile ast) {}
