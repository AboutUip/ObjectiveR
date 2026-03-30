package com.kitepromiss.obr.project;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.ast.ObrItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 {@code #LINK} 指令项（与 {@code docs/obr/preprocessor.md} §4、§5 一致；含逗号后换行续写）。
 */
public final class LinkParser {

    private static final String E_LINK_PARSE = "E_LINK_PARSE";

    private LinkParser() {}

    /**
     * 合并源码中全部 {@code #LINK} 行的路径项（多行并集；单条 {@code #LINK} 的逗号续写见
     * {@link #mergeLinkContinuationLines(List)}）。
     */
    public static List<String> parseMergedLinkItemsFromSource(String obrSource) {
        List<String> rawLines = splitPhysicalLines(obrSource);
        List<String> mergedLines = mergeLinkContinuationLines(rawLines);
        List<String> out = new ArrayList<>();
        for (String line : mergedLines) {
            String t = line.stripLeading();
            if (t.startsWith("#LINK")) {
                out.addAll(parseLinkLinePayload(t));
            }
        }
        return List.copyOf(out);
    }

    /**
     * 自 AST 顶层项收集 {@code #LINK}（词法阶段已将续行合并为单条 {@code PREPROCESSOR_LINE} 时，与源码一致）。
     */
    public static List<String> parseMergedLinkItemsFromAst(ObrFile ast) {
        List<String> out = new ArrayList<>();
        for (ObrItem item : ast.items()) {
            if (item instanceof ObrItem.Preproc p) {
                String t = p.rawLine().stripLeading();
                if (t.startsWith("#LINK")) {
                    out.addAll(parseLinkLinePayload(t));
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * 逗号续写判定：行在去掉尾部空白后须以 {@code #LINK} 开头且<strong>最后一个非空字符</strong>为 {@code ,}（见规范「逗号后仅空白直至行尾」）。
     */
    public static boolean endsWithLinkCommaContinuation(String lineWithoutNewline) {
        String s = stripTrailingSpaces(lineWithoutNewline);
        return s.startsWith("#LINK") && !s.isEmpty() && s.charAt(s.length() - 1) == ',';
    }

    /**
     * 将物理行列表中「以逗号结尾待续写」的 {@code #LINK} 与后续行合并为一条逻辑行（续行内容 stripLeading 后拼接）。
     */
    public static List<String> mergeLinkContinuationLines(List<String> rawLines) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            String t = line.stripLeading();
            if (!t.startsWith("#LINK")) {
                out.add(line);
                continue;
            }
            StringBuilder acc = new StringBuilder(t);
            while (i + 1 < rawLines.size() && endsWithLinkCommaContinuation(acc.toString())) {
                i++;
                String next = rawLines.get(i).stripLeading();
                if (next.isEmpty()) {
                    break;
                }
                acc.append(' ').append(next);
            }
            out.add(acc.toString());
        }
        return out;
    }

    static List<String> splitPhysicalLines(String obrSource) {
        return List.of(obrSource.split("\\R"));
    }

    private static String stripTrailingSpaces(String s) {
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c != ' ' && c != '\t') {
                break;
            }
            end--;
        }
        return s.substring(0, end);
    }

    private static List<String> parseLinkLinePayload(String lineStripLeading) {
        String rest = lineStripLeading.substring("#LINK".length()).strip();
        if (rest.isEmpty()) {
            throw new ObrException(E_LINK_PARSE + " #LINK 缺少路径项");
        }
        List<String> items = new ArrayList<>();
        for (String part : splitLinkItems(rest)) {
            String s = part.strip();
            if (s.isEmpty()) {
                throw new ObrException(E_LINK_PARSE + " #LINK 中存在空项");
            }
            if (s.charAt(0) != '/') {
                throw new ObrException(E_LINK_PARSE + " #LINK 路径项须以 / 开头: " + s);
            }
            items.add(normalizePathItem(s));
        }
        return items;
    }

    /** 按逗号拆分（{@code #LINK} 参数段）。 */
    private static List<String> splitLinkItems(String rest) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= rest.length(); i++) {
            if (i == rest.length() || rest.charAt(i) == ',') {
                parts.add(rest.substring(start, i));
                start = i + 1;
            }
        }
        return parts;
    }

    /**
     * 规范化单一路径项：统一 {@code /}、去掉除根以外的尾部 {@code /}。
     */
    public static String normalizePathItem(String raw) {
        String s = raw.replace('\\', '/').strip();
        if (s.isEmpty()) {
            return s;
        }
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        if ("/".equals(s)) {
            return "/";
        }
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
