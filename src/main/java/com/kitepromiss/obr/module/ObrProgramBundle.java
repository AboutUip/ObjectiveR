package com.kitepromiss.obr.module;

import com.kitepromiss.obr.ast.ObrFile;

import java.nio.file.Path;
import java.util.List;

/** 项目中的全部 .obr 语法树（含 main 与 libs/system.obr）。 */
public record ObrProgramBundle(Path mainPath, ObrFile mainAst, List<ParsedObrFile> files) {

    public record ParsedObrFile(Path path, ObrFile ast) {}
}
