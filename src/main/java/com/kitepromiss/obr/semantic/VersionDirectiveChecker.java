package com.kitepromiss.obr.semantic;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.ast.ObrItem;
import com.kitepromiss.obr.module.ObrProgramBundle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验各 {@code .obr} 顶层预处理行中的 {@code #VERSION}（见 {@code docs/obr/preprocessor.md}）。
 */
public final class VersionDirectiveChecker {

    private static final String E_PREPROC_VERSION_PARSE = "E_PREPROC_VERSION_PARSE";
    private static final String E_VER_UNSUPPORTED = "E_VER_UNSUPPORTED";
    private static final String E_VER_MISMATCH = "E_VER_MISMATCH";

    /** 整行：{@code #}、可选空白、VERSION、空白、十进制正整数、行尾空白。 */
    private static final Pattern VERSION_LINE = Pattern.compile("^#\\s*VERSION\\s+([0-9]+)\\s*$");

    private VersionDirectiveChecker() {}

    public static void checkProgram(ObrProgramBundle program) {
        for (ObrProgramBundle.ParsedObrFile f : program.files()) {
            checkObrFile(f.path(), f.ast());
        }
    }

    private static void checkObrFile(Path path, ObrFile obr) {
        List<Integer> declared = new ArrayList<>();
        for (ObrItem item : obr.items()) {
            if (!(item instanceof ObrItem.Preproc p)) {
                continue;
            }
            String line = p.rawLine().trim();
            if (!line.startsWith("#")) {
                continue;
            }
            String afterHash = line.substring(1).trim();
            if (!afterHash.startsWith("VERSION")) {
                continue;
            }
            Matcher m = VERSION_LINE.matcher(line);
            if (!m.matches()) {
                throw new ObrException(
                        E_PREPROC_VERSION_PARSE
                                + " "
                                + path
                                + ": 无法解析的 #VERSION 行（须为 \"#VERSION <正整数>\"）: "
                                + p.rawLine().trim());
            }
            int v = Integer.parseInt(m.group(1));
            if (v < 1) {
                throw new ObrException(
                        E_PREPROC_VERSION_PARSE + " " + path + ": #VERSION 须为正整数: " + p.rawLine().trim());
            }
            declared.add(v);
        }
        if (declared.isEmpty()) {
            return;
        }
        int first = declared.getFirst();
        for (int i = 1; i < declared.size(); i++) {
            if (declared.get(i) != first) {
                throw new ObrException(
                        E_VER_MISMATCH + " " + path + ": 同一文件内多条 #VERSION 不一致: " + declared);
            }
        }
        if (first != ObrLanguageVersion.SUPPORTED) {
            throw new ObrException(
                    E_VER_UNSUPPORTED
                            + " "
                            + path
                            + ": 不支持的 Obr 语言版本 "
                            + first
                            + "（当前解释器仅支持版本 "
                            + ObrLanguageVersion.SUPPORTED
                            + "）");
        }
    }
}
