package com.pomelo.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

/** Forme à courbure continue, comme les « coins continus » d'Apple, avec fond et trait optionnel. */
final class Squircle extends Drawable {

    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private float insetX;
    private Underlay underlay;

    /** Ce qui est dessiné sous le fond, découpé à la forme. */
    interface Underlay {
        void draw(Canvas c);
    }

    void setUnderlay(Underlay underlay) {
        this.underlay = underlay;
        invalidateSelf();
    }

    Squircle(float radius, int color) {
        this.radius = radius;
        fill.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
    }

    void setColor(int color) {
        fill.setColor(color);
        invalidateSelf();
    }

    void setStroke(float width, int color) {
        stroke.setStrokeWidth(width);
        stroke.setColor(color);
        invalidateSelf();
    }

    /** Les coefficients sont ceux du tracé des coins continus d'iOS, pour un rayon r. */
    static Path path(float w, float h, float radius) {
        final float a = 1.528665f, b = 1.08849296f, c = 0.86840694f, d = 0.63149379f,
                e = 0.07491139f, f = 0.37282383f, g = 0.16905956f;
        float r = Math.min(radius, Math.min(w, h) / 2 / a);
        Path p = new Path();
        p.moveTo(a * r, 0);
        p.lineTo(w - a * r, 0);
        p.cubicTo(w - b * r, 0, w - c * r, 0, w - d * r, e * r);
        p.cubicTo(w - f * r, g * r, w - g * r, f * r, w - e * r, d * r);
        p.cubicTo(w, c * r, w, b * r, w, a * r);
        p.lineTo(w, h - a * r);
        p.cubicTo(w, h - b * r, w, h - c * r, w - e * r, h - d * r);
        p.cubicTo(w - g * r, h - f * r, w - f * r, h - g * r, w - d * r, h - e * r);
        p.cubicTo(w - c * r, h, w - b * r, h, w - a * r, h);
        p.lineTo(a * r, h);
        p.cubicTo(b * r, h, c * r, h, d * r, h - e * r);
        p.cubicTo(f * r, h - g * r, g * r, h - f * r, e * r, h - d * r);
        p.cubicTo(0, h - c * r, 0, h - b * r, 0, h - a * r);
        p.lineTo(0, a * r);
        p.cubicTo(0, b * r, 0, c * r, e * r, d * r);
        p.cubicTo(g * r, f * r, f * r, g * r, d * r, e * r);
        p.cubicTo(c * r, 0, b * r, 0, a * r, 0);
        p.close();
        return p;
    }

    /** Rétrécit la forme des deux côtés (ou l'élargit si négatif), pour animer un changement de largeur. */
    void setInsetX(float inset) {
        insetX = inset;
        rebuildPath();
        invalidateSelf();
    }

    private void rebuildPath() {
        Rect b = getBounds();
        path = path(b.width() - 2 * insetX, b.height(), radius);
        path.offset(b.left + insetX, b.top);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        rebuildPath();
    }

    @Override
    public void draw(Canvas c) {
        if (underlay != null) {
            int save = c.save();
            c.clipPath(path);
            underlay.draw(c);
            c.restoreToCount(save);
        }
        c.drawPath(path, fill);
        if (stroke.getStrokeWidth() > 0) c.drawPath(path, stroke);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** Conteneur qui découpe son contenu en squircle : le découpage par contour d'Android ne gère pas cette forme. */
    static final class Frame extends FrameLayout {
        private final float radius;
        private Path clip = new Path();

        Frame(Context context, float radius) {
            super(context);
            this.radius = radius;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            clip = path(w, h, radius);
        }

        @Override
        protected void dispatchDraw(Canvas c) {
            int save = c.save();
            c.clipPath(clip);
            super.dispatchDraw(c);
            c.restoreToCount(save);
        }
    }
}
