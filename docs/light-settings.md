# Settings for a light TV

<p><a href="#العربية">العربية</a></p>

Run with `adb shell <command>` after installing Luxury TV and the debloat list.

- Recommended: animation scales 0.5, `wifi_scan_always_enabled 0`, `ble_scan_always_enabled 0`,
  `network_recommendations_enabled 0`, `auto_time 1` and `auto_time_zone 1`, one user only.
- Optional: no Google account (no Play Store; install APKs), `location_mode 0` (some screens stop showing the
  Wi-Fi name), `always_finish_activities 1` (frees memory, apps restart when you go back), one screensaver.
- Your city: time, date and greeting follow the TV's time zone (Settings → System → Date & time). For the
  temperature, set your city's `latitude` / `longitude` in the theme's `config.json` (or in `config.xml` when you
  build): the nearest airport is used by itself.
  `weather_metar` (e.g. `EGLL` London, `KJFK` New York) picks one particular airport instead. Then push the theme again
  (`tools/theme.sh`) or rebuild.

---

<div dir="rtl">

## العربية

### إعدادات أندرويد لجهاز خفيف

بعد تثبيت Luxury TV وتطبيق قائمة التنظيف (docs/debloat.md)، هذه الإعدادات تكمّل الخفة. كلها تُرجع بنفس الطريقة.
الأوامر تُشغَّل من الكمبيوتر والتلفزيون متصل: `adb shell <الأمر>`.

### أنصح بها
| الإعداد | الأمر | الفائدة |
|---|---|---|
| تسريع الحركة 0.5x | `settings put global window_animation_scale 0.5` و `transition_animation_scale 0.5` و `animator_duration_scale 0.5` | التنقل أسرع |
| إيقاف البحث الدائم عن الواي فاي | `settings put global wifi_scan_always_enabled 0` | أقل استهلاك في الخلفية |
| إيقاف البحث الدائم عن البلوتوث | `settings put global ble_scan_always_enabled 0` | نفس السبب |
| إيقاف اقتراحات الشبكات | `settings put global network_recommendations_enabled 0` | خدمة لا يحتاجها التلفزيون |
| التاريخ والوقت تلقائي | `settings put global auto_time 1` و `auto_time_zone 1` | الساعة والتاريخ يتبعان مدينتك |
| مستخدم واحد فقط | الإعدادات ← الحسابات | لا عمليات لمستخدمين آخرين |

### اختياري (حسب استخدامك)
| الإعداد | الأمر | انتبه |
|---|---|---|
| بدون حساب Google | الإعدادات ← الحسابات ← إزالة | لا متجر Play؛ التثبيت بملفات APK |
| إيقاف الموقع | `settings put secure location_mode 0` | اسم شبكة الواي فاي لا يظهر في بعض الشاشات |
| «عدم الاحتفاظ بالأنشطة» | `settings put global always_finish_activities 1` | يحرر الذاكرة، لكن التطبيق يبدأ من جديد عند الرجوع له |
| شاشة توقف خفيفة | ساعة Luxury TV (يضبطها التثبيت) | لا تضيف ذاكرة؛ إذا اخترت غيرها اختر واحدة فقط وعطّل الباقي |

### الوقت والطقس لمدينتك
- **الساعة والتاريخ** من نظام التلفزيون: الإعدادات ← النظام ← التاريخ والوقت ← المنطقة الزمنية (أو تلقائي).
- **درجة الحرارة**: ضع إحداثيات مدينتك في `latitude` و `longitude` في ملف الثيم `themes/luxury/config.json`
  (أو في `config.xml` إذا تبني من المصدر)،
  فتأخذ الواجهة قراءة أقرب مطار لمدينتك تلقائيًا. إذا تبغى مطارًا معيّنًا ضع رمزه في `weather_metar`
  (مثال: الرياض `OERK`، جدة `OEJN`، المدينة `OEMA`، دبي `OMDB`، القاهرة `HECA`، الكويت `OKBK`). ثم أعد إرسال الثيم (`tools\theme.ps1`) أو أعد البناء.
- **التحية** تتبع لغة الواجهة ووقت بلدك: «صباح الخير» من الفجر إلى الظهر و«مساء الخير» بعده، وكل لغة بأوقاتها.

</div>
