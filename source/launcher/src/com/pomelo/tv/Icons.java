package com.pomelo.tv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Icônes vectorielles fines (style trait) pour les boutons du dock. */
final class Icons extends Drawable {

    static final int INPUT = 1, TV = 3;

    private final int kind;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icons(int kind, int color) {
        this.kind = kind;
        stroke.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        stroke.setStrokeWidth(s * 0.07f);
        switch (kind) {
            case INPUT: {
                RectF screen = new RectF(cx - s * 0.36f, cy - s * 0.28f, cx + s * 0.36f, cy + s * 0.2f);
                c.drawRoundRect(screen, s * 0.06f, s * 0.06f, stroke);
                c.drawLine(cx - s * 0.14f, cy + s * 0.34f, cx + s * 0.14f, cy + s * 0.34f, stroke);
                Path arrow = new Path();
                arrow.moveTo(cx - s * 0.16f, cy - s * 0.04f);
                arrow.lineTo(cx + s * 0.1f, cy - s * 0.04f);
                arrow.moveTo(cx + s * 0.01f, cy - s * 0.13f);
                arrow.lineTo(cx + s * 0.1f, cy - s * 0.04f);
                arrow.lineTo(cx + s * 0.01f, cy + s * 0.05f);
                c.drawPath(arrow, stroke);
                break;
            }
            case TV: {
                RectF screen = new RectF(cx - s * 0.38f, cy - s * 0.2f, cx + s * 0.38f, cy + s * 0.32f);
                c.drawRoundRect(screen, s * 0.06f, s * 0.06f, stroke);
                c.drawLine(cx, cy - s * 0.2f, cx - s * 0.15f, cy - s * 0.4f, stroke);
                c.drawLine(cx, cy - s * 0.2f, cx + s * 0.15f, cy - s * 0.4f, stroke);
                break;
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        stroke.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
