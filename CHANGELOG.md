# Changelog

All notable changes to Luxury TV are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-09

The first public release.

### Added
- Home screen: black background with a thin streak of light, frameless app logos, a soft white focus glow that
  springs between apps, and the app in focus growing.
- Header: clock, greeting with the temperature and a weather icon, and the date, with bare Wi-Fi and Settings icons.
- Weather from the airport nearest your city (METAR from aviationweather.gov), with Open-Meteo as the fallback.
- Greetings by language and country, at the hours people there use them.
- Moving apps: hold OK (or press Menu) on an app, move it with the arrows, press OK; the order is kept.
- Clock screensaver: gold by day, silver at night, switching at your city's sunrise and sunset; Back on Home opens it.
- Themes: `config.json` and `wallpaper.jpg` pushed with `tools/theme.ps1` / `tools/theme.sh`, including the city and
  language, so a downloaded APK needs no rebuild.
- Your own logo for any app (`logo_<package>.png`).
- Optional Home-button helper for Xiaomi remotes (`HomeKeyService`).
- Vazirmatn typeface, and translations into 31 languages and variants.
- Install on the TV itself with the Downloader app and a short code, or from a computer with the scripts.
- Releases built and signed on GitHub, with the APK's SHA-256 and the R8 mapping file attached.
- Install, theme and debloat scripts for Windows, macOS and Linux, and a tested debloat list for the Xiaomi TV Box S.

[1.0.0]: https://github.com/esamyo/luxury-tv/releases/tag/v1.0.0
