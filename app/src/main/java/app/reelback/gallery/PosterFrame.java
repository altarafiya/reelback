package app.reelback.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class PosterFrame extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private Bitmap image;
    private boolean fit;

    PosterFrame(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setBitmap(Bitmap bitmap) {
        image = bitmap;
        invalidate();
    }

    void setFit(boolean fit) {
        this.fit = fit;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (image == null) return;
        float w = getWidth(), h = getHeight();
        float sx = w / image.getWidth(), sy = h / image.getHeight();
        float scale = fit ? Math.min(sx, sy) : Math.max(sx, sy);
        float drawW = image.getWidth() * scale, drawH = image.getHeight() * scale;
        float x = (w - drawW) * .5f, y = (h - drawH) * .5f;
        bounds.set(x, y, x + drawW, y + drawH);
        canvas.drawBitmap(image, null, bounds, paint);
    }
}
