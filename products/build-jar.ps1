#Requires -Version 5.1
<#
.SYNOPSIS
  Build the runnable fat JAR (Maven package + shade).

.DESCRIPTION
  Runs from repository root. Primary output: products/out/BlinkEngine.jar (also under target/).

.PARAMETER SkipTests
  Passes -DskipTests to Maven.

.EXAMPLE
  .\build-jar.ps1
  .\build-jar.ps1 -SkipTests
#>
param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ProductsOut = Join-Path $RepoRoot 'products\out'

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: mvn not found. Install Maven and add it to PATH." -ForegroundColor Red
    exit 1
}

Push-Location $RepoRoot
try {
    $mvnArgs = @('package')
    if ($SkipTests) {
        $mvnArgs += '-DskipTests'
    }
    Write-Host ("Running: mvn " + ($mvnArgs -join ' ')) -ForegroundColor Cyan
    Write-Host ("Working directory: " + $RepoRoot) -ForegroundColor DarkGray
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $fat = Get-ChildItem -Path $ProductsOut -Filter 'BlinkEngine.jar' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $fat) {
        $fat = Get-ChildItem -Path (Join-Path $RepoRoot 'target') -Filter 'BlinkEngine.jar' -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
    }

    if (-not $fat) {
        Write-Host "ERROR: No BlinkEngine.jar under products/out or target/. Build failed?" -ForegroundColor Red
        exit 1
    }

    Write-Host ""
    Write-Host "OK — Fat JAR (java -jar ...):" -ForegroundColor Green
    Write-Host $fat.FullName
    Write-Host ""
    Write-Host "Example:" -ForegroundColor DarkCyan
    Write-Host "  java -jar `"$($fat.FullName)`" [path to main.obr or project folder]"
    Write-Host ""
}
finally {
    Pop-Location
}
