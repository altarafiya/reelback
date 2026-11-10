package app.reelback.gallery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

final class WelcomeArt extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    WelcomeArt(Context context) { super(context); }
    @Override protected void onDraw(Canvas c) {
        float scale = Math.min(getWidth() / 310f, getHeight() / 218f);
        c.save();
        c.translate((getWidth() - 310 * scale) / 2f, (getHeight() - 218 * scale) / 2f);
        c.scale(scale, scale);
        p.setColor(0xffffffff);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(153, 112, 138, new int[]{0x684e306e, 0x004e306e},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 310, 218, p);
        p.setShader(null);
        for (int y = 28; y < 202; y += 16) for (int x = 28; x < 289; x += 16) {
            p.setColor(0x444f4262);
            c.drawCircle(x, y, 1, p);
        }
        card(c, 111, 111, -16, 0xff372b4b, 0xff58436d, "heart");
        card(c, 202, 106, 16, 0xff32283f, 0xff5e476d, "albums");
        card(c, 155, 109, -5, 0xff8a6cb4, 0xffcdb7fa, "play");
        p.setColor(0xffcdf49a);
        c.drawCircle(241, 45, 3, p);
        c.drawCircle(58, 153, 2, p);
        p.setStrokeWidth(1.3f);
        c.drawLine(58, 62, 70, 62, p);
        c.drawLine(64, 56, 64, 68, p);
        c.restore();
    }
    private void card(Canvas c, int x, int y, float angle, int a, int b, String glyph) {
        c.save(); c.translate(x, y); c.rotate(angle);
        RectF r = new RectF(-48, -76, 48, 76);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x5010091c);
        c.drawRoundRect(-45, -70, 54, 83, 15, 15, p);
        p.setColor(0xffffffff);
        p.setShader(new LinearGradient(-48, 75, 48, -75, a, b, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 14, 14, p);
        p.setShader(null);
        p.setColor(0x66eee5ff); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1);
        c.drawRoundRect(r, 14, 14, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x3d201330);
        c.drawCircle(0, 0, 25, p);
        c.save(); c.translate(-14, -14); c.scale(28 / 24f, 28 / 24f);
        Glyph.draw(c, p, glyph, 0xfff5ecff);
        c.restore();
        p.setStyle(Paint.Style.FILL); p.setColor(0x65eee5ff);
        c.drawRoundRect(-32, 54, 32, 57, 1.5f, 1.5f, p);
        p.setColor(0xffcdf49a);
        c.drawRoundRect(-32, 54, -3, 57, 1.5f, 1.5f, p);
        c.restore();
    }
}
