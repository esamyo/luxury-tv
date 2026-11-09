# Install Luxury TV on a TV and make it the home screen and the screensaver.
# Usage:
#   tools\install.ps1 192.168.1.50                         builds from this folder, then installs
#   tools\install.ps1 192.168.1.50 -Apk luxurytv.apk       installs a downloaded release, no build
param(
    [Parameter(Mandatory = $true)][string]$Tv,
    [string]$Apk
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ($Tv -notmatch ':') { $Tv = "${Tv}:5555" }
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw 'adb not found: install Android platform-tools and add it to PATH' }

if (-not $Apk) {
    if (-not $env:JAVA_HOME) {
        $jbr = 'C:\Program Files\Android\Android Studio\jbr'
        if (Test-Path "$jbr\bin\java.exe") { $env:JAVA_HOME = $jbr } else { throw 'Install Android Studio or set JAVA_HOME to a JDK 17+' }
    }
    $launcher = Join-Path $root 'launcher'
    if (-not (Test-Path "$launcher\local.properties")) {
        $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
        "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii "$launcher\local.properties"
    }
    Push-Location $launcher
    .\gradlew.bat assembleRelease -q --console=plain
    $ok = $LASTEXITCODE -eq 0
    Pop-Location
    if (-not $ok) { throw 'Build failed' }
    $Apk = "$launcher\app\build\outputs\apk\release\app-release.apk"
}
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

adb connect $Tv | Out-Null
adb -s $Tv install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed' }
adb -s $Tv shell cmd package set-home-activity --user 0 com.esamyo.luxurytv/.MainActivity
# The Luxury clock as the screensaver: gold by day, silver at night, with the greeting and weather.
adb -s $Tv shell settings put secure screensaver_enabled 1
adb -s $Tv shell settings put secure screensaver_components com.esamyo.luxurytv/.ClockDream
adb -s $Tv shell input keyevent HOME
Write-Host 'Luxury TV is installed, set as Home and as the screensaver.'
