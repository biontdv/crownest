<#
.SYNOPSIS
    Crownest installer for Windows. Builds a native Crownest.exe (with a bundled
    Java runtime) and adds Start Menu and Desktop shortcuts.

.DESCRIPTION
    Uses the JDK's own jpackage to produce a self-contained application image,
    so the machine you install on does not need Java afterwards. A JDK 17+ is
    only required to build.

.PARAMETER Uninstall
    Remove Crownest (app, shortcuts and PATH entry). Config in %APPDATA%\Crownest
    is left untouched.

.PARAMETER NoPath
    Do not add the install folder to the user PATH.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\install.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\install.ps1 -Uninstall
#>
param(
    [switch]$Uninstall,
    [switch]$NoPath
)

$ErrorActionPreference = 'Stop'
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

$AppName    = 'Crownest'
$InstallDir = Join-Path $env:LOCALAPPDATA 'Programs\Crownest'
$ExePath    = Join-Path $InstallDir 'Crownest.exe'
$StartMenu  = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Crownest.lnk'
$DesktopLnk = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Crownest.lnk'

function Remove-FromUserPath([string]$dir) {
    $cur = [Environment]::GetEnvironmentVariable('Path', 'User')
    if (-not $cur) { return }
    $parts = $cur.Split(';') | Where-Object { $_ -and ($_ -ne $dir) }
    [Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')
}

# ---------------------------------------------------------------- uninstall

if ($Uninstall) {
    Write-Host "[*] Removing $AppName"
    foreach ($lnk in @($StartMenu, $DesktopLnk)) {
        if (Test-Path $lnk) { Remove-Item -Force $lnk }
    }
    if (Test-Path $InstallDir) { Remove-Item -Recurse -Force $InstallDir }
    Remove-FromUserPath $InstallDir
    Write-Host '[+] Uninstalled. (Config in %APPDATA%\Crownest was left untouched.)'
    return
}

# ------------------------------------------------------------------- checks

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error 'Java runtime not found. Install a JDK 17+ (e.g. Temurin/OpenJDK) and re-run.'
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    Write-Error 'jpackage not found. It ships with JDK 14+. Install a full JDK 17+ (not just a JRE).'
}

$verLine = (& java -version 2>&1 | Select-Object -First 1)
$major = 0
if ($verLine -match '(\d+)(?:\.(\d+))?') {
    $major = [int]$Matches[1]
    if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2] }   # "1.8" -> 8
}
if ($major -lt 17) { Write-Error "Java 17+ is required (found: $verLine)." }

# -------------------------------------------------------------------- build

Write-Host '[*] Building the jar'
& (Join-Path $Here 'build.ps1')
$jar = Join-Path $Here 'build\crownest.jar'
if (-not (Test-Path $jar)) { Write-Error 'Build did not produce build\crownest.jar' }

# ------------------------------------------------------------- package .exe

Write-Host '[*] Packaging Crownest.exe with a bundled runtime (jpackage)'
$stage = Join-Path $env:TEMP ("crownest-stage-" + [guid]::NewGuid().ToString('N'))
$out   = Join-Path $env:TEMP ("crownest-out-"   + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
New-Item -ItemType Directory -Force -Path $out   | Out-Null
Copy-Item $jar (Join-Path $stage 'crownest.jar')

$icon = Join-Path $Here 'resources\crownest.ico'
& jpackage --type app-image --name $AppName `
    --input $stage --main-jar crownest.jar --main-class com.crownest.Main `
    --icon $icon --dest $out
if ($LASTEXITCODE -ne 0) { Write-Error 'jpackage failed to build the application image.' }

$built = Join-Path $out $AppName
if (-not (Test-Path (Join-Path $built 'Crownest.exe'))) {
    Write-Error 'jpackage did not produce Crownest.exe'
}

# ------------------------------------------------------------------ install

Write-Host "[*] Installing to $InstallDir"
if (Test-Path $InstallDir) { Remove-Item -Recurse -Force $InstallDir }
New-Item -ItemType Directory -Force -Path (Split-Path $InstallDir) | Out-Null
Move-Item $built $InstallDir

Remove-Item -Recurse -Force $stage, $out -ErrorAction SilentlyContinue

# --------------------------------------------------------------- shortcuts

Write-Host '[*] Creating shortcuts (Start Menu + Desktop)'
$shell = New-Object -ComObject WScript.Shell
foreach ($path in @($StartMenu, $DesktopLnk)) {
    $lnk = $shell.CreateShortcut($path)
    $lnk.TargetPath       = $ExePath
    $lnk.WorkingDirectory = $InstallDir
    $lnk.IconLocation     = "$ExePath,0"
    $lnk.Description       = 'Crownest - HTTP file server and reverse-shell payload host'
    $lnk.Save()
}

# ------------------------------------------------------------------- PATH

if (-not $NoPath) {
    $cur = [Environment]::GetEnvironmentVariable('Path', 'User')
    if (($cur -split ';') -notcontains $InstallDir) {
        $new = if ([string]::IsNullOrEmpty($cur)) { $InstallDir } else { "$cur;$InstallDir" }
        [Environment]::SetEnvironmentVariable('Path', $new, 'User')
        Write-Host "[*] Added $InstallDir to your user PATH (open a new terminal to use 'Crownest')"
    }
}

Write-Host ''
Write-Host "[+] $AppName installed."
Write-Host "    Executable : $ExePath"
Write-Host "    Menu       : search `"$AppName`" in the Start Menu"
Write-Host "    Command    : Crownest   (in a new terminal)"
Write-Host "    Uninstall  : powershell -ExecutionPolicy Bypass -File `"$Here\install.ps1`" -Uninstall"
