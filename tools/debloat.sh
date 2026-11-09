#!/bin/sh
# Disable (never uninstall) every package in a list for user 0. --undo turns them back on.
# Usage: tools/debloat.sh 192.168.1.50 examples/xiaomi-mitv-afmu0/luxury.txt [--undo]
TV="${1:?usage: tools/debloat.sh <tv ip> <list> [--undo]}"; LIST="${2:?list file}"
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
KEEP=" com.google.android.gms com.google.android.gsf com.google.android.webview com.google.android.tv.remote.service com.esamyo.luxurytv "
adb connect "$TV" >/dev/null
sed 's/#.*//' "$LIST" | while read -r pkg; do
  [ -n "$pkg" ] || continue
  case "$KEEP" in *" $pkg "*) echo "skip  $pkg (needed by the system)"; continue ;; esac
  if [ "$3" = --undo ]; then
    adb -s "$TV" shell "cmd package install-existing $pkg >/dev/null 2>&1; pm enable --user 0 $pkg" </dev/null
  else
    adb -s "$TV" shell pm disable-user --user 0 "$pkg" </dev/null
  fi
done
