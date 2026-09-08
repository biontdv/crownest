<#
    Build Crownest into build\crownest.jar (Windows).
    Requires a JDK 17+ (javac, jar) on PATH.
#>
$ErrorActionPreference = 'Stop'
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Here

foreach ($tool in 'javac', 'jar') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Error "$tool not found. Install a JDK 17+ and make sure it is on your PATH."
    }
}

$Out = Join-Path $Here 'build\classes'
$Jar = Join-Path $Here 'build\crownest.jar'

Write-Host '[*] Cleaning'
if (Test-Path (Join-Path $Here 'build')) { Remove-Item -Recurse -Force (Join-Path $Here 'build') }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

Write-Host '[*] Compiling'
$sources = Get-ChildItem -Recurse -Filter *.java -Path (Join-Path $Here 'src') | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -d $Out @sources

Write-Host '[*] Bundling resources'
Copy-Item (Join-Path $Here 'resources\crownest.png') $Out

Write-Host "[*] Packaging $Jar"
& jar --create --file $Jar --main-class com.crownest.Main -C $Out .

Write-Host "[+] Built $Jar"
