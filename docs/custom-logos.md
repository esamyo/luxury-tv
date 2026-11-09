# Custom app logos

<p><a href="#العربية">العربية</a></p>

1. Find the app's package: `adb shell pm list packages -3`.
2. Prepare a transparent PNG, about 400 px tall. Use a light logo: a black one disappears on the black background. Pad a heavy, solid logo with transparent space so it balances with the others.
3. Name it `logo_` + the package, lowercase, with dots (and any other symbol) as `_`: `org.example.player` → `logo_org_example_player.png`.
4. Put it in `launcher/app/src/main/res/drawable-nodpi/`.
5. Rebuild and install: `tools\install.ps1 <tv ip>`.

Logos are built into the APK, so this needs a build from source (see the README, Option 2). Delete the file and
rebuild to go back to the app's own icon. No brand logos ship with this project; a trademark you add is for your own device, at your own responsibility.

---

<div dir="rtl">

## العربية

### شعارات مخصصة للتطبيقات

Luxury TV يعرض لكل تطبيق أيقونته بدون مربع. تقدر تستبدل أيقونة أي تطبيق بشعار من اختيارك.

1. **اعرف اسم حزمة التطبيق.** من الكمبيوتر والتلفزيون متصل:
   ```powershell
   adb shell pm list packages -3
   ```
   تطلع أسماء مثل `org.example.player`.
2. **جهّز الشعار.** صورة PNG بخلفية شفافة، ويُفضّل أن يكون ارتفاعها 400 بكسل تقريبًا. الشعار العريض أو المربع كلاهما يصلح، فالواجهة تضعه في الوسط بحجم موحد.
   - على الخلفية السوداء استخدم شعارًا **فاتح اللون** (أبيض أو ملوّن). الشعار الأسود لن يظهر.
   - إذا كان الشعار ثقيلًا (كتلة لون كاملة)، أضف حوله مساحة شفافة حتى يتوازن مع باقي الشعارات.
3. **سمّه** `logo_` + اسم الحزمة مع تحويل النقاط وأي رمز آخر إلى `_`، وبحروف صغيرة:
   `org.example.player` ← `logo_org_example_player.png`
4. **ضعه في** `launcher/app/src/main/res/drawable-nodpi/`
5. **أعد البناء والتثبيت**:
   ```powershell
   tools\install.ps1 <عنوان التلفزيون>
   ```

الشعارات تُبنى داخل ملف APK، فهذه الطريقة تحتاج البناء من المصدر (الطريقة 2 في README). لإرجاع الأيقونة الأصلية: احذف الملف وأعد البناء.

> لا يوزّع هذا المشروع شعارات أي شركة. استخدامك لشعار علامة تجارية يكون لجهازك الشخصي وعلى مسؤوليتك.

</div>
