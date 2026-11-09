package com.esamyo.luxurytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

/** Flat one-colour weather icons drawn in code, so they follow the greeting's colour. */
final class WeatherIcon {
    static final int SUN = 0, MOON = 1, PARTLY = 2, CLOUD = 3, RAIN = 4, STORM = 5, SNOW = 6, DUST = 7;

    private WeatherIcon() {}

    static Bitmap draw(int kind, int s, int color) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color | 0xFF000000);
        fill.setStyle(Paint.Style.FILL);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(color | 0xFF000000);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeWidth(s * 0.085f);
        switch (kind) {
            case MOON: moon(c, fill, s); break;
            case PARTLY:
                sun(c, fill, line, s * 0.36f, s * 0.34f, s * 0.13f, s * 0.21f, s * 0.29f);
                cutCloud(c, s, s * 0.22f, s * 0.36f, s * 0.74f, s * 0.5f);
                c.drawPath(cloud(s * 0.22f, s * 0.36f, s * 0.74f, s * 0.5f), fill);
                break;
            case CLOUD: c.drawPath(cloud(s * 0.06f, s * 0.2f, s * 0.88f, s * 0.6f), fill); break;
            case RAIN:
                c.drawPath(cloud(s * 0.08f, s * 0.06f, s * 0.84f, s * 0.56f), fill);
                for (float x : new float[] {0.34f, 0.52f, 0.70f})
                    c.drawLine(s * x, s * 0.72f, s * (x - 0.07f), s * 0.92f, line);
                break;
            case STORM: {
                c.drawPath(cloud(s * 0.08f, s * 0.04f, s * 0.84f, s * 0.56f), fill);
                Path bolt = new Path();
                bolt.moveTo(s * 0.56f, s * 0.52f);
                bolt.lineTo(s * 0.38f, s * 0.78f);
                bolt.lineTo(s * 0.50f, s * 0.78f);
                bolt.lineTo(s * 0.42f, s * 0.98f);
                bolt.lineTo(s * 0.66f, s * 0.68f);
                bolt.lineTo(s * 0.54f, s * 0.68f);
                bolt.lineTo(s * 0.62f, s * 0.52f);
                bolt.close();
                cutPath(c, bolt, s * 0.06f);
                c.drawPath(bolt, fill);
                break;
            }
            case SNOW:
                c.drawPath(cloud(s * 0.08f, s * 0.06f, s * 0.84f, s * 0.56f), fill);
                for (float x : new float[] {0.30f, 0.50f, 0.70f}) {
                    c.drawCircle(s * x, s * 0.78f, s * 0.055f, fill);
                    c.drawCircle(s * (x + 0.1f), s * 0.93f, s * 0.045f, fill);
                }
                break;
            case DUST:
                wave(c, line, s, 0.30f, 0.12f, 0.88f);
                wave(c, line, s, 0.52f, 0.06f, 0.94f);
                wave(c, line, s, 0.74f, 0.18f, 0.82f);
                break;
            default: sun(c, fill, line, s * 0.5f, s * 0.5f, s * 0.19f, s * 0.31f, s * 0.45f);
        }
        return b;
    }

    private static void sun(Canvas c, Paint fill, Paint line, float cx, float cy, float r, float r1, float r2) {
        c.drawCircle(cx, cy, r, fill);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(cx + dx * r1, cy + dy * r1, cx + dx * r2, cy + dy * r2, line);
        }
    }

    private static void moon(Canvas c, Paint fill, int s) {
        Path a = new Path();
        a.addCircle(s * 0.47f, s * 0.53f, s * 0.36f, Path.Direction.CW);
        Path b = new Path();
        b.addCircle(s * 0.66f, s * 0.38f, s * 0.30f, Path.Direction.CW);
        a.op(b, Path.Op.DIFFERENCE);
        c.drawPath(a, fill);
    }

    /** A cloud inside the box (x, y, w, h), flat along the bottom. */
    private static Path cloud(float x, float y, float w, float h) {
        Path p = new Path();
        p.addCircle(x + 0.26f * w, y + 0.66f * h, 0.30f * h, Path.Direction.CW);
        p.addCircle(x + 0.52f * w, y + 0.44f * h, 0.42f * h, Path.Direction.CW);
        p.addCircle(x + 0.76f * w, y + 0.68f * h, 0.28f * h, Path.Direction.CW);
        p.addRect(x + 0.26f * w, y + 0.60f * h, x + 0.76f * w, y + 0.96f * h, Path.Direction.CW);
        return p;
    }

    private static void cutCloud(Canvas c, int s, float x, float y, float w, float h) {
        cutPath(c, cloud(x, y, w, h), s * 0.07f);
    }

    /** Clears a thin gap around a shape so it reads apart from what is drawn under it. */
    private static void cutPath(Canvas c, Path p, float gap) {
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clear.setStyle(Paint.Style.FILL_AND_STROKE);
        clear.setStrokeWidth(gap * 2);
        clear.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(p, clear);
    }

    private static void wave(Canvas c, Paint line, int s, float y, float x0, float x1) {
        Path p = new Path();
        float a = s * 0.05f;
        float step = (x1 - x0) * s / 4;
        p.moveTo(s * x0, s * y);
        for (int i = 0; i < 4; i++) {
            float sx = s * x0 + step * i;
            p.quadTo(sx + step / 2, s * y + (i % 2 == 0 ? -a : a), sx + step, s * y);
        }
        c.drawPath(p, line);
    }
}
