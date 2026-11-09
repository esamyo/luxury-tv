# Disable (never uninstall) every package in a list for user 0. -Undo turns them back on.
# Usage: tools\debloat.ps1 192.168.1.50 examples\xiaomi-mitv-afmu0\luxury.txt [-Undo]
param([Parameter(Mandatory = $true)][string]$Tv, [Parameter(Mandatory = $true)][string]$List, [switch]$Undo)
if ($Tv -notmatch ':') { $Tv = "${Tv}:5555" }
$keep = 'com.google.android.gms', 'com.google.android.gsf', 'com.google.android.webview', 'com.google.android.tv.remote.service', 'com.esamyo.luxurytv'
adb connect $Tv | Out-Null
foreach ($line in Get-Content $List) {
    $pkg = ($line -replace '#.*$', '').Trim()
    if (-not $pkg) { continue }
    if ($keep -contains $pkg) { Write-Host "skip  $pkg (needed by the system)"; continue }
    if ($Undo) {
        adb -s $Tv shell "cmd package install-existing $pkg >/dev/null 2>&1; pm enable --user 0 $pkg"
    } else {
        adb -s $Tv shell pm disable-user --user 0 $pkg
    }
}
