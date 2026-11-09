# Security policy

## Supported versions

Only the [latest release](https://github.com/esamyo/luxury-tv/releases/latest) receives fixes.

## Reporting a vulnerability

Please do not open a public issue. Report it privately through
[GitHub's security advisories](https://github.com/esamyo/luxury-tv/security/advisories/new), with the steps to
reproduce it and the device and Android version. You will get an answer within a week.

## What Luxury TV does on your TV

- No accounts, analytics, ads or trackers.
- The only network use is the weather, and only when a city or airport is set: aviationweather.gov, then Open-Meteo.
- The theme provider (`ThemeProvider`) accepts writes only from the adb shell (`WRITE_SECURE_SETTINGS`), and only
  the theme's own files.
- The optional Home-button helper (`HomeKeyService`) reads only the Home and Settings keys and lets every other key
  pass through untouched.
