package com.esamyo.luxurytv;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    /** Every three minutes the clock moves (and the glow shifts): often enough against burn-in, calm to watch. */
    private static final long MOVE_MS = 180_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final ImageView ambient;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_glow false: plain black replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_style gold or silver: the look, the views it colours, and the look now showing (null: the glow). */
    private ClockStyle style;
    private ClockStyle.Palette palette;
    private TextView clock, ampm, date, greeting;
    private Context styleContext;
    /** beside_date: the row holding greeting and date, and the short line between them. */
    private View dateLine, dateSeparator;
    private final Runnable weatherChanged = this::updateGreeting;
    private View hairline, streak;
    private int clockColor, ampmColor, dateColor;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            if (style == null || palette == null) shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        if (greeting != null) {
            AirportTemp.init(styleContext);   // the last reading at once, then a fresh one every half hour
            AirportTemp.start(weatherChanged);
            updateGreeting();
        }
    }

    void stop() {
        if (greeting != null) AirportTemp.stopIfOwner(weatherChanged);   // never Home's own updates
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
    }

    /**
     * clock_glow false: plain black behind the clock (OLED), with no coloured glow; the clock itself still moves.
     * Call before start().
     */
    void applySettings(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        if (!cfg.clockGlow) {
            wallpaperShown = true;   // also stops the glow's colour cycle
            ambient.setBackground(null);
        }
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            if (style != null) {
                applyLook(style.current());   // day and night change while the clock is hidden
                updateGreeting();
            }
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (streak != null && streak.getVisibility() == View.VISIBLE) placeStreak(parent, x + content.getWidth() / 2f);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    /**
     * clock_style: colour the clock, date and AM/PM in gold or silver, centre them over a thin line, and add a faint
     * streak of light like Home's. Only views and two gradients: no picture, no animation of its own.
     * Call after applySettings and before start().
     */
    void useStyle(Context ctx) {
        ClockStyle s;
        try {
            s = ClockStyle.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        if (!s.used() || !(content instanceof LinearLayout)) return;
        LinearLayout box = (LinearLayout) content;
        if (box.getChildCount() < 2 || !(box.getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) box.getChildAt(0);
        if (row.getChildCount() < 2 || !(row.getChildAt(0) instanceof TextView)
                || !(row.getChildAt(1) instanceof TextView) || !(box.getChildAt(1) instanceof TextView)) return;
        style = s;
        clock = (TextView) row.getChildAt(0);
        ampm = (TextView) row.getChildAt(1);
        date = (TextView) box.getChildAt(1);
        clockColor = clock.getCurrentTextColor();
        ampmColor = ampm.getCurrentTextColor();
        dateColor = date.getCurrentTextColor();
        float dp = ctx.getResources().getDisplayMetrics().density;

        styleContext = ctx;
        ampm.setVisibility(View.GONE);   // no AM/PM beside the clock
        // The greeting with the temperature from the nearest airport, above the clock, as on Home.
        greeting = new TextView(ctx);
        greeting.setTypeface(UiFont.get(ctx, R.string.font_regular));
        greeting.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        // Room under the line for Arabic letters that dip below it (the ح in صباح would be cut and read as ج).
        greeting.setIncludeFontPadding(true);
        if (android.os.Build.VERSION.SDK_INT >= 28) greeting.setFallbackLineSpacing(true);
        int below = Math.round(6 * ctx.getResources().getDisplayMetrics().density);
        greeting.setPadding(0, 0, 0, below);
        date.setIncludeFontPadding(true);
        if (android.os.Build.VERSION.SDK_INT >= 28) date.setFallbackLineSpacing(true);
        date.setPadding(0, 0, 0, below);
        greeting.setSingleLine(true);
        greeting.setGravity(Gravity.CENTER_HORIZONTAL);
        float density = ctx.getResources().getDisplayMetrics().density;
        if ("top".equals(s.greetingPlace)) {
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            gl.gravity = Gravity.CENTER_HORIZONTAL;
            gl.bottomMargin = Math.round(10 * density);
            box.addView(greeting, 0, gl);
        } else if ("above_date".equals(s.greetingPlace)) {
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            gl.gravity = Gravity.CENTER_HORIZONTAL;
            gl.bottomMargin = Math.round(8 * density);
            box.addView(greeting, box.indexOfChild(date), gl);
        } else {
            // beside_date: one line under the clock, the greeting first (on the right in Arabic), a short
            // upright line, then the date, like the header on Home.
            int at = box.indexOfChild(date);
            box.removeView(date);
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            // Left to right on screen whatever the language; the greeting goes where reading starts: on the
            // right in Arabic (date on the left), on the left in English.
            line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            boolean rtl = android.text.TextUtils.getLayoutDirectionFromLocale(
                    ctx.getResources().getConfiguration().getLocales().get(0)) == View.LAYOUT_DIRECTION_RTL;
            dateSeparator = new View(ctx);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Math.max(1, Math.round(density)),
                    Math.round(16 * density));
            sl.leftMargin = Math.round(14 * density);
            sl.rightMargin = Math.round(14 * density);
            View first = rtl ? date : greeting, last = rtl ? greeting : date;
            line.addView(first, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            line.addView(dateSeparator, sl);
            line.addView(last, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            ll.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(line, at, ll);
            dateLine = line;
        }

        Typeface font = s.font(ctx);
        if (font != null) {
            clock.setTypeface(font);
            ampm.setTypeface(font);
            date.setTypeface(font);
            greeting.setTypeface(font);
        }
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        ViewGroup.MarginLayoutParams dl = (ViewGroup.MarginLayoutParams) date.getLayoutParams();
        dl.setMarginStart(0);
        dl.topMargin = 0;
        date.setLayoutParams(dl);
        date.setLetterSpacing(0.04f);
        date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);   // in proportion to the greeting above
        date.setAllCaps(false);

        hairline = new View(ctx);
        hairline.setVisibility(View.GONE);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Math.round(180 * dp), Math.max(1, Math.round(dp / 2)));
        // The clock's box reaches the font's full descent (deep for Arabic letters), which digits never use: pull the
        // line up by that empty space so it sits close under the digits.
        hl.topMargin = Math.round(18 * dp - clock.getPaint().getFontMetrics().descent);
        hl.bottomMargin = Math.round(12 * dp);
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        int under = dateLine != null ? box.indexOfChild(dateLine) : box.indexOfChild("above_date".equals(s.greetingPlace) ? greeting : date);
        box.addView(hairline, under, hl);

        View parent = (View) content.getParent();
        if (parent instanceof FrameLayout) {
            streak = new View(ctx);
            streak.setVisibility(View.GONE);
            streak.setPivotX(0);
            streak.setPivotY(0);
            ((FrameLayout) parent).addView(streak, 1,
                    new FrameLayout.LayoutParams(Math.round(380 * dp), Math.max(1, Math.round(dp * 0.75f))));
        }
        clock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) shadeClock();
        });
        applyLook(s.current());
    }

    /** One look, or null to put the coloured glow back (a day or night set to "glow"). */
    private void applyLook(ClockStyle.Palette p) {
        if (p == palette && clock != null) return;
        palette = p;
        boolean metal = p != null;
        if (hairline != null) hairline.setVisibility(metal ? View.VISIBLE : View.GONE);
        if (streak != null) streak.setVisibility(metal ? View.VISIBLE : View.GONE);
        if (greeting != null) greeting.setVisibility(metal ? View.VISIBLE : View.GONE);
        if (dateSeparator != null) dateSeparator.setVisibility(metal ? View.VISIBLE : View.GONE);
        if (!metal) {
            clock.getPaint().setShader(null);
            clock.setTextColor(clockColor);
            ampm.setTextColor(ampmColor);
            date.setTextColor(dateColor);
            if (!wallpaperShown) setGlow(PALETTE[colorIndex]);
            clock.invalidate();
            return;
        }
        ampm.setTextColor(p.ampm);
        date.setTextColor(p.date);
        if (dateSeparator != null) dateSeparator.setBackgroundColor(p.line);
        if (greeting != null) {
            greeting.setTextColor(p.date);
            updateGreeting();   // its weather icon is drawn in the text colour
        }
        clock.setTextColor(p.clock[0]);
        shadeClock();
        hairline.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{p.line & 0x00FFFFFF, p.line, p.line & 0x00FFFFFF}));
        if (streak != null) {
            streak.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{p.streak & 0x00FFFFFF, p.streak, p.streak & 0x00FFFFFF}));
        }
        if (!wallpaperShown) {
            int c = p.glow & 0x00FFFFFF, a = p.glow >>> 24;
            glow.setColors(new int[]{p.glow, ((a * 2 / 5) << 24) | c, c});
        }
    }

    /** The greeting for this hour in the TV's language (Greeting), then the temperature if there is one. */
    private void updateGreeting() {
        if (greeting == null || styleContext == null) return;
        java.util.Locale locale = styleContext.getResources().getConfiguration().getLocales().get(0);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        greeting.setText(styleContext.getString(Greeting.res(locale, hour)));
        greeting.append(AirportTemp.suffix(greeting));
    }

    /** The digits' top-to-bottom metal gradient, sized to the clock's height. */
    private void shadeClock() {
        if (palette == null || clock == null) return;
        int h = clock.getHeight();
        if (h <= 0) return;
        float[] stops = palette.clock.length == 3 ? new float[]{0f, 0.55f, 1f} : null;
        clock.getPaint().setShader(new LinearGradient(0, 0, 0, h, palette.clock, stops, Shader.TileMode.CLAMP));
        clock.invalidate();
    }

    /** The streak in the top corner away from the clock, so it never crosses the digits and never sits still long. */
    private void placeStreak(View parent, float clockCentreX) {
        int w = parent.getWidth();
        if (w <= 0) return;
        float len = streak.getLayoutParams().width;
        float y = parent.getHeight() * (0.16f + 0.06f * random.nextFloat());
        boolean right = clockCentreX < w / 2f;
        float inset = w * (0.02f + 0.03f * random.nextFloat());
        if (right) {
            streak.setRotation(-24f);
            streak.setX(w - inset - len * 0.91f);
        } else {
            streak.setRotation(-156f);
            streak.setX(inset + len * 0.91f);
        }
        streak.setY(y);
    }

    private void shiftGlow() {
        if (wallpaperShown) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
