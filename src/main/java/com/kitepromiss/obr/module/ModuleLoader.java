package com.kitepromiss.obr.module;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.MrFile;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.ast.ObrItem;
import com.kitepromiss.obr.lex.Lexer;
import com.kitepromiss.obr.lex.Token;
import com.kitepromiss.obr.parse.Parser;
import com.kitepromiss.obr.trace.InterpreterAuditLog;
import com.kitepromiss.obr.trace.TraceCategory;
import com.kitepromiss.obr.trace.TraceLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 解析 {@code main.obr} 的 import 图并加载对应 .mr（含隐式 system）。 */
public final class ModuleLoader {

    private ModuleLoader() {}

    public static ModuleBundle load(
            Path projectRoot, ObrFile mainObr, InterpreterAuditLog audit) {
        MrModuleIndex index = MrModuleIndex.scan(projectRoot);
        audit.event(
                TraceLevel.NORMAL,
                TraceCategory.MODULES,
                "module_index_built",
                InterpreterAuditLog.fields(
                        "project_root",
                        projectRoot.toAbsolutePath().normalize().toString(),
                        "mr_count", Integer.toString(index.asMap().size())));

        List<String> order = importLoadOrder(mainObr);
        List<LoadedMrModule> loaded = new ArrayList<>();
        for (String name : order) {
            Path path = index.require(name);
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.MODULES,
                    "mr_load_begin",
                    InterpreterAuditLog.fields(
                            "module", name,
                            "path", path.toAbsolutePath().normalize().toString()));
            try {
                String src = Files.readString(path);
                List<Token> tokens = Lexer.readAllTokens(src, path.toString(), audit);
                MrFile ast = new Parser(path.toString(), tokens).parseMrFile();
                loaded.add(new LoadedMrModule(name, path, ast));
                audit.event(
                        TraceLevel.NORMAL,
                        TraceCategory.MODULES,
                        "mr_load_end",
                        InterpreterAuditLog.fields(
                                "module",
                                name,
                                "top_level_items",
                                Integer.toString(ast.items().size())));
            } catch (IOException e) {
                throw new ObrException("读取模块失败: " + path + " — " + e.getMessage());
            }
        }
        return ModuleBundle.of(loaded);
    }

    /**
     * 隐式 {@code system} 始终第一；其余按 {@code main.obr} 顶层出现顺序，去重。
     */
    static List<String> importLoadOrder(ObrFile mainObr) {
        List<String> order = new ArrayList<>();
        order.add(LibsProvisioner.SYSTEM_MODULE_NAME);
        Set<String> seen = new LinkedHashSet<>(order);
        for (ObrItem item : mainObr.items()) {
            if (item instanceof ObrItem.Import imp) {
                String n = imp.moduleName();
                if (seen.add(n)) {
                    order.add(n);
                }
            }
        }
        return order;
    }
}
