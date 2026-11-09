# Shrink, optimise and rename: the release APK stays as small as it can be. Android keeps the names of
# everything the manifest declares (the activity, the screensaver, the Home-key service, the theme
# provider), so the adb commands in the docs keep working. Each release ships its mapping.txt, which
# turns a renamed crash trace back into readable class and method names.
-repackageclasses ''
-allowaccessmodification
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
