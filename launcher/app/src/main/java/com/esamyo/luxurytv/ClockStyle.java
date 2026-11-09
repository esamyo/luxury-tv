/*
 * Luxury TV
 * Copyright (c) 2026 esamyo (Esam). MIT License, see LICENSE and NOTICE.md.
 * This notice must stay in every copy and modified version.
 */
package com.esamyo.luxurytv;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;

import org.json.JSONObject;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * clock_style: the clock screensaver as a metal clock on black instead of the coloured glow, "gold" or "silver";
 * clock_style_night gives the night another metal (gold by day and silver by night, the default), at the same
 * brightness. Night runs from sunset to sunrise at config.xml's latitude and longitude, worked out offline.
 * Nothing here loads a picture or runs an animation of its own: the colours are set once per minute at most.
 */
final class ClockStyle {

    static final String GLOW = "glow", GOLD = "gold", SILVER = "silver";
    /** A font the theme pushed for the clock (tools/theme), used instead of the bundled one when present. */
    static final String FONT_FILE = "clock_font.ttf";

    /** The colours of one look. Alpha is baked into every colour, so no view needs an offscreen layer. */
    static final class Palette {
        final int[] clock;      // top to bottom gradient of the digits
        final int ampm, date, line, glow, streak;

        Palette(int[] clock, int ampm, int date, int line, int glow, int streak) {
            // Text is drawn in solid colours: with a translucent colour the places where joined Arabic letters
            // overlap are painted twice and show as brighter seams. On black the solid colour looks the same.
            for (int i = 0; i < clock.length; i++) clock[i] = solid(clock[i]);
            this.clock = clock;
            this.ampm = solid(ampm);
            this.date = solid(date);
            this.line = line;
            this.glow = glow;
            this.streak = streak;
        }
    }

    /**
     * One brightness, day and night alike: about 72 % for the digits, date and greeting, a fainter line and streak,
     * and a glow that is barely there. Gold by default in the day and silver at night (config.xml).
     */
    /** The colour as it looks drawn at its alpha on black, fully opaque. */
    static int solid(int argb) {
        int a = argb >>> 24;
        int r = ((argb >> 16) & 0xFF) * a / 255, g = ((argb >> 8) & 0xFF) * a / 255, b = (argb & 0xFF) * a / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static final Palette GOLD_LOOK = new Palette(
            new int[]{0xB8F6E7BC, 0xB8D9B26A, 0xB8A87C3A}, 0xB8D9B26A, 0xB8C9A86A, 0x73DEBE78, 0x1AC49646, 0x66F0D7A0);
    private static final Palette SILVER_LOOK = new Palette(
            new int[]{0xB8FFFFFF, 0xB8D5DAE0, 0xB88E98A4}, 0xB8D5DAE0, 0xB8B9C1CB, 0x73D2DAE4, 0x16AAB9CD, 0x66EBF0F8);

    final String day;
    final String night;
    /** "auto", or "day" / "night" to hold one look (a preview). */
    final String timeOfDay;
    final int fontWeight;
    /** clock_greeting: "beside_date" (the default), "above_date" (between the line and the date) or "top". */
    final String greetingPlace;
    private final String latitude, longitude;

    private ClockStyle(String day, String night, String timeOfDay, int fontWeight, String greetingPlace,
                       String latitude, String longitude) {
        this.greetingPlace = greetingPlace;
        this.day = day;
        this.night = night;
        this.timeOfDay = timeOfDay;
        this.fontWeight = fontWeight;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** config.xml, then the theme's config.json on top. */
    static ClockStyle load(Context ctx) {
        Resources res = ctx.getResources();
        String day = res.getString(R.string.clock_style);
        String night = res.getString(R.string.clock_style_night);
        String time = res.getString(R.string.clock_time_of_day);
        int weight = res.getInteger(R.integer.clock_font_weight);
        String place = res.getString(R.string.clock_greeting);
        JSONObject json = Config.overlay(ctx);
        if (json != null) {
            day = json.optString("clock_style", day);
            night = json.optString("clock_style_night", night);
            time = json.optString("clock_time_of_day", time);
            weight = json.optInt("clock_font_weight", weight);
            place = json.optString("clock_greeting", place);
        }
        day = known(day, GLOW);
        night = night.trim().isEmpty() ? day : known(night, day);
        String[] where = Config.place(ctx);
        return new ClockStyle(day, night, time.trim(), Math.max(100, Math.min(900, weight)), place.trim(),
                where[0], where[1]);
    }

    private static String known(String s, String fallback) {
        s = s.trim().toLowerCase(java.util.Locale.ROOT);
        return GOLD.equals(s) || SILVER.equals(s) || GLOW.equals(s) ? s : fallback;
    }

    /** True when either look is a metal one; the plain glow screensaver needs none of this. */
    boolean used() {
        return !GLOW.equals(day) || !GLOW.equals(night);
    }

    boolean isNight() {
        if ("night".equals(timeOfDay)) return true;
        if ("day".equals(timeOfDay)) return false;
        LocalDateTime now = LocalDateTime.now();
        int[] sun = Sun.sun(now.toLocalDate(), latitude, longitude, ZoneId.systemDefault());
        int minute = now.getHour() * 60 + now.getMinute();
        return minute < sun[0] || minute >= sun[1];
    }

    /** The look for now: a Palette, or null for the coloured glow. */
    Palette current() {
        boolean night = isNight();
        String metal = night ? this.night : day;
        if (GOLD.equals(metal)) return ClockStyle.GOLD_LOOK;
        if (SILVER.equals(metal)) return ClockStyle.SILVER_LOOK;
        return null;
    }

    /** The theme's clock_font.ttf at fontWeight, or null to keep the bundled font. */
    Typeface font(Context ctx) {
        File dir = Config.dir(ctx);
        File file = dir != null ? new File(dir, FONT_FILE) : null;
        if (file == null || !file.isFile() || file.length() > 4L * 1024 * 1024) return null;
        try {
            Typeface t = new Typeface.Builder(file)
                    .setFontVariationSettings("'wght' " + fontWeight)
                    .setWeight(fontWeight)
                    .build();
            return t;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
