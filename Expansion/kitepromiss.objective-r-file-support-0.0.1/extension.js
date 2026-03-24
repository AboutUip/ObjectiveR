const vscode = require("vscode");
const path = require("path");

const LANGUAGE_IDS = ["objective-r-obr", "objective-r-mr"];

const KEYWORDS = [
  "deRfun",
  "import",
  "namespace",
  "return",
  "if",
  "else",
  "for",
  "while",
  "break",
  "continue",
  "public",
  "private",
  "static",
  "#VERSION",
  "#LINK",
  "void",
  "boolean",
  "byte",
  "short",
  "int",
  "long",
  "float",
  "double",
  "char",
  "string",
  "true",
  "false",
  "null",
  "undefined"
];

class ObrIndex {
  constructor() {
    this.byFullName = new Map();
    this.byShortName = new Map();
    this.moduleNames = new Set();
    this.workspaceReady = false;
  }

  async rebuild() {
    this.byFullName.clear();
    this.byShortName.clear();
    this.moduleNames.clear();
    const [mrFiles, obrFiles] = await Promise.all([
      vscode.workspace.findFiles("**/*.mr", "**/node_modules/**"),
      vscode.workspace.findFiles("**/*.obr", "**/node_modules/**")
    ]);

    for (const uri of [...mrFiles, ...obrFiles]) {
      await this.indexFile(uri);
    }
    this.workspaceReady = true;
  }

  async update(uri) {
    // For simplicity and correctness, rebuild full index.
    await this.rebuild();
  }

  async indexFile(uri) {
    try {
      const doc = await vscode.workspace.openTextDocument(uri);
      const source = doc.getText();
      this.moduleNames.add(getModuleNameFromUri(uri));
      const symbols = parseFunctions(source, uri);
      for (const sym of symbols) {
        addToMapArray(this.byFullName, sym.fullName, sym);
        addToMapArray(this.byShortName, sym.shortName, sym);
      }
    } catch (_e) {
      // Ignore file parse/index failures to keep extension resilient.
    }
  }

  find(name) {
    if (!name) return [];
    if (name.includes("::")) {
      return dedupeSymbols([...(this.byFullName.get(name) || [])]);
    }
    return dedupeSymbols([...(this.byShortName.get(name) || [])]);
  }

  allSymbols() {
    const acc = [];
    for (const values of this.byFullName.values()) {
      for (const v of values) acc.push(v);
    }
    return dedupeSymbols(acc);
  }

  allModuleNames() {
    return [...this.moduleNames].sort((a, b) => a.localeCompare(b));
  }
}

function parseFunctionHeadsWithLine(text) {
  const source = stripBlockComments(text);
  const lines = source.split(/\r?\n/);
  const namespaceStack = [];
  const result = [];
  for (let i = 0; i < lines.length; i += 1) {
    let line = lines[i];
    if (!line) continue;
    line = stripLineComment(line).trim();
    if (!line) continue;

    const ns = line.match(/^namespace\s+([A-Za-z_]\w*)\s*\{/);
    if (ns) {
      namespaceStack.push(ns[1]);
      continue;
    }

    if (/^deRfun\b/.test(line)) {
      const { merged, endLine } = readFunctionHeader(lines, i);
      const parsed = parseFunctionFromHeader(merged, namespaceStack);
      if (parsed) {
        result.push({
          fullName: parsed.fullName,
          signature: parsed.signature,
          ending: parsed.ending,
          line: i
        });
      }
      i = endLine;
    }

    if (line.includes("}")) {
      const closes = (line.match(/\}/g) || []).length;
      for (let j = 0; j < closes && namespaceStack.length > 0; j += 1) {
        namespaceStack.pop();
      }
    }
  }
  return result;
}

function addToMapArray(map, key, value) {
  if (!map.has(key)) map.set(key, []);
  map.get(key).push(value);
}

function dedupeSymbols(symbols) {
  const seen = new Set();
  const out = [];
  for (const s of symbols) {
    const key = `${s.fullName}|${s.signature}|${s.source}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(s);
  }
  return out;
}

function stripBlockComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, "");
}

function stripLineComment(line) {
  return line.replace(/\/\/.*$/, "");
}

function readFunctionHeader(lines, startLine) {
  let endLine = startLine;
  let merged = stripLineComment(lines[startLine] || "").trim();
  while (endLine + 1 < lines.length && !/[;{]\s*$/.test(merged)) {
    endLine += 1;
    const next = stripLineComment(lines[endLine] || "").trim();
    if (!next) continue;
    merged += ` ${next}`;
  }
  return { merged, endLine };
}

function parseFunctionFromHeader(headerText, namespaceStack) {
  const fn = headerText.match(
    /^deRfun\s+([A-Za-z_]\w*(?:::[A-Za-z_]\w*)*)\s*\(([^)]*)\)\s*:\s*([A-Za-z_]\w*)\s*([;{])/
  );
  if (!fn) return null;
  const rawName = fn[1];
  const paramsRaw = fn[2].trim();
  const returnType = fn[3];
  const ending = fn[4];
  const fullName =
    rawName.includes("::") || namespaceStack.length === 0
      ? rawName
      : `${namespaceStack.join("::")}::${rawName}`;
  const shortName = fullName.includes("::") ? fullName.split("::").pop() : fullName;
  const params = parseParams(paramsRaw);
  const signature = `${fullName}(${params.map((p) => `[${p.type}]${p.name}`).join(", ")}):${returnType}`;
  return {
    fullName,
    shortName,
    returnType,
    params,
    signature,
    ending
  };
}

function parseFunctions(text, uri) {
  const source = stripBlockComments(text);
  const lines = source.split(/\r?\n/);
  const namespaceStack = [];
  const symbols = [];
  const pendingAnnotations = [];

  for (let i = 0; i < lines.length; i += 1) {
    let line = lines[i];
    if (!line) continue;
    line = stripLineComment(line).trim();
    if (!line) continue;

    const ann = line.match(/^@(Overwrite|Callfun)\s*\(([^)]*)\)/);
    if (ann) {
      pendingAnnotations.push(`${ann[1]}(${ann[2] || ""})`);
      continue;
    }

    const ns = line.match(/^namespace\s+([A-Za-z_]\w*)\s*\{/);
    if (ns) {
      namespaceStack.push(ns[1]);
      continue;
    }

    if (/^deRfun\b/.test(line)) {
      const { merged, endLine } = readFunctionHeader(lines, i);
      const parsed = parseFunctionFromHeader(merged, namespaceStack);
      if (!parsed) {
        i = endLine;
        continue;
      }
      symbols.push({
        fullName: parsed.fullName,
        shortName: parsed.shortName,
        returnType: parsed.returnType,
        params: parsed.params,
        signature: parsed.signature,
        kind: parsed.ending === ";" ? "declaration" : "implementation",
        annotations: [...pendingAnnotations],
        source: vscode.workspace.asRelativePath(uri, false),
        uri,
        moduleName: getModuleNameFromUri(uri),
        line: i + 1
      });
      pendingAnnotations.length = 0;
      i = endLine;
    }

    // Very lightweight namespace close tracking.
    if (line.includes("}")) {
      const closes = (line.match(/\}/g) || []).length;
      for (let j = 0; j < closes && namespaceStack.length > 0; j += 1) {
        namespaceStack.pop();
      }
    }
  }

  return symbols;
}

function getModuleNameFromUri(uri) {
  return path.basename(uri.fsPath, path.extname(uri.fsPath));
}

function parseParams(raw) {
  if (!raw) return [];
  return raw
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part, idx) => {
      const m = part.match(/^\[([A-Za-z_]\w*)\]\s*([A-Za-z_]\w*)$/);
      if (m) return { type: m[1], name: m[2] };
      return { type: "any", name: `arg${idx + 1}` };
    });
}

function buildFunctionCompletionItem(sym) {
  const item = new vscode.CompletionItem(
    sym.fullName,
    vscode.CompletionItemKind.Function
  );
  item.detail = sym.signature;
  item.sortText = sym.fullName.includes("::") ? `1_${sym.fullName}` : `0_${sym.fullName}`;
  item.documentation = new vscode.MarkdownString(
    [
      `\`${sym.signature}\``,
      "",
      `来源: \`${sym.source}\``,
      ...(sym.annotations.length
        ? ["", `注解: \`${sym.annotations.join("`, `")}\``]
        : [])
    ].join("\n")
  );

  const snippet = new vscode.SnippetString();
  snippet.appendText(`${sym.fullName}(`);
  sym.params.forEach((p, index) => {
    if (index > 0) snippet.appendText(", ");
    snippet.appendPlaceholder(p.name || `arg${index + 1}`);
  });
  snippet.appendText(")");
  item.insertText = snippet;
  return item;
}

function buildImportCompletionItems(index) {
  return index.allModuleNames().map((name) => {
    const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Module);
    item.detail = "模块";
    item.insertText = name;
    item.sortText = `00_${name}`;
    return item;
  });
}

function buildDirectiveCompletionItems() {
  const overwrite = new vscode.CompletionItem(
    "@Overwrite",
    vscode.CompletionItemKind.Snippet
  );
  overwrite.insertText = new vscode.SnippetString("@Overwrite(${1:*})");
  overwrite.filterText = "Overwrite";
  overwrite.detail = "Annotation";
  overwrite.documentation = "Restrict where this function can be implemented.";

  const callfun = new vscode.CompletionItem(
    "@Callfun",
    vscode.CompletionItemKind.Snippet
  );
  callfun.insertText = new vscode.SnippetString("@Callfun(${1:*})");
  callfun.filterText = "Callfun";
  callfun.detail = "Annotation";
  callfun.documentation = "Restrict where this function can be called.";

  const version = new vscode.CompletionItem(
    "#VERSION",
    vscode.CompletionItemKind.Snippet
  );
  version.insertText = new vscode.SnippetString("#VERSION: ${1:1.0};");
  version.detail = "Preprocessor";

  const link = new vscode.CompletionItem(
    "#LINK",
    vscode.CompletionItemKind.Snippet
  );
  link.insertText = new vscode.SnippetString("#LINK: ${1:/};");
  link.detail = "Preprocessor";

  return [overwrite, callfun, version, link];
}

function getQualifiedTokenAtPosition(document, position) {
  const lineText = document.lineAt(position.line).text;
  if (!lineText) return null;
  const isTokenChar = (ch) => /[A-Za-z0-9_:]/.test(ch);

  let left = position.character;
  let right = position.character;

  while (left > 0 && isTokenChar(lineText[left - 1])) left -= 1;
  while (right < lineText.length && isTokenChar(lineText[right])) right += 1;

  if (left === right) return null;
  const token = lineText.slice(left, right).replace(/^:+|:+$/g, "");
  return /^[A-Za-z_]\w*(?:::[A-Za-z_]\w*)*$/.test(token) ? token : null;
}

function getNamespaceQualifierBeforePosition(document, position) {
  const prefix = document.lineAt(position.line).text.slice(0, position.character);
  const m = prefix.match(/([A-Za-z_]\w*(?:::[A-Za-z_]\w*)*)::[A-Za-z_]\w*$/);
  return m ? m[1] : null;
}

function parseImportsFromDocument(document) {
  const imports = new Set(["system"]);
  const text = document.getText();
  const importRegex = /^\s*import\s+([A-Za-z_]\w*)\s*;/gm;
  let match;
  while ((match = importRegex.exec(text)) !== null) {
    imports.add(match[1]);
  }
  return imports;
}

function isImportContext(document, position) {
  const linePrefix = document.lineAt(position.line).text.slice(0, position.character);
  return /^\s*import\s+[A-Za-z_]*$/.test(linePrefix);
}

function getImportModuleAtPosition(document, position) {
  const lineText = document.lineAt(position.line).text;
  const match = lineText.match(/^\s*import\s+([A-Za-z_]\w*)\s*;/);
  if (!match) return null;
  const moduleName = match[1];
  const tokenStart = lineText.indexOf(moduleName);
  if (tokenStart < 0) return null;
  const tokenEnd = tokenStart + moduleName.length;
  if (position.character < tokenStart || position.character > tokenEnd) return null;
  return moduleName;
}

async function findModuleUrisByName(moduleName) {
  if (!moduleName) return [];
  const [mrUris, obrUris] = await Promise.all([
    vscode.workspace.findFiles(`**/${moduleName}.mr`, "**/node_modules/**"),
    vscode.workspace.findFiles(`**/${moduleName}.obr`, "**/node_modules/**")
  ]);
  return [...mrUris, ...obrUris];
}

function collectDiagnosticsForDocument(document, index) {
  const diagnostics = [];
  if (!LANGUAGE_IDS.includes(document.languageId)) return diagnostics;

  const text = document.getText();
  const lines = text.split(/\r?\n/);
  const heads = parseFunctionHeadsWithLine(text);
  const ext = path.extname(document.uri.fsPath).toLowerCase();

  // Rule 1: duplicate signatures in same file.
  const seen = new Map();
  for (const h of heads) {
    if (!seen.has(h.signature)) {
      seen.set(h.signature, h.line);
      continue;
    }
    const lineText = lines[h.line] || "";
    const range = new vscode.Range(
      new vscode.Position(h.line, 0),
      new vscode.Position(h.line, Math.max(1, lineText.length))
    );
    diagnostics.push(
      new vscode.Diagnostic(
        range,
        `重复函数签名: ${h.signature}`,
        vscode.DiagnosticSeverity.Error
      )
    );
  }

  // Rule 2: .mr should only declare (end with ';').
  if (ext === ".mr") {
    for (const h of heads) {
      if (h.ending !== ";") {
        const lineText = lines[h.line] || "";
        const range = new vscode.Range(
          new vscode.Position(h.line, 0),
          new vscode.Position(h.line, Math.max(1, lineText.length))
        );
        diagnostics.push(
          new vscode.Diagnostic(
            range,
            "`.mr` 头文件中的 `deRfun` 必须以 `;` 结束（仅声明，不应包含实现体）。",
            vscode.DiagnosticSeverity.Error
          )
        );
      }
    }
  }

  // Rule 3: .obr should implement (end with '{').
  if (ext === ".obr") {
    for (const h of heads) {
      if (h.ending !== "{") {
        const lineText = lines[h.line] || "";
        const range = new vscode.Range(
          new vscode.Position(h.line, 0),
          new vscode.Position(h.line, Math.max(1, lineText.length))
        );
        diagnostics.push(
          new vscode.Diagnostic(
            range,
            "`.obr` 实现文件中的 `deRfun` 应以 `{` 开始函数体，而不是仅声明。",
            vscode.DiagnosticSeverity.Warning
          )
        );
      }
    }
  }

  // Rule 4: import module exists.
  const modules = new Set(index.allModuleNames());
  const importRegex = /^\s*import\s+([A-Za-z_]\w*)\s*;/gm;
  let m;
  while ((m = importRegex.exec(text)) !== null) {
    const moduleName = m[1];
    if (moduleName === "system") continue;
    if (modules.has(moduleName)) continue;
    const line = document.positionAt(m.index).line;
    const lineText = lines[line] || "";
    const range = new vscode.Range(
      new vscode.Position(line, 0),
      new vscode.Position(line, Math.max(1, lineText.length))
    );
    diagnostics.push(
      new vscode.Diagnostic(
        range,
        `未找到模块 \`${moduleName}\`（检查是否存在同名 .mr/.obr 文件）。`,
        vscode.DiagnosticSeverity.Warning
      )
    );
  }

  // Rule 4.1: duplicate import module.
  const imported = new Set();
  const importRegex2 = /^\s*import\s+([A-Za-z_]\w*)\s*;/gm;
  while ((m = importRegex2.exec(text)) !== null) {
    const moduleName = m[1];
    const line = document.positionAt(m.index).line;
    if (imported.has(moduleName)) {
      const lineText = lines[line] || "";
      diagnostics.push(
        new vscode.Diagnostic(
          new vscode.Range(
            new vscode.Position(line, 0),
            new vscode.Position(line, Math.max(1, lineText.length))
          ),
          `重复 import: \`${moduleName}\`。`,
          vscode.DiagnosticSeverity.Warning
        )
      );
    }
    imported.add(moduleName);
  }

  // Rule 4.2: malformed annotation syntax.
  for (let i = 0; i < lines.length; i += 1) {
    const line = stripLineComment(lines[i] || "").trim();
    if (!line.startsWith("@")) continue;
    if (/^@(Overwrite|Callfun)\s*\([^)]*\)\s*$/.test(line)) continue;
    if (/^@(Overwrite|Callfun)\b/.test(line)) {
      diagnostics.push(
        new vscode.Diagnostic(
          new vscode.Range(
            new vscode.Position(i, 0),
            new vscode.Position(i, Math.max(1, (lines[i] || "").length))
          ),
          "注解语法无效，应为 `@Overwrite(...)` 或 `@Callfun(...)`。",
          vscode.DiagnosticSeverity.Error
        )
      );
    }
  }

  // Rule 5: main.obr should define main():void
  const rel = vscode.workspace.asRelativePath(document.uri, false).replace(/\\/g, "/");
  if (rel.endsWith("/main.obr") || rel === "main.obr") {
    const hasMain = heads.some((h) => h.fullName === "main");
    if (!hasMain) {
      diagnostics.push(
        new vscode.Diagnostic(
          new vscode.Range(new vscode.Position(0, 0), new vscode.Position(0, 1)),
          "`main.obr` 建议包含入口函数 `deRfun main():void{...}`。",
          vscode.DiagnosticSeverity.Warning
        )
      );
    }
  }

  return diagnostics;
}

function filterVisibleSymbols(symbols, document) {
  const imports = parseImportsFromDocument(document);
  const currentModule = getModuleNameFromUri(document.uri);
  return symbols.filter((sym) => {
    if (!sym || !sym.moduleName) return false;
    if (sym.moduleName === currentModule) return true;
    if (imports.has(sym.moduleName)) return true;
    return false;
  });
}

function resolveSymbolsForHover(index, document, position, token) {
  if (!token) return [];
  if (token.includes("::")) return index.find(token);

  const qualifier = getNamespaceQualifierBeforePosition(document, position);
  if (qualifier) {
    const qualified = filterVisibleSymbols(
      index.find(`${qualifier}::${token}`),
      document
    );
    if (qualified.length) return qualified;
  }

  return filterVisibleSymbols(index.find(token), document);
}

function toDisplayName(sym) {
  return sym.fullName.includes("::") ? sym.fullName : `${sym.fullName}`;
}

function buildHoverMarkdown(matches) {
  const md = new vscode.MarkdownString(undefined, true);
  const top = matches[0];
  md.appendMarkdown(`**函数: ${toDisplayName(top)}**\n\n`);
  md.appendCodeblock(top.signature, "objective-r");
  md.appendMarkdown(`定义位置: \`${top.source}:${top.line}\`\n\n`);
  if (top.annotations.length) {
    md.appendMarkdown(`注解: \`${top.annotations.join("`, `")}\`\n\n`);
  }

  if (matches.length > 1) {
    md.appendMarkdown(`**其他签名**\n\n`);
    for (const sym of matches.slice(1, 7)) {
      md.appendMarkdown(`- \`${sym.signature}\`  \n  来自 \`${sym.source}:${sym.line}\`\n`);
    }
    if (matches.length > 7) {
      md.appendMarkdown(`\n... 还有 ${matches.length - 7} 个重载。`);
    }
  }
  return md;
}

function rankDefinitionMatches(matches, document, position) {
  const currentRel = vscode.workspace.asRelativePath(document.uri, false);
  const currentLine = position.line + 1;
  const fromObr = document.languageId === "objective-r-obr";

  const withoutSelf = matches.filter((sym) => {
    // Avoid "no move" by excluding the exact current location.
    return !(sym.source === currentRel && sym.line === currentLine);
  });
  const pool = withoutSelf.length ? withoutSelf : matches;

  const rank = (sym) => {
    const isMr = String(sym.source || "").toLowerCase().endsWith(".mr");
    const isDecl = sym.kind === "declaration";
    if (fromObr) {
      if (isMr && isDecl) return 0;
      if (isMr) return 1;
      if (isDecl) return 2;
      return 3;
    }
    if (isDecl) return 0;
    return 1;
  };

  return [...pool].sort((a, b) => rank(a) - rank(b));
}

function extractCallContext(document, position) {
  const line = document.lineAt(position.line).text.slice(0, position.character);
  const m = line.match(/([A-Za-z_]\w*(?:::[A-Za-z_]\w*)*)\s*\(([^()]*)$/);
  if (!m) return null;
  const fnName = m[1];
  const argText = m[2] || "";
  const activeParameter = argText.trim() ? argText.split(",").length - 1 : 0;
  return { fnName, activeParameter };
}

async function activate(context) {
  const index = new ObrIndex();
  await index.rebuild();
  const diagnosticCollection = vscode.languages.createDiagnosticCollection("objective-r");
  context.subscriptions.push(diagnosticCollection);
  let scheduledRefresh = null;

  const refreshDiagnosticsForAll = async () => {
    const docs = await Promise.all(
      (await vscode.workspace.findFiles("**/*.{obr,mr}", "**/node_modules/**")).map((uri) =>
        vscode.workspace.openTextDocument(uri)
      )
    );
    for (const doc of docs) {
      const diags = collectDiagnosticsForDocument(doc, index);
      diagnosticCollection.set(doc.uri, diags);
    }
  };
  const scheduleIndexAndDiagnosticsRefresh = (delayMs = 250) => {
    if (scheduledRefresh) clearTimeout(scheduledRefresh);
    scheduledRefresh = setTimeout(async () => {
      scheduledRefresh = null;
      try {
        await index.rebuild();
        await refreshDiagnosticsForAll();
      } catch (_e) {
        // Keep extension responsive if background refresh fails.
      }
    }, delayMs);
  };
  context.subscriptions.push(
    new vscode.Disposable(() => {
      if (scheduledRefresh) clearTimeout(scheduledRefresh);
    })
  );
  await refreshDiagnosticsForAll();

  const watcherMr = vscode.workspace.createFileSystemWatcher("**/*.mr");
  const watcherObr = vscode.workspace.createFileSystemWatcher("**/*.obr");

  const onChange = async (_uri) => {
    scheduleIndexAndDiagnosticsRefresh(180);
  };
  watcherMr.onDidChange(onChange);
  watcherMr.onDidCreate(onChange);
  watcherMr.onDidDelete(onChange);
  watcherObr.onDidChange(onChange);
  watcherObr.onDidCreate(onChange);
  watcherObr.onDidDelete(onChange);

  context.subscriptions.push(watcherMr, watcherObr);

  context.subscriptions.push(
    vscode.workspace.onDidOpenTextDocument(async (doc) => {
      if (!LANGUAGE_IDS.includes(doc.languageId)) return;
      diagnosticCollection.set(doc.uri, collectDiagnosticsForDocument(doc, index));
    }),
    vscode.workspace.onDidChangeTextDocument((evt) => {
      const doc = evt.document;
      if (!LANGUAGE_IDS.includes(doc.languageId)) return;
      diagnosticCollection.set(doc.uri, collectDiagnosticsForDocument(doc, index));
      scheduleIndexAndDiagnosticsRefresh(500);
    }),
    vscode.workspace.onDidCloseTextDocument((doc) => {
      diagnosticCollection.delete(doc.uri);
    })
  );

  const completionProvider = vscode.languages.registerCompletionItemProvider(
    LANGUAGE_IDS,
    {
      provideCompletionItems(document, position) {
        const items = [];
        if (isImportContext(document, position)) {
          return buildImportCompletionItems(index);
        }

        for (const kw of KEYWORDS) {
          const item = new vscode.CompletionItem(
            kw,
            kw.startsWith("#")
              ? vscode.CompletionItemKind.Keyword
              : kw.startsWith("@")
              ? vscode.CompletionItemKind.Snippet
              : vscode.CompletionItemKind.Keyword
          );
          item.insertText = kw;
          items.push(item);
        }
        items.push(...buildDirectiveCompletionItems());

        const qualifier = getNamespaceQualifierBeforePosition(document, position);
        const symbols = filterVisibleSymbols(index.allSymbols(), document);
        for (const sym of symbols) {
          const item = buildFunctionCompletionItem(sym);
          if (qualifier) {
            const preferredPrefix = `${qualifier}::`;
            item.sortText = sym.fullName.startsWith(preferredPrefix)
              ? `0_${sym.fullName}`
              : `2_${sym.fullName}`;
          }
          items.push(item);
        }

        return items;
      }
    },
    ":",
    "@",
    "#"
  );

  const hoverProvider = vscode.languages.registerHoverProvider(LANGUAGE_IDS, {
    async provideHover(document, position) {
      const importModule = getImportModuleAtPosition(document, position);
      if (importModule) {
        const uris = await findModuleUrisByName(importModule);
        const md = new vscode.MarkdownString(undefined, true);
        md.appendMarkdown(`**模块: ${importModule}**\n\n`);
        if (!uris.length) {
          md.appendMarkdown("未找到对应模块文件（`.mr` / `.obr`）。");
        } else {
          for (const uri of uris.slice(0, 6)) {
            const rel = vscode.workspace.asRelativePath(uri, false);
            md.appendMarkdown(`- \`${rel}\`\n`);
          }
        }
        return new vscode.Hover(md);
      }

      const token = getQualifiedTokenAtPosition(document, position);
      if (!token) return null;

      const matches = resolveSymbolsForHover(index, document, position, token);
      if (!matches.length) return null;

      const md = buildHoverMarkdown(matches);
      return new vscode.Hover(md);
    }
  });

  const signatureProvider = vscode.languages.registerSignatureHelpProvider(
    LANGUAGE_IDS,
    {
      provideSignatureHelp(document, position) {
        const ctx = extractCallContext(document, position);
        if (!ctx) return null;
        const matches = filterVisibleSymbols(index.find(ctx.fnName), document);
        if (!matches.length) return null;

        const result = new vscode.SignatureHelp();
        result.activeParameter = ctx.activeParameter;
        result.activeSignature = 0;
        result.signatures = matches.map((sym) => {
          const info = new vscode.SignatureInformation(
            sym.signature,
            new vscode.MarkdownString(
              `来源: \`${sym.source}:${sym.line}\`${sym.annotations.length ? `\n\n注解: \`${sym.annotations.join("`, `")}\`` : ""}`
            )
          );
          info.parameters = sym.params.map(
            (p) => new vscode.ParameterInformation(`[${p.type}]${p.name}`)
          );
          return info;
        });
        return result;
      }
    },
    "(",
    ","
  );

  const definitionProvider = vscode.languages.registerDefinitionProvider(
    LANGUAGE_IDS,
    {
      async provideDefinition(document, position) {
        const importModule = getImportModuleAtPosition(document, position);
        if (importModule) {
          const uris = await findModuleUrisByName(importModule);
          return uris.map((uri) => new vscode.Location(uri, new vscode.Position(0, 0)));
        }

        const token = getQualifiedTokenAtPosition(document, position);
        if (!token) return null;
        const matches = resolveSymbolsForHover(index, document, position, token);
        if (!matches.length) return null;

        const ranked = rankDefinitionMatches(matches, document, position);
        return ranked.map((sym) => {
          const targetLine = Math.max(0, (sym.line || 1) - 1);
          return new vscode.Location(sym.uri, new vscode.Position(targetLine, 0));
        });
      }
    }
  );

  const referenceProvider = vscode.languages.registerReferenceProvider(
    LANGUAGE_IDS,
    {
      async provideReferences(document, position) {
        const token = getQualifiedTokenAtPosition(document, position);
        if (!token) return [];
        const matches = resolveSymbolsForHover(index, document, position, token);
        if (!matches.length) return [];

        const names = new Set();
        for (const sym of matches) {
          names.add(sym.fullName);
          names.add(sym.shortName);
        }
        const pattern = [...names]
          .sort((a, b) => b.length - a.length)
          .map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
          .join("|");
        if (!pattern) return [];

        const allDocs = await Promise.all(
          (await vscode.workspace.findFiles("**/*.{obr,mr}", "**/node_modules/**")).map((uri) =>
            vscode.workspace.openTextDocument(uri)
          )
        );

        const refRegex = new RegExp(`\\b(?:${pattern})\\b`, "g");
        const refs = [];
        for (const doc of allDocs) {
          const text = doc.getText();
          let m;
          while ((m = refRegex.exec(text)) !== null) {
            const start = doc.positionAt(m.index);
            const end = doc.positionAt(m.index + m[0].length);
            refs.push(new vscode.Location(doc.uri, new vscode.Range(start, end)));
          }
        }
        return refs;
      }
    }
  );

  context.subscriptions.push(
    completionProvider,
    hoverProvider,
    signatureProvider,
    definitionProvider,
    referenceProvider
  );
}

function deactivate() {}

module.exports = {
  activate,
  deactivate
};
