# Debloat

<p><a href="#العربية">العربية</a></p>

Everything is disabled for user 0 (`pm disable-user`), never uninstalled, and comes back with one command.

1. Install platform-tools (adb) and enable network debugging on the TV.
2. Pick a list in `examples/` (`examples/xiaomi-mitv-afmu0/luxury.txt` is tested on a Xiaomi TV Box S 3rd gen). For another TV, have your AI assistant read `adb shell pm list packages` and propose a list with the same sections. Read it and remove anything you want to keep.
3. Apply: `tools\debloat.ps1 <tv ip> <list>` (macOS/Linux: `tools/debloat.sh`). Undo: add `-Undo` (`--undo`).

Never disable `com.google.android.gms`, `com.google.android.gsf`, WebView, `com.google.android.tv.remote.service` or your keyboard. On Google TV, re-join Wi-Fi from Settings before disabling `setupwraith`. Install Luxury TV as Home before disabling the stock launcher. Re-check the list after system updates.

---

<div dir="rtl">

## العربية

### تنظيف الجهاز وتخفيفه

الهدف: جهاز نظيف وخفيف بدون إعلانات وتتبع وتطبيقات لا تستخدمها، **بدون حذف أي شيء**. كل التطبيقات تُعطَّل فقط للمستخدم (`pm disable-user`)، ويرجع أي واحد منها بأمر واحد.

### قبل البدء
- فعّل تصحيح الأخطاء عبر الشبكة (ADB) في التلفزيون، وثبّت [platform-tools](https://developer.android.com/tools/releases/platform-tools) (فيه adb) في الكمبيوتر.
- ابحث عن قائمة جاهزة لجهازك في `examples/`. لجهاز Xiaomi TV Box S الجيل الثالث قائمة مجرّبة: `examples/xiaomi-mitv-afmu0/luxury.txt`. لجهاز آخر، اطلب من المساعد الذكي يقرأ التطبيقات المثبتة (`adb shell pm list packages`) ويقترح قائمة بنفس الأقسام، وراجعها قبل التطبيق.
- اقرأ القائمة قبل تطبيقها، واحذف منها أي تطبيق تريد إبقاءه (سطر لكل حزمة، و`#` للتعليق).

### التطبيق
```powershell
tools\debloat.ps1 <عنوان التلفزيون> examples\xiaomi-mitv-afmu0\luxury.txt
```

### التراجع
```powershell
tools\debloat.ps1 <عنوان التلفزيون> examples\xiaomi-mitv-afmu0\luxury.txt -Undo
```
أو لحزمة واحدة: `adb shell pm enable --user 0 <اسم الحزمة>`

### قواعد مهمة
- **لا تعطّل** خدمات يحتاجها النظام: `com.google.android.gms` و `com.google.android.gsf` و WebView و `com.google.android.tv.remote.service` (ريموت الجوال)، ولوحة المفاتيح التي تستخدمها.
- في أجهزة Google TV: إذا كانت شبكة الواي فاي محفوظة من معالج الإعداد (`setupwraith`)، **انسَ الشبكة وأعد الاتصال بها من الإعدادات** قبل تعطيل `setupwraith`، وإلا ينقطع الواي فاي وADB.
- ثبّت Luxury TV واجعله الواجهة الرئيسية **قبل** تعطيل الواجهة الأصلية (`launcherx` أو `tvlauncher`).
- تحديثات النظام قد تعيد تفعيل بعض الحزم: راجع القائمة بعد كل تحديث.

### خطوات إضافية اختيارية لجهاز أخف
- خيارات المطور ← سرعة الرسوم المتحركة `0.5x`.
- إزالة حساب Google من الجهاز إذا لم تكن تحتاج متجر Play، والتثبيت بملفات APK.
- إبقاء مستخدم واحد فقط على الجهاز.

</div>
