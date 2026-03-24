#Requires -Version 5.1
<#
.SYNOPSIS
  Windows only: build app image with BlinkEngine.exe (Maven -Pwin-exe + jpackage).

.DESCRIPTION
  After Maven, flattens products/out/BlinkEngine/ into products/out/ (removes the BlinkEngine wrapper folder).
  Final layout: products/out/BlinkEngine.exe and runtime next to it (plus BlinkEngine.jar from the same build).

.PARAMETER SkipTests
  Passes -DskipTests to Maven.

.EXAMPLE
  .\build-win-exe.ps1
  .\build-win-exe.ps1 -SkipTests
#>
param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

if ($env:OS -notmatch '^Windows') {
    Write-Host "ERROR: This script is for Windows only (jpackage .exe)." -ForegroundColor Red
    exit 1
}

$RepoRoot = Split-Path -Parent $PSScriptRoot

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: mvn not found. Install Maven and add it to PATH." -ForegroundColor Red
    exit 1
}

$jpackageHint = $false
if ($env:JAVA_HOME) {
    $jp = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
    if (Test-Path -LiteralPath $jp) { $jpackageHint = $true }
}
if (-not $jpackageHint) {
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) { $jpackageHint = $true }
}
if (-not $jpackageHint) {
    Write-Host "WARN: jpackage.exe not found (install full JDK, set JAVA_HOME)." -ForegroundColor Yellow
}

function Expand-JpackageAppFolder {
    param(
        [string]$OutDir,
        [string]$NestedName
    )
    $nested = Join-Path $OutDir $NestedName
    if (-not (Test-Path -LiteralPath $nested)) {
        return
    }
    Write-Host ("Flattening: moving {0}\* -> {1}\" -f $nested, $OutDir) -ForegroundColor DarkGray
    Get-ChildItem -LiteralPath $nested -Force | ForEach-Object {
        $dest = Join-Path $OutDir $_.Name
        if (Test-Path -LiteralPath $dest) {
            Remove-Item -LiteralPath $dest -Recurse -Force -ErrorAction Stop
        }
        Move-Item -LiteralPath $_.FullName -Destination $OutDir -Force
    }
    Remove-Item -LiteralPath $nested -Force -Recurse -ErrorAction Stop
}

Push-Location $RepoRoot
try {
    $mvnArgs = @('package', '-Pwin-exe')
    if ($SkipTests) {
        $mvnArgs += '-DskipTests'
    }
    Write-Host ("Running: mvn " + ($mvnArgs -join ' ')) -ForegroundColor Cyan
    Write-Host ("Working directory: " + $RepoRoot) -ForegroundColor DarkGray
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $outDir = Join-Path $RepoRoot 'products\out'
    Expand-JpackageAppFolder -OutDir $outDir -NestedName 'BlinkEngine'

    $exe = Join-Path $outDir 'BlinkEngine.exe'
    if (-not (Test-Path -LiteralPath $exe)) {
        Write-Host "ERROR: Not found after flatten: $exe" -ForegroundColor Red
        exit 1
    }

    # Old jpackage name: launcher still looks for app/ObjectiveR.cfg; remove to avoid confusion with BlinkEngine.
    $staleExe = Join-Path $outDir 'ObjectiveR.exe'
    if (Test-Path -LiteralPath $staleExe) {
        Remove-Item -LiteralPath $staleExe -Force -ErrorAction Stop
        Write-Host "Removed stale ObjectiveR.exe (app is now BlinkEngine; use BlinkEngine.exe)." -ForegroundColor DarkYellow
    }
    $staleNested = Join-Path $outDir 'ObjectiveR'
    if (Test-Path -LiteralPath $staleNested -PathType Container) {
        Remove-Item -LiteralPath $staleNested -Recurse -Force -ErrorAction Stop
        Write-Host "Removed stale products\out\ObjectiveR\ folder." -ForegroundColor DarkYellow
    }

    Write-Host ""
    Write-Host "OK - Distribute this folder (contents are flattened under products\out):" -ForegroundColor Green
    Write-Host $outDir
    Write-Host ""
    Write-Host "Executable:" -ForegroundColor DarkCyan
    Write-Host $exe
    Write-Host ""
    Write-Host "Example:" -ForegroundColor DarkCyan
    Write-Host "  & `"$exe`" [path to main.obr or project folder]"
    Write-Host ""
}
finally {
    Pop-Location
}
