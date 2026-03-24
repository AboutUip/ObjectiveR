package com.kitepromiss.obr.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code main.obr} 的隐式 {@code system} 与顶层 {@code import} 所加载的全部 .mr，按加载顺序排列，且按模块名可查询。
 */
public record ModuleBundle(List<LoadedMrModule> loadOrder, Map<String, LoadedMrModule> byName) {

    public static ModuleBundle of(List<LoadedMrModule> order) {
        LinkedHashMap<String, LoadedMrModule> m = new LinkedHashMap<>();
        for (LoadedMrModule lm : order) {
            m.put(lm.moduleName(), lm);
        }
        return new ModuleBundle(List.copyOf(order), Map.copyOf(m));
    }
}
