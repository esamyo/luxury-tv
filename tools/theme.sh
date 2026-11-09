#!/bin/sh
# Send a theme (config.json and wallpaper.jpg) to Luxury TV and restart the home screen.
# Usage: tools/theme.sh 192.168.1.50 themes/luxury
set -e
TV="${1:?usage: tools/theme.sh <tv ip> <theme folder>}"; THEME="${2:?theme folder}"
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
URI=content://com.esamyo.luxurytv.theme
adb connect "$TV" >/dev/null
for f in config.json wallpaper.jpg; do
  [ -f "$THEME/$f" ] || continue
  adb -s "$TV" push "$THEME/$f" "/data/local/tmp/lt-$f" >/dev/null
  adb -s "$TV" shell "content write --uri $URI/$f < /data/local/tmp/lt-$f; rm /data/local/tmp/lt-$f"
done
adb -s "$TV" shell "echo $(date +%s) > /data/local/tmp/lt-rev; content write --uri $URI/theme_revision < /data/local/tmp/lt-rev; rm /data/local/tmp/lt-rev"
adb -s "$TV" shell 'am force-stop com.esamyo.luxurytv; input keyevent HOME'
echo "Theme applied."
