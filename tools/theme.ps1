# Send a theme (config.json and wallpaper.jpg) to Luxury TV and restart the home screen.
# Usage: tools\theme.ps1 192.168.1.50 themes\luxury
param([Parameter(Mandatory = $true)][string]$Tv, [Parameter(Mandatory = $true)][string]$Theme)
$ErrorActionPreference = 'Stop'
if ($Tv -notmatch ':') { $Tv = "${Tv}:5555" }
$uri = 'content://com.esamyo.luxurytv.theme'
adb connect $Tv | Out-Null
foreach ($f in 'config.json', 'wallpaper.jpg') {
    $p = Join-Path $Theme $f
    if (Test-Path $p) {
        adb -s $Tv push $p "/data/local/tmp/lt-$f" | Out-Null
        adb -s $Tv shell "content write --uri $uri/$f < /data/local/tmp/lt-$f; rm /data/local/tmp/lt-$f"
    }
}
$rev = [DateTimeOffset]::Now.ToUnixTimeSeconds()
adb -s $Tv shell "echo $rev > /data/local/tmp/lt-rev; content write --uri $uri/theme_revision < /data/local/tmp/lt-rev; rm /data/local/tmp/lt-rev"
adb -s $Tv shell 'am force-stop com.esamyo.luxurytv; input keyevent HOME'
Write-Host 'Theme applied.'
