package app.reelback.gallery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class Glyph extends View {
    private static final Path HEART = new Path();
    private static final Path SHARE_ARROW = new Path();
    static {
        HEART.moveTo(12, 20.3f);
        HEART.cubicTo(11.8f, 20.3f, 11.55f, 20.19f, 11.34f, 20.01f);
        HEART.cubicTo(7.2f, 16.53f, 2.73f, 13.25f, 2.73f, 8.77f);
        HEART.cubicTo(2.73f, 5.78f, 4.88f, 3.75f, 7.52f, 3.75f);
        HEART.cubicTo(9.39f, 3.75f, 10.92f, 4.66f, 12, 6.08f);
        HEART.cubicTo(13.08f, 4.66f, 14.61f, 3.75f, 16.48f, 3.75f);
        HEART.cubicTo(19.12f, 3.75f, 21.27f, 5.78f, 21.27f, 8.77f);
        HEART.cubicTo(21.27f, 13.25f, 16.8f, 16.53f, 12.66f, 20.01f);
        HEART.cubicTo(12.45f, 20.19f, 12.2f, 20.3f, 12, 20.3f);
        HEART.close();
        SHARE_ARROW.moveTo(13.5f, 3.6f);
        SHARE_ARROW.cubicTo(13.5f, 2.99f, 14.23f, 2.67f, 14.69f, 3.08f);
        SHARE_ARROW.lineTo(22.17f, 9.84f);
        SHARE_ARROW.cubicTo(22.66f, 10.28f, 22.66f, 11.05f, 22.17f, 11.49f);
        SHARE_ARROW.lineTo(14.69f, 18.19f);
        SHARE_ARROW.cubicTo(14.23f, 18.6f, 13.5f, 18.28f, 13.5f, 17.67f);
        SHARE_ARROW.lineTo(13.5f, 14.3f);
        SHARE_ARROW.cubicTo(8.5f, 14.4f, 5.52f, 15.65f, 3.23f, 19.06f);
        SHARE_ARROW.cubicTo(2.86f, 19.61f, 2.01f, 19.3f, 2.06f, 18.64f);
        SHARE_ARROW.cubicTo(2.6f, 11.64f, 6.87f, 7.55f, 13.5f, 7.12f);
        SHARE_ARROW.close();
    }
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    String name;
    int color;
    int size;
    private boolean shadow;

    Glyph(Context c, String name, int color, int size) {
        super(c); this.name = name; this.color = color; this.size = size;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void update(String next, int tint) { name = next; color = tint; invalidate(); }
    Glyph shadowed() { shadow = true; return this; }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = size * getResources().getDisplayMetrics().density;
        c.save(); c.translate((getWidth() - d) / 2, (getHeight() - d) / 2); c.scale(d / 24f, d / 24f);
        if (shadow) p.setShadowLayer(1.42f, 0, .86f, 0xb8000000);
        draw(c, p, name, color);
        c.restore();
    }

    static void draw(Canvas c, Paint p, String name, int color) {
        p.setColor(color); p.setStrokeWidth(1.85f); p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND);
        switch (name) {
            case "logo":
                c.drawRoundRect(8, 2, 20, 19, 3, 3, p);
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(4, 5, 17, 22, 3, 3, p);
                p.setColor(0xff191323);
                polygon(c, p, 9, 10, 14, 13.5f, 9, 17);
                break;
            case "feed":
                c.drawRoundRect(5, 3, 19, 21, 4, 4, p);
                p.setStyle(Paint.Style.FILL); polygon(c, p, 10, 8, 15, 12, 10, 16); break;
            case "home": case "home-filled":
                if (name.endsWith("filled")) p.setStyle(Paint.Style.FILL);
                Path home = new Path();
                home.moveTo(2.95f, 10.25f); home.lineTo(10.5f, 3.55f);
                home.quadTo(12f, 2.25f, 13.5f, 3.55f); home.lineTo(21.05f, 10.25f);
                home.quadTo(21.5f, 10.66f, 21.5f, 11.26f); home.lineTo(21.5f, 19.5f);
                home.quadTo(21.5f, 21.5f, 19.5f, 21.5f); home.lineTo(14.8f, 21.5f);
                home.lineTo(14.8f, 15.1f); home.quadTo(14.8f, 14.2f, 13.9f, 14.2f);
                home.lineTo(10.1f, 14.2f); home.quadTo(9.2f, 14.2f, 9.2f, 15.1f);
                home.lineTo(9.2f, 21.5f); home.lineTo(4.5f, 21.5f);
                home.quadTo(2.5f, 21.5f, 2.5f, 19.5f); home.lineTo(2.5f, 11.26f);
                home.quadTo(2.5f, 10.66f, 2.95f, 10.25f); home.close();
                c.drawPath(home, p);
                break;
            case "bookmark": case "bookmark-filled":
                if (name.endsWith("filled")) p.setStyle(Paint.Style.FILL);
                Path mark = new Path();
                mark.moveTo(7.1f, 3.2f); mark.lineTo(16.9f, 3.2f);
                mark.quadTo(18.5f, 3.2f, 18.5f, 4.8f); mark.lineTo(18.5f, 20.42f);
                mark.quadTo(18.5f, 21.24f, 17.8f, 20.78f); mark.lineTo(12.55f, 17.32f);
                mark.quadTo(12, 16.95f, 11.45f, 17.32f); mark.lineTo(6.2f, 20.78f);
                mark.quadTo(5.5f, 21.24f, 5.5f, 20.42f); mark.lineTo(5.5f, 4.8f);
                mark.quadTo(5.5f, 3.2f, 7.1f, 3.2f); mark.close(); c.drawPath(mark,p); break;
            case "play":
                p.setStyle(Paint.Style.FILL); polygon(c, p, 7, 4, 20, 12, 7, 20); break;
            case "pause":
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(6, 4, 10, 20, 1, 1, p); c.drawRoundRect(14, 4, 18, 20, 1, 1, p); break;
            case "heart": case "heart-filled":
                if (name.endsWith("filled")) p.setStyle(Paint.Style.FILL);
                else p.setStrokeWidth(2.05f);
                c.drawPath(HEART, p); break;
            case "albums":
                c.drawRoundRect(6.15f, 6.25f, 21.3f, 20.8f, 3.05f, 3.05f, p);
                Path stack = new Path(); stack.moveTo(2.7f, 16.8f); stack.lineTo(2.7f, 6.2f);
                stack.quadTo(2.7f, 2.9f, 6f, 2.9f); stack.lineTo(17.55f, 2.9f); c.drawPath(stack,p);
                line(c,p, 7.75f, 17.75f, 11.5f, 14.1f, 14f, 16.25f, 17.6f, 12.7f, 20.5f, 15.7f);
                p.setStyle(Paint.Style.FILL); c.drawCircle(11.1f, 10.75f, 1.18f, p);
                break;
            case "share":
                line(c,p, 8.1f, 10.4f, 15.7f, 6.2f);
                line(c,p, 8.1f, 13.6f, 15.7f, 17.8f);
                c.drawCircle(6, 12, 2.7f, p); c.drawCircle(18, 5, 2.7f, p);
                c.drawCircle(18, 19, 2.7f, p); break;
            case "forward":
                p.setStyle(Paint.Style.FILL);
                c.drawPath(SHARE_ARROW, p); break;
            case "trash":
                line(c,p, 3.9f, 6.6f, 20.1f, 6.6f);
                Path lid = new Path(); lid.moveTo(8.6f,6.6f); lid.lineTo(8.6f,4.9f);
                lid.quadTo(8.6f,3.55f,9.95f,3.55f); lid.lineTo(14.05f,3.55f);
                lid.quadTo(15.4f,3.55f,15.4f,4.9f); lid.lineTo(15.4f,6.6f); c.drawPath(lid,p);
                Path bin = new Path(); bin.moveTo(5.65f,7f); bin.lineTo(6.48f,18.8f);
                bin.quadTo(6.64f,21f,8.83f,21f); bin.lineTo(15.17f,21f);
                bin.quadTo(17.36f,21f,17.52f,18.8f); bin.lineTo(18.35f,7f); c.drawPath(bin,p);
                line(c,p, 10.1f, 10.3f, 10.4f, 17.1f); line(c,p, 13.9f, 10.3f, 13.6f, 17.1f); break;
            case "sliders":
                line(c,p, 3, 7, 6, 7); line(c,p, 11, 7, 21, 7); c.drawCircle(8.5f, 7, 2.5f, p);
                line(c,p, 3, 17, 13, 17); line(c,p, 18, 17, 21, 17); c.drawCircle(15.5f, 17, 2.5f, p); break;
            case "back": line(c,p, 14, 5, 7, 12, 14, 19); line(c,p, 7, 12, 21, 12); break;
            case "left": line(c,p, 15, 5, 8, 12, 15, 19); break;
            case "expand":
                line(c,p, 3, 9, 3, 3, 9, 3); line(c,p, 15, 3, 21, 3, 21, 9);
                line(c,p, 21, 15, 21, 21, 15, 21); line(c,p, 9, 21, 3, 21, 3, 15);
                break;
            case "expand-exit":
                line(c,p, 9, 3, 9, 9, 3, 9); line(c,p, 21, 9, 15, 9, 15, 3);
                line(c,p, 15, 21, 15, 15, 21, 15); line(c,p, 3, 15, 9, 15, 9, 21);
                break;
            case "speed":
                c.drawArc(3, 4, 21, 22, 150, 240, false, p);
                line(c,p, 8, 17, 16, 17); line(c,p, 12, 13, 16, 9);
                break;
            case "history":
                c.drawArc(3.1f, 3.1f, 20.9f, 20.9f, 202, 315, false, p);
                line(c, p, 3.4f, 3.8f, 3.4f, 9, 8.6f, 9);
                line(c, p, 12, 7, 12, 12, 15.8f, 14);
                break;
            case "filter":
                line(c, p, 3, 6, 21, 6);
                line(c, p, 6, 12, 18, 12);
                line(c, p, 9, 18, 15, 18);
                break;
            case "right": line(c,p, 9, 6, 15, 12, 9, 18); break;
            case "down": line(c,p, 6, 9, 12, 15, 18, 9); break;
            case "up": line(c,p, 6, 15, 12, 9, 18, 15); break;
            case "sound": case "muted":
                polygon(c,p, 3, 9, 7, 9, 12, 5, 12, 19, 7, 15, 3, 15);
                if (name.equals("sound")) {
                    c.drawArc(10, 5, 22, 19, -54, 108, false, p);
                    c.drawArc(9, 8, 18, 16, -54, 108, false, p);
                } else { line(c,p, 16, 10, 21, 15); line(c,p, 21, 10, 16, 15); }
                break;
            case "close": line(c,p, 6, 6, 18, 18); line(c,p, 18, 6, 6, 18); break;
            case "check": line(c,p, 4, 12, 9, 17, 20, 6); break;
            case "shuffle":
                Path s = new Path(); s.moveTo(3, 6); s.lineTo(6, 6); s.cubicTo(11, 6, 13, 18, 17, 18);
                s.lineTo(21, 18); c.drawPath(s, p);
                line(c,p, 18, 15, 21, 18, 18, 21); line(c,p, 18, 3, 21, 6, 18, 9);
                s.reset(); s.moveTo(3, 18); s.lineTo(6, 18); s.cubicTo(11, 18, 13, 6, 17, 6);
                s.lineTo(21, 6); c.drawPath(s, p); break;
            case "lock":
                c.drawRoundRect(5, 10, 19, 21, 3, 3, p);
                c.drawArc(8, 3, 16, 13, 180, 180, false, p);
                line(c,p, 8, 8, 8, 10); line(c,p, 16, 8, 16, 10); c.drawCircle(12, 15, .8f, p);
                break;
        }
    }
    private static void polygon(Canvas c, Paint p, float... pts) { path(c, p, true, pts); }
    private static void line(Canvas c, Paint p, float... pts) { path(c, p, false, pts); }
    private static void path(Canvas c, Paint p, boolean close, float... pts) {
        Path s = new Path();
        s.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) s.lineTo(pts[i], pts[i + 1]);
        if (close) s.close();
        c.drawPath(s, p);
    }
}
