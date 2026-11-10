package app.reelback.gallery;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

final class AlbumAvatar extends View {
    private final MainActivity app;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix crop = new Matrix();
    private final int size;
    private final boolean onVideo;
    private Bitmap bitmap;
    private BitmapShader photo;
    private ValueAnimator reveal;
    private String boundKey;
    private int photoAlpha = 255;
    private int background = 0xff35323a;
    private static final int[] BACKGROUNDS = {
            0xff382943, 0xff253b46, 0xff453038, 0xff353c2b, 0xff403329
    };

    AlbumAvatar(MainActivity app, int size, boolean onVideo) {
        super(app);
        this.app = app;
        this.size = app.dp(size);
        this.onVideo = onVideo;
    }

    void makeClickable(Runnable open) {
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setOnClickListener(v -> open.run());
        setFocusable(true);
        Motion.press(this, this);
    }

    void bind(VideoLibrary.Album album) {
        if (album == null) {
            boundKey = null;
            display(null, false);
            return;
        }
        String key = album.cover.key;
        if (key.equals(boundKey)) return;
        boundKey = key;
        background = BACKGROUNDS[Math.floorMod(album.key.hashCode(), BACKGROUNDS.length)];
        Bitmap cached = app.cached(album.cover);
        display(cached, false);
        if (cached != null) return;
        app.thumbnail(album.cover, next -> {
            if (key.equals(boundKey) && next != null) display(next, true);
        });
    }

    private void display(Bitmap image, boolean animate) {
        if (reveal != null) { reveal.cancel(); reveal = null; }
        bitmap = image;
        photo = image == null ? null : new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        updateCrop();
        photoAlpha = 255;
        if (photo != null && animate && Motion.enabled() && isAttachedToWindow() && isShown()) {
            photoAlpha = 0;
            reveal = ValueAnimator.ofInt(0, 255);
            reveal.setDuration(170);
            reveal.setInterpolator(Motion.ENTER);
            reveal.addUpdateListener(f -> { photoAlpha = (int) f.getAnimatedValue(); invalidate(); });
            reveal.start();
        }
        invalidate();
    }

    private void updateCrop() {
        if (photo == null || getWidth() == 0 || getHeight() == 0) return;
        float d = Math.min(size, Math.min(getWidth(), getHeight()));
        float scale = Math.max(d / bitmap.getWidth(), d / bitmap.getHeight());
        crop.setScale(scale, scale);
        crop.postTranslate((getWidth() - bitmap.getWidth() * scale) / 2,
                (getHeight() - bitmap.getHeight() * scale) / 2);
        photo.setLocalMatrix(crop);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { updateCrop(); }

    @Override protected void onDraw(Canvas c) {
        float x = getWidth() / 2f, y = getHeight() / 2f;
        float r = Math.min(size, Math.min(getWidth(), getHeight())) / 2f - app.dp(1);
        if (r <= 0) return;
        paint.setColor(background);
        paint.setStyle(Paint.Style.FILL);
        if (onVideo) paint.setShadowLayer(app.dp(3), 0, app.dp(1), 0xa9000000);
        c.drawCircle(x, y, r, paint);
        paint.clearShadowLayer();
        if (photo == null || photoAlpha < 255) {
            float icon = r * .98f;
            c.save();
            c.translate(x - icon / 2, y - icon / 2);
            c.scale(icon / 24, icon / 24);
            Glyph.draw(c, paint, "albums", 0xeaffffff);
            c.restore();
        }
        if (photo != null) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setAlpha(photoAlpha);
            paint.setShader(photo);
            c.drawCircle(x, y, r, paint);
            paint.setShader(null);
        }
        paint.setColor(onVideo ? 0xf0ffffff : 0x50ffffff);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(app.dp(onVideo ? 1.6f : 1f));
        c.drawCircle(x, y, r, paint);
    }

    @Override protected void onDetachedFromWindow() {
        if (reveal != null) { reveal.cancel(); reveal = null; }
        photoAlpha = 255;
        super.onDetachedFromWindow();
    }
}
