package app.reelback.gallery;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class FavoriteBurst extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MainActivity app;
    private ValueAnimator motion;
    private float x, y, fraction;
    private static final float[] DX = {0f,.93f,.73f,-.13f,-.94f,-.73f};
    private static final float[] DY = {-1f,-.37f,.55f,1f,.27f,-.51f};

    FavoriteBurst(MainActivity app) {
        super(app);
        this.app = app;
        setVisibility(INVISIBLE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void play(float px, float py) {
        if (!Motion.enabled()) return;
        clear();
        float margin = app.dp(46);
        x = Math.max(margin, Math.min(getWidth() - margin, px));
        y = Math.max(margin, Math.min(getHeight() - margin, py));
        fraction = 0;
        setVisibility(VISIBLE);
        motion = ValueAnimator.ofFloat(0f, 1f);
        motion.setInterpolator(new android.view.animation.LinearInterpolator());
        motion.setDuration(610);
        motion.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            if (fraction >= 1f) setVisibility(INVISIBLE);
            else invalidate();
        });
        motion.start();
    }

    void clear() {
        if (motion != null) { motion.cancel(); motion = null; }
        setVisibility(INVISIBLE);
    }

    @Override protected void onDraw(Canvas c) {
        float p = fraction;
        float rise = Motion.ENTER.getInterpolation(p);
        float pop = p < .36f ? .4f + .6f * Motion.POP.getInterpolation(p / .36f) : 1f - .2f * (p - .36f) / .64f;
        float fade = p < .57f ? 1f : Math.max(0f, (1f - p) / .43f);
        float size = app.dp(71) * pop;
        float cy = y - app.dp(23) * rise;
        c.save();
        c.translate(x, cy);
        c.rotate(-11f + 12f * rise);
        c.translate(-size * .5f, -size * .5f);
        c.scale(size / 24f, size / 24f);
        paint.setShadowLayer(1.5f, 0, 1f, 0x60000000);
        Glyph.draw(c, paint, "heart-filled", ((int) (255 * fade) << 24) | 0x00ff4166);
        paint.clearShadowLayer();
        c.restore();
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < DX.length; i++) {
            paint.setColor(i % 2 == 0 ? Color.WHITE : 0xffff7593);
            paint.setAlpha((int) (230 * fade * Math.min(1f, p * 7f)));
            float distance = app.dp(23 + (i % 3) * 3) + app.dp(31) * rise;
            c.drawCircle(x + DX[i] * distance, cy + DY[i] * distance,
                    app.dp(i % 2 == 0 ? 2.3f : 3.1f) * fade, paint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }
}
