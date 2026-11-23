# Launches TFT on the USB-connected phone and saves screenshots on an interval.
# Usage (PowerShell): .\scripts\adb-capture.ps1 -Count 20 -IntervalSec 5
param(
    [int]$Count = 20,
    [int]$IntervalSec = 5,
    [int]$LaunchWaitSec = 25,
    [string]$OutDir = "$PSScriptRoot\..\captures"
)

$ErrorActionPreference = "Stop"
$pkg = "com.riotgames.league.teamfighttactics"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb not found on PATH" }
$devices = (adb devices) -match "\tdevice$"
if (@($devices).Count -ne 1) { throw "expected exactly one authorized device, found $(@($devices).Count)" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Launched TFT, waiting $LaunchWaitSec s..."
Start-Sleep -Seconds $LaunchWaitSec

for ($i = 1; $i -le $Count; $i++) {
    $file = Join-Path $OutDir ("tft-{0}-{1:D3}.png" -f (Get-Date -Format "yyyyMMdd-HHmmss"), $i)
    cmd /c "adb exec-out screencap -p > `"$file`""
    Write-Host "saved $file"
    Start-Sleep -Seconds $IntervalSec
}
