# ObjectiveR File Support (Cursor/VSCode Extension)

This extension provides:

- file recognition for `.obr` and `.mr`
- dedicated icons for `.obr` and `.mr`

## Local use in Cursor

1. Open Cursor command palette.
2. Run `Extensions: Install from VSIX...` after packing.
3. Select **File Icon Theme** = `ObjectiveR Icons`.

## Pack as VSIX

```bash
npm i -g @vscode/vsce
vsce package
```

The command creates a `.vsix` file for installation.
