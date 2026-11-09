package com.esamyo.luxurytv;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.esamyo.luxurytv/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the main row and the wallpaper without a rebuild:
 * {@code tools/theme.sh <tv> themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
final class Config {
    private static final String TAG = "LuxuryTV.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";

    /** Main-row order (config.xml dock_apps). */
    final List<String> dockApps;
    /** Never shown as tiles (config.xml hidden_apps). */
    final List<String> hiddenApps;
    /** Language tag the launcher shows itself in instead of the TV's, or "" for the TV's (config.xml, locale). */
    final String locale;
    /** "12", "24" or "system" (config.xml clock_format); see ClockFormat. */
    final String clockFormat;
    /** Back on the Home screen opens the clock screensaver (config.xml back_opens_clock). */
    final boolean backOpensClock;
    /** The clock screensaver's coloured glow; false leaves the clock on plain black, e.g. for an OLED (clock_glow). */
    final boolean clockGlow;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise (background_color). */
    final int backgroundColor;
    /** Full-screen picture behind the launcher (the theme's wallpaper.jpg), or null for the plain colour. */
    final File wallpaper;
    /** Decode the wallpaper in 32-bit colour instead of RGB_565 (wallpaper_true_color). */
    final boolean wallpaperTrueColor;
    /** How dark the wallpaper is drawn, 0 to 90 % (config.json wallpaper_dim). */
    final int wallpaperDim;

    private Config(List<String> dockApps, List<String> hiddenApps, String locale, String clockFormat,
                   boolean backOpensClock, boolean clockGlow, int backgroundColor, File wallpaper,
                   boolean wallpaperTrueColor, int wallpaperDim) {
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.locale = locale;
        this.clockFormat = clockFormat;
        this.backOpensClock = backOpensClock;
        this.clockGlow = clockGlow;
        this.backgroundColor = backgroundColor;
        this.wallpaper = wallpaper;
        this.wallpaperTrueColor = wallpaperTrueColor;
        this.wallpaperDim = wallpaperDim;
    }

    /** The theme's config.json as read for load(), or null without one: for settings read outside Config (ClockStyle). */
    static JSONObject overlay(Context ctx) {
        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        return readOverlay(ctx, file, Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()));
    }

    /**
     * Where the TV is: {latitude, longitude, weather_metar}, from config.xml with the theme's config.json on top, so
     * a prebuilt APK gets its city from a pushed theme. Empty strings when unset.
     */
    static String[] place(Context ctx) {
        Resources res = ctx.getResources();
        String lat = res.getString(R.string.latitude), lon = res.getString(R.string.longitude);
        String metar = res.getString(R.string.weather_metar);
        JSONObject json = overlay(ctx);
        if (json != null) {
            lat = json.optString("latitude", lat);
            lon = json.optString("longitude", lon);
            metar = json.optString("weather_metar", metar);
        }
        return new String[]{lat.trim(), lon.trim(), metar.trim()};
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        String locale = res.getString(R.string.locale).trim();
        String clockFormat = res.getString(R.string.clock_format);
        boolean backOpensClock = res.getBoolean(R.bool.back_opens_clock);
        boolean clockGlow = res.getBoolean(R.bool.clock_glow);
        boolean wallpaperTrueColor = res.getBoolean(R.bool.wallpaper_true_color);
        int background = 0xFF000000;

        JSONObject json = overlay(ctx);
        if (json != null) {
            dock = stringList(json, "dock_apps", dock);
            hidden = stringList(json, "hidden_apps", hidden);
            locale = string(json, "locale", locale).trim();
            clockFormat = string(json, "clock_format", clockFormat);
            backOpensClock = bool(json, "back_opens_clock", backOpensClock);
            clockGlow = bool(json, "clock_glow", clockGlow);
            wallpaperTrueColor = bool(json, "wallpaper_true_color", wallpaperTrueColor);
            background = color(json, "background_color", background);
        }
        File dir = dir(ctx);
        File wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (wallpaper != null && !(wallpaper.isFile() && wallpaper.length() > 0)) wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        if (json != null && !bool(json, "wallpaper", true)) wallpaper = null;
        int wallpaperDim = json == null ? 0 : integer(json, "wallpaper_dim", 0, 0, 90);
        return new Config(dock, hidden, locale, clockFormat, backOpensClock, clockGlow, background, wallpaper,
                wallpaperTrueColor, wallpaperDim);
    }

    /**
     * The overlay, or before external storage is mounted, the copy kept from the last time it was read. Home can
     * start before the card is mounted (seen at every boot on a Xiaomi MiTV-MTEQ0), and without the copy it comes
     * up with the built-in dock order. A missing file on mounted storage still means no overlay.
     */
    private static JSONObject readOverlay(Context ctx, File file, boolean mounted) {
        File copy = new File(ctx.getFilesDir(), FILE_NAME);
        if (!mounted) return read(copy);
        JSONObject json = read(file);
        if (json == null) {
            copy.delete();
        } else {
            try (FileOutputStream out = new FileOutputStream(copy)) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "could not keep a copy of " + file + ": " + e.getMessage());
            }
        }
        return json;
    }

    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
