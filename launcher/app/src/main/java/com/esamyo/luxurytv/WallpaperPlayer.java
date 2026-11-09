package com.esamyo.luxurytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows the theme's wallpaper (wallpaper.jpg) behind Home. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final File file;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final int width;
    private final int height;
    /** wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    /** wallpaper_dim as an ARGB black, painted into the picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen; null after releaseForMemoryPressure until start() puts it back. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        file = config.wallpaper;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (file != null) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Decoded right here, on the main thread: Home's first frame already has the picture instead of the
            // plain colour with the picture popping in later.
            Bitmap bitmap = decode(file);
            if (bitmap != null) show(bitmap);
        }
    }

    void start() {
        active = true;
        if (current != null || file == null || closed || loading) return;
        // Back after releaseForMemoryPressure: decode the picture again off the main thread.
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(file);
            root.post(() -> {
                loading = false;
                if (closed || !active || current != null) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap != null) show(bitmap);
            });
        });
    }

    void stop() {
        active = false;
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our reference and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        root.setBackgroundColor(backgroundColor);
    }

    void close() {
        active = false;
        closed = true;
        decoder.shutdownNow();
    }

    private void show(Bitmap bitmap) {
        current = bitmap;
        // The wallpaper covers the whole root: the root's plain colour under it would be overdraw.
        root.setBackground(null);
        image.setImageBitmap(bitmap);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
