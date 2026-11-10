package app.reelback.gallery;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

final class Motion {
    static final Interpolator ENTER = new PathInterpolator(.18f, .82f, .22f, 1f);
    static final Interpolator EXIT = new PathInterpolator(.4f, 0f, 1f, 1f);
    static final Interpolator SETTLE = new PathInterpolator(.26f, .8f, .3f, 1f);
    static final Interpolator POP = new PathInterpolator(.2f, 1.36f, .34f, 1f);

    static boolean enabled() { return ValueAnimator.areAnimatorsEnabled(); }

    static void press(View target, View art) {
        target.setOnTouchListener((v, e) -> {
            if (!enabled()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    art.animate().cancel();
                    art.animate().scaleX(.89f).scaleY(.89f).setDuration(90).setInterpolator(SETTLE).start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    art.animate().cancel();
                    art.animate().scaleX(1f).scaleY(1f).setDuration(190).setInterpolator(POP).start();
                    break;
                default: break;
            }
            return false;
        });
    }

    private Motion() {}
}
