package com.pomelo.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Visuels des tuiles Sources, Réglages et Éteindre, au format des bannières d'apps. */
final class TileArt extends Drawable {

    static final int SOURCES = 0, SETTINGS = 1, POWER = 2;

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
                : kind == POWER ? vertical(b.top, b.bottom, 0xFF48484D, 0xFF1C1C1E)
                : vertical(b.top, b.bottom, 0xFF4C8DFF, 0xFF1D4FD6));
        c.drawRect(b, paint);
        paint.setShader(null);
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        if (settings) {
            drawCog(c, cx, cy, s * 0.92f);
        } else if (kind == POWER) {
            drawPowerKnob(c, cx, cy, s * 0.72f);
        } else {
            int half = Math.round(s * 0.34f);
            sourcesIcon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
            sourcesIcon.draw(c);
        }
    }

    /** Deux roues crantées métalliques l'une dans l'autre, avec un moyeu à trois branches, dans l'esprit de l'icône Réglages d'Apple. */
    private void drawCog(Canvas c, float cx, float cy, float s) {
        float top = cy - s / 2, bottom = cy + s / 2;
        // Grande roue : couronne sombre et crantée, puis sa face métallique.
        paint.setShader(vertical(top, bottom, 0xFF5B5E65, 0xFF2E3035));
        c.drawPath(toothed(cx, cy, s * 0.5f, s * 0.44f, 30), paint);
        paint.setShader(vertical(top, bottom, 0xFFF4F5F7, 0xFFAEB1B7));
        c.drawCircle(cx, cy, s * 0.405f, paint);
        // Gorge sombre où tourne la petite roue.
        paint.setShader(vertical(top, bottom, 0xFF3C3E44, 0xFF5A5D64));
        c.drawCircle(cx, cy, s * 0.36f, paint);
        // Petite roue crantée.
        paint.setShader(vertical(top, bottom, 0xFFE9EAED, 0xFF9DA0A7));
        c.drawPath(toothed(cx, cy, s * 0.33f, s * 0.285f, 18), paint);
        paint.setShader(vertical(top, bottom, 0xFF4A4C52, 0xFF2B2D32));
        c.drawCircle(cx, cy, s * 0.235f, paint);

        // Trois branches jusqu'au moyeu.
        paint.setShader(null);
        paint.setColor(0xFFD9DADE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(s * 0.058f);
        for (int i = 0; i < 3; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * i / 3;
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * s * 0.215f, cy + (float) Math.sin(a) * s * 0.215f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, s * 0.085f, paint);
        paint.setColor(0xFF3A3C42);
        c.drawCircle(cx, cy, s * 0.038f, paint);
    }

    /** Le curseur du « glisser pour éteindre » de l'iPhone : rond blanc et symbole marche/arrêt rouge. */
    private void drawPowerKnob(Canvas c, float cx, float cy, float s) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        c.drawCircle(cx, cy, s / 2, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(s * 0.075f);
        paint.setColor(0xFFFF3B30);
        float r = s * 0.235f;
        c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), -55, 290, false, paint);
        c.drawLine(cx, cy - s * 0.3f, cx, cy - s * 0.04f, paint);
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
