package com.esamyo.luxurytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The focus glow: a soft, wide white glow behind the focused logo, with no slab, rim or box.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Cache the glow as a texture. The spring only changes translation, so the GPU can
        // move it without drawing the gradient again on every frame.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = getHeight() * 0.36f;
        // One glow for every app: soft white, whatever the logo's colour.
        int a = 0x48, c = 0xFFFFFF;
        soft.setShader(new RadialGradient(cx, cy, r,
                new int[]{a << 24 | c, (a * 2 / 5) << 24 | c, c},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.save();
        // Wide and low: the glow reaches out left and right of the logo more than above and below it.
        canvas.scale((float) getWidth() / getHeight(), 1f, cx, cy);
        canvas.drawCircle(cx, cy, r, soft);
        canvas.restore();
    }
}
