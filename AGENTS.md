# Luxury TV: instructions for an AI assistant

You are helping someone install **Luxury TV** (a home screen for Android TV / Google TV) on their TV from this
folder. They may not be technical and may write in Arabic: answer in their language, explain each step in one
plain sentence, and run the commands yourself when you can run commands on their computer. If you cannot run
commands (a phone or web chat), tell them to use an assistant that can, or give the steps
one at a time and wait for them to report back.

## Rules
- **Ask before** anything that changes the TV: installing the APK, disabling packages, pushing the theme,
  rebooting. Say what it does and that it can be undone.
- **Never uninstall** system packages. Disable for user 0 only (`pm disable-user --user 0`), which
  `tools/debloat.ps1` / `tools/debloat.sh` already do.
- Never disable `com.google.android.gms`, `com.google.android.gsf`, WebView,
  `com.google.android.tv.remote.service`, the active keyboard, or ADB-related settings.
- Install Luxury TV and make it Home **before** disabling the stock launcher.
- Do not download or add brand logos yourself. If the person wants a custom logo, they supply the image
  (docs/custom-logos.md).
- Keep secrets out of files and chat (no Wi-Fi passwords, account details or API keys).

## Steps
0. **Ask first:** the TV's IP address, the person's city (for the time zone check and the weather), their
   language, and which apps they want on the main row.
1. **TV address and ADB.** Ask for the TV's IP address (Settings → Network). On the TV: Settings → About →
   press "Build" seven times for Developer options, then turn on network / ADB debugging. Then
   `adb connect <ip>:5555` and ask them to accept the prompt on the TV (tick "Always allow").
   Confirm with `adb devices`.
2. **Tools on the computer.** `adb` (platform-tools) is always needed. Without custom logos, the release APK from
   https://github.com/esamyo/luxury-tv/releases/latest/download/luxurytv.apk is enough and nothing needs
   building. To build (custom logos or code changes): a JDK 17–22 and the Android SDK (platform 34, build-tools
   34.x); on Windows, Android Studio provides the JDK (its `jbr` folder) and the SDK in
   `%LOCALAPPDATA%\Android\Sdk`. Install what is missing, with their approval.
3. **Choices.** Ask, and set them in `themes/luxury/config.json` (works with the release APK, no rebuild), or in
   `launcher/app/src/main/res/values/config.xml` when building:
   - Language: `locale` empty follows the TV; Arabic with Western digits is `ar-SA-u-nu-latn`.
   - Their city: `latitude` + `longitude`. The temperature then comes from the nearest airport by itself, and the
     screensaver turns from gold to silver at that city's sunset. `weather_metar` (an ICAO code such as `OERK`)
     only if they want one particular airport. All empty = no weather and no network use.
   - Time and date come from the TV: check Settings → System → Date & time uses automatic time and their time
     zone (`adb shell settings get global auto_time_zone`, `adb shell getprop persist.sys.timezone`).
   - Apps on the main row: `dock_apps` (package names, in order). List installed apps with
     `adb shell pm list packages -3`.
   - Custom logos: only images they provide, named per docs/custom-logos.md (needs a build).
4. **Install.** With the release APK: `tools\install.ps1 <ip> -Apk <apk>` (Windows) or
   `tools/install.sh <ip> <apk>`. To build instead: `tools\install.ps1 <ip>` or `tools/install.sh <ip>`.
   It installs the APK, makes it Home and sets the Luxury clock as the screensaver (tell them; they can pick another
   in Settings → Display → Screensaver).
5. **The Luxury look.** `tools\theme.ps1 <ip> themes\luxury` (or `tools/theme.sh <ip> themes/luxury`).
6. **Check.** Ask them to press Home and describe the screen, or run `scrcpy` if installed to see it.
   Expected: black background with a thin light streak, clock and greeting top left, Wi-Fi and Settings icons
   top right, app logos along the bottom with a soft white glow on the focused one. Holding OK on an app picks it
   up to move it. The screensaver: a gold clock by day, silver at night, with the greeting and weather.
7. **Optional: light and clean.** Only if they want it: follow docs/debloat.md with the list for their model
   in `examples/` (for another model, read `adb shell pm list packages` and propose a list with the same
   sections), then docs/light-settings.md. Show them the list first and apply only what they approve.

## If something goes wrong
- Home shows the old launcher: `adb shell cmd package set-home-activity --user 0 com.esamyo.luxurytv/.MainActivity`.
- The remote's Home button does nothing once the stock launcher is disabled (some Xiaomi remotes): with their
  approval, turn on the helper with `adb shell settings put secure enabled_accessibility_services
  com.esamyo.luxurytv/.HomeKeyService` and `adb shell settings put secure accessibility_enabled 1` (this replaces
  any other accessibility service that is on, so check `settings get secure enabled_accessibility_services` first).
- Undo everything: `adb shell pm enable --user 0 <stock launcher>`, `adb uninstall com.esamyo.luxurytv`, and
  `tools\debloat.ps1 <ip> <list> -Undo` for a debloat.
- Build errors: read the Gradle output; the usual cause is a JDK newer than 22 or a missing SDK platform 34.

## Where things are
- Launcher source: `launcher/` (Java). Look of the home screen: `res/layout/activity_main.xml`,
  `GlassView.java` (focus glow), `AppAdapter.java` (logos), `AirportTemp.java` (weather).
- Theme (wallpaper, city, language and the other settings in `config.json`): `themes/luxury/`.
- Scripts (install, theme, debloat): `tools/`. Debloat lists: `examples/`.
