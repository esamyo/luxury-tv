package com.esamyo.luxurytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Temperature and a weather icon after the greeting. config.xml weather_metar names an airport (ICAO, e.g.
 * OEMA) read first from its METAR; latitude and longitude feed Open-Meteo as the fallback. Both empty: no
 * weather and no network use. The last reading is kept, so it shows at once after a restart.
 */
final class AirportTemp {
    private static final long EVERY_MS = 30 * 60_000L;
    private static final long RETRY_MS = 2 * 60_000L;
    /** A kept reading older than this is not shown while a fresh one is on its way. */
    private static final long KEEP_MS = 3 * 60 * 60_000L;
    private static final int TIMEOUT_MS = 6_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static HandlerThread thread;
    private static volatile Handler bg;
    private static volatile Runnable onChange;
    private static volatile String value = "";
    private static volatile int kind = WeatherIcon.SUN;
    private static volatile String metarUrl;
    private static volatile String meteoUrl;
    private static volatile SharedPreferences prefs;
    /** No weather_metar but a place: the nearest airport reporting around latitude and longitude is used. */
    private static volatile double nearLat = Double.NaN, nearLon = Double.NaN;

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            Object[] r = read();
            String v = r == null ? null : (String) r[1];
            if (v != null) {
                kind = (Integer) r[0];
                value = v;
                SharedPreferences p = prefs;
                if (p != null) {
                    p.edit().putString("weather_value", v).putInt("weather_kind", kind)
                            .putLong("weather_time", System.currentTimeMillis()).apply();
                }
                MAIN.post(() -> {
                    Runnable cb = onChange;
                    if (cb != null) cb.run();
                });
            }
            Handler h = bg;
            if (h != null) h.postDelayed(this, v != null ? EVERY_MS : RETRY_MS);
        }
    };

    private AirportTemp() {}

    /** Reads the settings (config.xml and the theme) once and puts back the last reading, so the greeting has it before the network answers. */
    static synchronized void init(Context ctx) {
        if (prefs != null) return;
        String[] place = Config.place(ctx);
        String lat = place[0], lon = place[1], station = place[2];
        metarUrl = station.isEmpty() ? null
                : "https://aviationweather.gov/api/data/metar?ids=" + station + "&format=json";
        if (metarUrl == null && !lat.isEmpty() && !lon.isEmpty()) {
            // Every user gets the airport of their own city: the reports within about 100 km, nearest first.
            try {
                double la = Double.parseDouble(lat), lo = Double.parseDouble(lon);
                nearLat = la;
                nearLon = lo;
                metarUrl = String.format(Locale.ROOT,
                        "https://aviationweather.gov/api/data/metar?bbox=%.2f,%.2f,%.2f,%.2f&format=json",
                        la - 1, lo - 1, la + 1, lo + 1);
            } catch (NumberFormatException ignored) {
                // not a number: Open-Meteo alone, as before
            }
        }
        meteoUrl = lat.isEmpty() || lon.isEmpty() ? null
                : "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day";
        prefs = ctx.getApplicationContext().getSharedPreferences("weather", Context.MODE_PRIVATE);
        if (value.isEmpty() && System.currentTimeMillis() - prefs.getLong("weather_time", 0) < KEEP_MS) {
            value = prefs.getString("weather_value", "");
            kind = prefs.getInt("weather_kind", WeatherIcon.SUN);
        }
    }

    static synchronized void start(Runnable cb) {
        onChange = cb;
        if (metarUrl == null && meteoUrl == null) return;   // no weather configured
        if (thread == null) {
            thread = new HandlerThread("airport-temp");
            thread.start();
            bg = new Handler(thread.getLooper());
        }
        bg.removeCallbacks(TICK);
        bg.post(TICK);
    }

    /** Stops only if cb is still the one being told: the screensaver closing must not stop Home's updates. */
    static synchronized void stopIfOwner(Runnable cb) {
        if (onChange == cb) stop();
    }

    static synchronized void stop() {
        onChange = null;
        if (thread != null) {
            bg.removeCallbacks(TICK);
            bg = null;
            thread.quitSafely();
            thread = null;
        }
    }

    /** Icon and temperature after the greeting once a reading has arrived, otherwise empty. */
    static CharSequence suffix(TextView tv) {
        String v = value;
        if (v.isEmpty()) return "";
        int size = Math.round(tv.getTextSize() * 1.25f);
        Bitmap b = WeatherIcon.draw(kind, size, tv.getCurrentTextColor());
        BitmapDrawable d = new BitmapDrawable(tv.getResources(), b);
        d.setAlpha(android.graphics.Color.alpha(tv.getCurrentTextColor()));
        d.setBounds(0, 0, size, size);
        SpannableString s = new SpannableString(" \uFFFC " + v);
        s.setSpan(new ImageSpan(d, android.os.Build.VERSION.SDK_INT >= 29 ? ImageSpan.ALIGN_CENTER : ImageSpan.ALIGN_BOTTOM), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static Object[] format(int icon, double temp) {
        return new Object[] {icon, String.format(Locale.ROOT, "%d", (int) Math.round(temp))};
    }

    private static Object[] read() {
        try {
            JSONArray a = new JSONArray(get(metarUrl));
            JSONObject best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.has("temp") || o.isNull("temp")) continue;
                if (Double.isNaN(nearLat)) {
                    best = o;   // weather_metar: the one airport asked for
                    break;
                }
                double dLat = o.optDouble("lat", 999) - nearLat;
                double dLon = (o.optDouble("lon", 999) - nearLon) * Math.cos(Math.toRadians(nearLat));
                double distance = dLat * dLat + dLon * dLon;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = o;
                }
            }
            if (best != null) return format(metarIcon(best), best.getDouble("temp"));
        } catch (Exception ignored) {
        }
        try {
            JSONObject o = new JSONObject(get(meteoUrl)).getJSONObject("current");
            boolean day = o.optInt("is_day", 1) == 1;
            return format(wmoIcon(o.optInt("weather_code", 0), day), o.getDouble("temperature_2m"));
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isDay() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 18;
    }

    private static int metarIcon(JSONObject o) {
        String wx = o.optString("wxString", "");
        if (wx.equals("null")) wx = "";
        if (wx.contains("TS")) return WeatherIcon.STORM;
        if (wx.contains("SN")) return WeatherIcon.SNOW;
        if (wx.contains("RA") || wx.contains("DZ") || wx.contains("SH")) return WeatherIcon.RAIN;
        if (wx.contains("DU") || wx.contains("SA") || wx.contains("SS") || wx.contains("DS")
                || wx.contains("HZ") || wx.contains("FU") || wx.contains("FG") || wx.contains("BR")) return WeatherIcon.DUST;
        String cover = o.optString("cover", "");
        JSONArray clouds = o.optJSONArray("clouds");
        if (clouds != null) {
            for (int i = 0; i < clouds.length(); i++) {
                JSONObject c = clouds.optJSONObject(i);
                if (c == null) continue;
                String cv = c.optString("cover", "");
                if (rank(cv) > rank(cover)) cover = cv;
            }
        }
        boolean day = isDay();
        int r = rank(cover);
        if (r >= 3) return WeatherIcon.CLOUD;
        if (r >= 1) return day ? WeatherIcon.PARTLY : WeatherIcon.CLOUD;
        return day ? WeatherIcon.SUN : WeatherIcon.MOON;
    }

    private static int rank(String cover) {
        switch (cover) {
            case "OVC": case "OVX": return 4;
            case "BKN": return 3;
            case "SCT": return 2;
            case "FEW": return 1;
            default: return 0;
        }
    }

    private static int wmoIcon(int code, boolean day) {
        if (code >= 95) return WeatherIcon.STORM;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return WeatherIcon.SNOW;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return WeatherIcon.RAIN;
        if (code == 45 || code == 48) return WeatherIcon.DUST;
        if (code == 3) return WeatherIcon.CLOUD;
        if (code == 1 || code == 2) return day ? WeatherIcon.PARTLY : WeatherIcon.CLOUD;
        return day ? WeatherIcon.SUN : WeatherIcon.MOON;
    }

    private static String get(String url) throws Exception {
        if (url == null) throw new IllegalStateException("not configured");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "luxurytv");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }
}
