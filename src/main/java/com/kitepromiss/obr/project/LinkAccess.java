package com.kitepromiss.obr.project;

/**
 * {@code docs/obr/preprocessor.md}：访问方路径 {@code C} 是否命中 {@code #LINK} 列表中的某项。
 */
public final class LinkAccess {

    private LinkAccess() {}

    /**
     * @param callerRel 以 {@code /} 开头的项目相对路径（如 {@code /main.obr}、{@code /lib/a.obr}）
     * @param linkItem 已 {@link LinkParser#normalizePathItem(String)} 的单项
     */
    public static boolean hits(String callerRel, String linkItem) {
        String item = LinkParser.normalizePathItem(linkItem);
        if ("/".equals(item)) {
            return true;
        }
        if (item.endsWith(".obr")) {
            return callerRel.equals(item);
        }
        String prefix = item.endsWith("/") ? item : item + "/";
        return callerRel.startsWith(prefix) || callerRel.equals(item);
    }
}
