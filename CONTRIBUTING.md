# Contributing to Luxury TV

Thank you for helping. Luxury TV is small on purpose: a calm home screen that stays light on low-memory TVs.
Changes that keep it that way are the easiest to accept.

## Reporting a bug

[Open an issue](https://github.com/esamyo/luxury-tv/issues/new/choose) with:
- the TV or box model and its Android version (`adb shell getprop ro.product.model` and
  `adb shell getprop ro.build.version.release`);
- the Luxury TV version (`adb shell dumpsys package com.esamyo.luxurytv | grep versionName`);
- what you did, what you expected and what happened, with a screenshot (`adb exec-out screencap -p > shot.png`) if
  it is about the screen.

Leave out anything private: Wi-Fi names, account details, your list of installed apps.

## Suggesting a feature

Open an issue first and describe what you want to do on your TV. New features should not add memory, background
work or network use for people who do not turn them on.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Build and try your change on a real TV or the Android TV emulator: `tools/install.sh <tv ip>`.
3. Keep the code style of the file you are editing: plain Java, no new dependencies.
4. Every new user-facing text goes in `res/values/strings.xml`, and in the other `values-*` folders when you can
   translate it.
5. Describe the change and how you tested it in the pull request, and add a line to `CHANGELOG.md` under
   *Unreleased*.

## Licence

By contributing you agree that your contribution is released under the project's [MIT License](LICENSE), and that
the copyright lines and [NOTICE](NOTICE.md) stay as they are.
