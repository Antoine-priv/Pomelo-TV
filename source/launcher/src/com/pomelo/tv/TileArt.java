package com.pomelo.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Visuels des tuiles Sources et Réglages, au format des bannières d'apps. */
final class TileArt extends Drawable {

    static final int SOURCES = 0, SETTINGS = 1;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Icons sourcesIcon = new Icons(Icons.INPUT, Color.WHITE);

    TileArt(int kind) {
        this.kind = kind;
    }

    private Shader vertical(float top, float bottom, int from, int to) {
        return new LinearGradient(0, top, 0, bottom, from, to, Shader.TileMode.CLAMP);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        boolean settings = kind == SETTINGS;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(settings ? vertical(b.top, b.bottom, 0xFFD5D7DC, 0xFF8F9299)
                : vertical(b.top, b.bottom, 0xFF4C8DFF, 0xFF1D4FD6));
        c.drawRect(b, paint);
        paint.setShader(null);
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        if (settings) {
            drawCog(c, cx, cy, s * 0.86f);
        } else {
            int half = Math.round(s * 0.34f);
            sourcesIcon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
            sourcesIcon.draw(c);
        }
    }

    /** Engrenage métallique à anneaux concentriques et rayons, dans l'esprit de l'icône Réglages d'Apple. */
    private void drawCog(Canvas c, float cx, float cy, float s) {
        float top = cy - s / 2, bottom = cy + s / 2;
        paint.setShader(vertical(top, bottom, 0xFF6A6D74, 0xFF3D3F45));
        c.drawPath(toothed(cx, cy, s * 0.5f, s * 0.455f, 36), paint);
        paint.setShader(vertical(top, bottom, 0xFFECEDEF, 0xFFA5A8AE));
        c.drawCircle(cx, cy, s * 0.415f, paint);
        paint.setShader(vertical(top, bottom, 0xFF55585E, 0xFF34363B));
        c.drawCircle(cx, cy, s * 0.36f, paint);
        paint.setShader(vertical(top, bottom, 0xFFDFE0E3, 0xFFB4B6BB));
        c.drawCircle(cx, cy, s * 0.325f, paint);

        paint.setShader(null);
        paint.setColor(0xFF45474D);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(s * 0.038f);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            c.drawLine(cx + (float) Math.cos(a) * s * 0.11f, cy + (float) Math.sin(a) * s * 0.11f,
                    cx + (float) Math.cos(a) * s * 0.3f, cy + (float) Math.sin(a) * s * 0.3f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, s * 0.135f, paint);
        paint.setColor(0xFFC9CBD0);
        c.drawCircle(cx, cy, s * 0.06f, paint);
    }

    private Path toothed(float cx, float cy, float out, float in, int teeth) {
        Path p = new Path();
        for (int i = 0; i < teeth * 4; i++) {
            double a = Math.PI * 2 * i / (teeth * 4);
            float r = (i % 4 == 0 || i % 4 == 1) ? out : in;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
