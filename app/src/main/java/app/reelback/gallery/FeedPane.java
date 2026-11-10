package app.reelback.gallery;

import static app.reelback.gallery.MainActivity.*;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

final class FeedPane extends FrameLayout {
    private static final int NEXT = 0x01050001, PREV = 0x01050002;
    private static final int WHITE = Color.WHITE, FAVORITE = 0xfffe4164;
    private final MainActivity a;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final FrameLayout live, preview, favButton, backButton, exitClear;
    private final LinearLayout meta, rail, timeline;
    private final AlbumAvatar albumAvatar;
    private final FavoriteBurst burst;
    private final View topShade, bottomShade, previewShade, scrubEdge;
    private final PosterFrame poster, previewArt;
    private final TextureView surface;
    private final TextView title, album, time, length, speed, trashHint, failure, previewTitle, previewAlbum;
    private final LinearLayout previewMeta;
    private final Glyph favGlyph, playGlyph;
    private final SeekBar seek;
    private final ProgressBar spinner;
    private final GestureDetector gestures;
    private final View touch;
    private final int slop;
    private VelocityTracker velocity;
    private float downX, downY, scrubX;
    private int axis, prevOffset, lastSeconds = -1, lastDuration = -1;
    private int previewIndex = Integer.MIN_VALUE, scrubTarget;
    private int watchPosition = -1, watchRevision;
    private long watchSampleAt;
    private WatchProgress progress;
    private String previewKey;
    private long lastSeekAt;
    private boolean disposed, foreground, draggingSeek, held, moving, rendered, showTimelineBriefly, edgeScrub, timelineShown, playShown;
    private boolean previewLandscape;
    final VideoPlayer player;
    VideoLibrary.Video video;
    private final Runnable showSpinner = this::showSpinnerIfLoading;
    private final Runnable hideTimeline = this::finishTimelinePreview;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (disposed || !foreground) return;
            boolean tracking = video != null && !a.isWatched(video);
            if (tracking) sampleWatch();
            if (timelineShown)
                updateTime(draggingSeek ? scrubTarget : player.position(), player.duration());
            int watchDelay = Math.max(45, Math.min(260, player.duration() / 8));
            clock.postDelayed(this, timelineShown ? Math.min(watchDelay, 140) : tracking && rendered ? watchDelay : 650);
        }
    };

    FeedPane(MainActivity a) {
        super(a);
        this.a = a;
        slop = ViewConfiguration.get(a).getScaledTouchSlop();
        setBackgroundColor(Color.BLACK);
        setClipChildren(true);
        preview = new FrameLayout(a);
        preview.setBackgroundColor(Color.BLACK);
        preview.setVisibility(INVISIBLE);
        preview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        previewArt = new PosterFrame(a);
        preview.addView(previewArt, a.place(MATCH, MATCH, Gravity.CENTER));
        previewShade = scrim(false);
        preview.addView(previewShade, a.place(MATCH, a.dp(144), Gravity.BOTTOM));
        previewMeta = a.column();
        previewAlbum = a.text("", 17, WHITE, "sans-serif-medium");
        previewAlbum.setSingleLine(true);
        previewAlbum.setEllipsize(TextUtils.TruncateAt.END);
        previewMeta.addView(previewAlbum, new LinearLayout.LayoutParams(MATCH, WRAP));
        previewTitle = a.text("", 14, 0xfff1f1f1, "sans-serif");
        previewTitle.setMaxLines(2);
        previewTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pt = new LinearLayout.LayoutParams(MATCH, WRAP);
        pt.topMargin = a.dp(5);
        previewMeta.addView(previewTitle, pt);
        FrameLayout.LayoutParams ppt = a.place(MATCH, WRAP, Gravity.BOTTOM);
        ppt.setMargins(a.dp(15), 0, a.dp(76), a.dp(18));
        preview.addView(previewMeta, ppt);
        addView(preview, a.place(MATCH, MATCH, Gravity.CENTER));

        live = new FrameLayout(a);
        live.setBackgroundColor(Color.BLACK);
        addView(live, a.place(MATCH, MATCH, Gravity.CENTER));
        surface = new TextureView(a);
        surface.setOpaque(false);
        surface.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        live.addView(surface, a.place(MATCH, MATCH, Gravity.CENTER));
        poster = new PosterFrame(a);
        poster.setBackgroundColor(Color.BLACK);
        live.addView(poster, a.place(MATCH, MATCH, Gravity.CENTER));
        topShade = scrim(true);
        bottomShade = scrim(false);
        live.addView(topShade, a.place(MATCH, a.dp(82), Gravity.TOP));
        live.addView(bottomShade, a.place(MATCH, a.dp(158), Gravity.BOTTOM));

        touch = new View(a) {
            @Override public boolean performClick() {
                super.performClick();
                togglePlayback();
                return true;
            }
            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo n) {
                super.onInitializeAccessibilityNodeInfo(n);
                n.setClassName("android.widget.Button");
                n.setScrollable(a.total() > 1 || a.canFinishFeed(1));
                n.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Video options"));
                if (a.neighbor(1) != null || a.canFinishFeed(1))
                    n.addAction(new AccessibilityNodeInfo.AccessibilityAction(NEXT, "Next video"));
                if (a.neighbor(-1) != null) n.addAction(new AccessibilityNodeInfo.AccessibilityAction(PREV, "Previous video"));
            }
            @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
                if (action == NEXT || action == PREV) {
                    animateStep(action == NEXT ? 1 : -1);
                    return true;
                }
                return super.performAccessibilityAction(action, arguments);
            }
        };
        touch.setClickable(true);
        touch.setFocusable(true);
        touch.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        touch.setOnLongClickListener(v -> { a.videoOptions(video); return true; });
        live.addView(touch, a.place(MATCH, MATCH, Gravity.CENTER));
        gestures = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (video != null && !moving) touch.performClick();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (video != null && !moving) saveWithFeedback(e.getX(), e.getY());
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (axis != 0 || moving || disposed || video == null) return;
                float x = e.getX();
                float side = Math.min(a.dp(104), touch.getWidth() * .22f);
                if ((x < side || x > touch.getWidth() - side) && player.setSpeed(2f)) {
                    held = true;
                    speed.setVisibility(VISIBLE);
                    touch.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    touch.performLongClick();
                }
            }
        });
        touch.setOnTouchListener((v, e) -> handleTouch(e));

        meta = a.column();
        meta.setGravity(Gravity.START);
        album = a.text("", 17, WHITE, "sans-serif-medium");
        album.setSingleLine(true);
        album.setEllipsize(TextUtils.TruncateAt.END);
        album.setShadowLayer(a.dp(4), 0, a.dp(1), 0xd9000000);
        album.setPadding(0, a.dp(9), 0, a.dp(5));
        a.click(album, Color.TRANSPARENT, 0, () -> a.openVideoAlbum(video));
        meta.addView(album, new LinearLayout.LayoutParams(WRAP, WRAP));
        title = a.text("", 14, 0xfff1f1f1, "sans-serif");
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(a.dp(3), 0, a.dp(1), 0xd9000000);
        title.setAccessibilityHeading(true);
        meta.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        FrameLayout.LayoutParams mp = a.place(MATCH, WRAP, Gravity.BOTTOM | Gravity.START);
        mp.setMarginStart(a.dp(15));
        mp.setMarginEnd(a.dp(76));
        mp.bottomMargin = a.dp(18);
        live.addView(meta, mp);

        rail = a.column();
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        albumAvatar = new AlbumAvatar(a, 44, true);
        albumAvatar.makeClickable(() -> a.openVideoAlbum(video));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(MATCH, a.dp(56));
        avatarLp.bottomMargin = a.dp(5);
        rail.addView(albumAvatar, avatarLp);
        favGlyph = new Glyph(a, "heart", WHITE, 35).shadowed();
        favButton = actionIcon(rail, favGlyph, "Add to Favorites", () -> a.favorite(video, true));
        actionIcon(rail, new Glyph(a, "forward", WHITE, 33).shadowed(), "Share video", () -> a.share(video));
        actionIcon(rail, new Glyph(a, "trash", WHITE, 29).shadowed(), "Move video to Trash", () -> a.trash(video));
        FrameLayout.LayoutParams rp = a.place(a.dp(57), WRAP, Gravity.BOTTOM | Gravity.END);
        rp.setMarginEnd(a.dp(6));
        rp.bottomMargin = a.dp(28);
        live.addView(rail, rp);

        timeline = a.column();
        timeline.setGravity(Gravity.BOTTOM);
        LinearLayout stamps = a.row();
        stamps.setPadding(a.dp(14), 0, a.dp(14), 0);
        time = readout("0:00");
        length = readout("0:00");
        stamps.addView(time, new LinearLayout.LayoutParams(WRAP, WRAP));
        stamps.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        stamps.addView(length, new LinearLayout.LayoutParams(WRAP, WRAP));
        timeline.addView(stamps, new LinearLayout.LayoutParams(MATCH, WRAP));
        seek = new SeekBar(a);
        seek.setContentDescription("Video position");
        seek.setMax(1);
        seek.setProgressTintList(ColorStateList.valueOf(WHITE));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x74ffffff));
        GradientDrawable knob = a.round(WHITE, 6);
        knob.setSize(a.dp(9), a.dp(9));
        seek.setThumb(knob);
        seek.setThumbOffset(a.dp(5));
        seek.setPadding(a.dp(14), 0, a.dp(14), 0);
        seek.setSplitTrack(false);
        timeline.addView(seek, new LinearLayout.LayoutParams(MATCH, a.dp(31)));
        live.addView(timeline, a.place(MATCH, WRAP, Gravity.BOTTOM));
        timeline.setVisibility(INVISIBLE);

        scrubEdge = new View(a);
        scrubEdge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrubEdge.setOnTouchListener((v, e) -> scrubFromEdge(e));
        live.addView(scrubEdge, a.place(MATCH, a.dp(14), Gravity.BOTTOM));

        playGlyph = new Glyph(a, "play", 0xeaffffff, 39).shadowed();
        playGlyph.setVisibility(GONE);
        live.addView(playGlyph, a.place(a.dp(54), a.dp(54), Gravity.CENTER));
        spinner = new ProgressBar(a);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(WHITE));
        spinner.setVisibility(GONE);
        spinner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        live.addView(spinner, a.place(a.dp(28), a.dp(28), Gravity.CENTER));
        failure = a.text("This video couldn’t play. Tap to retry.", 13, WHITE, "sans-serif-medium");
        failure.setShadowLayer(a.dp(4), 0, a.dp(1), Color.BLACK);
        failure.setVisibility(GONE);
        live.addView(failure, a.place(WRAP, WRAP, Gravity.CENTER));
        failure.setTranslationY(a.dp(43));

        burst = new FavoriteBurst(a);
        live.addView(burst, a.place(MATCH, MATCH, Gravity.CENTER));

        backButton = bareIcon("left", "Back to collection", a::onBackPressed, 28);
        addView(backButton, a.place(a.dp(48), a.dp(48), Gravity.TOP | Gravity.START));
        backButton.setVisibility(a.inCollection() ? VISIBLE : GONE);
        exitClear = bareIcon("expand-exit", "Exit Clear display", () -> a.setClearDisplay(false), 22);
        FrameLayout.LayoutParams xp = a.place(a.dp(52), a.dp(52), Gravity.BOTTOM | Gravity.END);
        xp.setMarginEnd(a.dp(9));
        xp.bottomMargin = a.dp(14);
        addView(exitClear, xp);
        exitClear.setVisibility(GONE);

        speed = a.text("»  2× speed", 13, WHITE, "sans-serif-medium");
        speed.setShadowLayer(a.dp(4), 0, a.dp(1), Color.BLACK);
        speed.setVisibility(GONE);
        FrameLayout.LayoutParams fp = a.place(WRAP, WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        fp.topMargin = a.dp(60);
        addView(speed, fp);
        trashHint = a.text("Move to Trash", 15, WHITE, "sans-serif-medium");
        trashHint.setShadowLayer(a.dp(5), 0, a.dp(1), Color.BLACK);
        trashHint.setVisibility(GONE);
        addView(trashHint, a.place(WRAP, WRAP, Gravity.CENTER));

        player = new VideoPlayer(a, surface, new VideoPlayer.Events() {
            @Override public void stateChanged() { updateState(); }
            @Override public void firstFrame() {
                rendered = true;
                sampleWatch();
                clock.removeCallbacks(tick);
                clock.post(tick);
                clock.removeCallbacks(showSpinner);
                spinner.setVisibility(GONE);
                poster.animate().cancel();
                poster.animate().alpha(0).setDuration(145).setInterpolator(Motion.ENTER).withEndAction(() -> {
                    if (rendered) poster.setVisibility(INVISIBLE);
                }).start();
            }
        });
        player.setMuted(a.mute);
        player.setPlaybackRate(a.playbackRate);
        applyFit();
        updateState();
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                clock.removeCallbacks(hideTimeline);
                draggingSeek = true;
                scrubTarget = bar.getProgress();
                updateTimeline();
            }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (draggingSeek) seekPreview(progress);
                else player.seek(progress);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) { finishScrub(bar.getProgress()); }
        });
    }

    private View scrim(boolean top) {
        View gradient = new View(a);
        gradient.setBackground(new GradientDrawable(top ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP,
                top ? new int[]{0x9c000000, 0x38000000, 0x00000000} : new int[]{0xc9000000, 0x53000000, 0x00000000}));
        return gradient;
    }

    private TextView readout(String initial) {
        TextView t = a.text(initial, 11, WHITE, "sans-serif-medium");
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setShadowLayer(a.dp(2), 0, a.dp(1), Color.BLACK);
        t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    private FrameLayout bareIcon(String icon, String description, Runnable action, int size) {
        return bareIcon(new Glyph(a, icon, WHITE, size).shadowed(), description, action);
    }

    private FrameLayout bareIcon(Glyph glyph, String description, Runnable action) {
        FrameLayout button = new FrameLayout(a);
        button.addView(glyph, a.place(MATCH, MATCH, Gravity.CENTER));
        button.setContentDescription(description);
        a.click(button, Color.TRANSPARENT, 0, action);
        Motion.press(button, glyph);
        return button;
    }

    private FrameLayout actionIcon(LinearLayout rail, Glyph glyph, String description, Runnable action) {
        FrameLayout button = bareIcon(glyph, description, action);
        rail.addView(button, new LinearLayout.LayoutParams(MATCH, a.dp(54)));
        return button;
    }

    void showVideo(VideoLibrary.Video next, int where) { showVideo(next, where, null); }

    void showVideo(VideoLibrary.Video next, int where, Bitmap retainedFrame) {
        if (next == null || disposed) return;
        if (moving) resetDrag();
        burst.clear();
        finishTimelinePreview();
        spinner.setVisibility(GONE);
        boolean switching = video != null && !video.key.equals(next.key);
        video = next;
        progress = a.isWatched(next) ? null : a.watchProgress(next);
        watchPosition = -1;
        title.setText(next.title);
        album.setText(next.album);
        rendered = false;
        poster.animate().cancel();
        poster.setAlpha(1);
        poster.setFit(a.fit || next.landscape());
        poster.setBitmap(retainedFrame != null ? retainedFrame : a.cached(next));
        poster.setVisibility(VISIBLE);
        if (retainedFrame == null) a.thumbnail(next, bitmap -> {
            if (!disposed && !rendered && video != null && next.key.equals(video.key)) poster.setBitmap(bitmap);
        });
        lastSeconds = lastDuration = -1;
        seek.setProgress(0);
        updateFavorite(false);
        player.open(next, where);
        refreshNeighbors();
        updateState();
        updateTime(where, player.duration());
        if (switching && !a.clearDisplay && Motion.enabled()) {
            rail.setAlpha(.76f);
            rail.animate().alpha(1f).setDuration(200).setInterpolator(Motion.ENTER).start();
        }
    }

    Bitmap frameForReturn() {
        if (rendered && surface.isAvailable() && surface.getWidth() > 0 && surface.getHeight() > 0) {
            int width = Math.min(280, surface.getWidth());
            int height = Math.max(1, Math.min(680, Math.round((float) width * surface.getHeight() / surface.getWidth())));
            try {
                Bitmap frame = surface.getBitmap(width, height);
                if (frame != null) return frame;
            } catch (RuntimeException ignored) { }
        }
        return a.cached(video);
    }

    void refreshNeighbors() {
        VideoLibrary.Album cover = a.albumOf(video);
        albumAvatar.bind(cover);
        albumAvatar.setContentDescription(cover == null ? "Open album" : "Open " + cover.title + " album");
        albumAvatar.setVisibility(cover == null ? GONE : VISIBLE);
        a.prefetch(a.neighbor(1));
        a.prefetch(a.neighbor(-1));
        a.prefetch(a.at(a.index() + 2));
        a.prefetch(a.at(a.index() - 2));
    }

    void updateFavorite(boolean animate) {
        boolean liked = a.isFavorite(video);
        favGlyph.update(liked ? "heart-filled" : "heart", liked ? FAVORITE : WHITE);
        favButton.setContentDescription(liked ? "Remove from Favorites" : "Add to Favorites");
        favButton.setSelected(liked);
        if (animate && liked) favButton.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (animate && Motion.enabled()) {
            favGlyph.animate().cancel();
            float start = liked ? .8f : .92f;
            favGlyph.setScaleX(start); favGlyph.setScaleY(start);
            favGlyph.animate().scaleX(1).scaleY(1).setDuration(300).setInterpolator(Motion.POP).start();
        }
    }

    private void saveWithFeedback(float x, float y) {
        a.favorite(video, false);
        if (!a.clearDisplay) burst.play(x, y);
    }

    void applyFit() {
        player.setFit(a.fit);
        poster.setFit(a.fit || video != null && video.landscape());
        previewArt.setFit(a.fit || previewLandscape);
    }

    void setSafeTop(int inset) {
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) backButton.getLayoutParams();
        int margin = inset + a.dp(2);
        if (bp.topMargin != margin) { bp.topMargin = margin; backButton.setLayoutParams(bp); }
        FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) speed.getLayoutParams();
        int speedMargin = (a.clearDisplay ? 0 : inset) + a.dp(14);
        if (sp.topMargin != speedMargin) { sp.topMargin = speedMargin; speed.setLayoutParams(sp); }
    }

    void applyClearDisplay() {
        int regular = a.clearDisplay ? GONE : VISIBLE;
        for (View view : new View[]{rail, meta, topShade, bottomShade, previewMeta, previewShade}) view.setVisibility(regular);
        burst.clear();
        backButton.setVisibility(!a.clearDisplay && a.inCollection() ? VISIBLE : GONE);
        exitClear.setVisibility(a.clearDisplay ? VISIBLE : GONE);
        finishTimelinePreview();
        updateState();
    }

    void foreground(boolean show) {
        if (show == foreground) return;
        if (!show) sampleWatch();
        foreground = show;
        if (!show) {
            clock.removeCallbacks(tick);
            endHold();
            burst.clear();
            resetDrag();
            finishTimelinePreview();
        }
        player.setForeground(show);
        if (show) clock.post(tick);
        updateState();
    }

    void dispose() {
        sampleWatch();
        disposed = true;
        clock.removeCallbacksAndMessages(null);
        if (velocity != null) { velocity.recycle(); velocity = null; }
        live.animate().cancel();
        preview.animate().cancel();
        poster.animate().cancel();
        player.close();
    }

    private void togglePlayback() {
        if (disposed || video == null) return;
        sampleWatch();
        if (!player.intendsToPlay()) finishTimelinePreview();
        player.toggle();
    }

    void sampleWatch() {
        if (disposed || !foreground || !rendered || progress == null || a.isWatched(video)
                || draggingSeek || player.seeking() || !player.playing()) {
            watchPosition = -1;
            return;
        }
        int duration = player.duration();
        if (duration <= 0) return;
        int current = Math.min(duration, player.position());
        int revision = player.playbackRevision();
        long now = SystemClock.uptimeMillis();
        if (watchPosition >= 0 && revision == watchRevision) {
            long plausibleAdvance = (now - watchSampleAt) * 2 + 150;
            if (current >= watchPosition && current - watchPosition <= plausibleAdvance) {
                progress.add(watchPosition, current);
            } else if (current < watchPosition && (long) duration - watchPosition + current <= plausibleAdvance) {
                progress.add(watchPosition, duration);
                progress.add(0, current);
            }
            if (progress.watched(duration)) {
                a.recordWatch(video);
                progress = null;
                refreshNeighbors();
            }
        }
        watchPosition = current;
        watchRevision = revision;
        watchSampleAt = now;
    }

    void stopTrackingCurrent() {
        progress = null;
        watchPosition = -1;
    }

    private void showSpinnerIfLoading() {
        if (!disposed && foreground && !rendered && player.intendsToPlay()) spinner.setVisibility(VISIBLE);
    }

    private void updateState() {
        if (disposed || player == null) return;
        boolean playing = player.playing(), expects = player.intendsToPlay(), failed = player.failed();
        String label = failed ? "Retry video" : expects ? "Pause video" : "Play video";
        touch.setContentDescription((video != null ? video.title + ". " : "") + label);
        failure.setVisibility(failed ? VISIBLE : GONE);
        showPlayGlyph(foreground && !a.clearDisplay && !expects && !failed);
        clock.removeCallbacks(showSpinner);
        if (foreground && expects && !rendered) clock.postDelayed(showSpinner, 700);
        else spinner.setVisibility(GONE);
        setKeepScreenOn(foreground && playing);
        updateTimeline();
        updateTime(draggingSeek ? scrubTarget : player.position(), player.duration());
    }

    private void updateTimeline() {
        if (player == null) return;
        boolean visible = !a.clearDisplay && foreground && player.ready()
                && (draggingSeek || showTimelineBriefly || !player.intendsToPlay());
        scrubEdge.setVisibility((!visible && !a.clearDisplay && player.ready()) || edgeScrub ? VISIBLE : GONE);
        seek.setEnabled(visible);
        if (timelineShown == visible && !a.clearDisplay) return;
        timelineShown = visible;
        timeline.setImportantForAccessibility(visible ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int shift = visible ? -a.dp(38) : 0;
        timeline.animate().cancel();
        if (!foreground || a.clearDisplay || !Motion.enabled()) {
            timeline.setAlpha(visible ? 1f : 0f);
            timeline.setVisibility(visible ? VISIBLE : INVISIBLE);
            meta.setTranslationY(shift);
            rail.setTranslationY(shift);
        } else {
            if (visible) {
                timeline.setAlpha(0f);
                timeline.setVisibility(VISIBLE);
            }
            int duration = visible ? 170 : 120;
            timeline.animate().alpha(visible ? 1f : 0f).setDuration(duration).setInterpolator(Motion.SETTLE)
                    .withEndAction(() -> { if (!timelineShown) timeline.setVisibility(INVISIBLE); }).start();
            meta.animate().translationY(shift).setDuration(duration).setInterpolator(Motion.SETTLE).start();
            rail.animate().translationY(shift).setDuration(duration).setInterpolator(Motion.SETTLE).start();
        }
        if (visible) updateTime(draggingSeek ? scrubTarget : player.position(), player.duration());
    }

    private void showPlayGlyph(boolean visible) {
        if (playShown == visible && !a.clearDisplay) return;
        playShown = visible;
        playGlyph.animate().cancel();
        if (!foreground || a.clearDisplay || !Motion.enabled()) {
            playGlyph.setAlpha(visible ? 1f : 0f);
            playGlyph.setScaleX(1f);
            playGlyph.setScaleY(1f);
            playGlyph.setVisibility(visible ? VISIBLE : GONE);
        } else if (visible) {
            playGlyph.setVisibility(VISIBLE);
            playGlyph.setAlpha(0f);
            playGlyph.setScaleX(.76f);
            playGlyph.setScaleY(.76f);
            playGlyph.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(Motion.ENTER).start();
        } else {
            playGlyph.animate().alpha(0f).scaleX(1.16f).scaleY(1.16f).setDuration(120)
                    .setInterpolator(Motion.EXIT).withEndAction(() -> { if (!playShown) playGlyph.setVisibility(GONE); }).start();
        }
    }

    private void updateTime(int ms, int dur) {
        if (seek.getMax() != Math.max(1, dur)) seek.setMax(Math.max(1, dur));
        if ((!draggingSeek || edgeScrub) && seek.getProgress() != ms) seek.setProgress(ms);
        if (lastSeconds != ms / 1000) { lastSeconds = ms / 1000; time.setText(time(ms)); }
        if (lastDuration != dur) { lastDuration = dur; length.setText(time(dur)); }
    }

    private boolean scrubFromEdge(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (a.clearDisplay || !player.ready()) return false;
                edgeScrub = true;
                scrubX = event.getX();
                scrubTarget = player.position();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!edgeScrub) return false;
                if (!draggingSeek && Math.abs(event.getX() - scrubX) <= slop) return true;
                draggingSeek = true;
                updateTimeline();
                seekPreview(edgePosition(event.getX()));
                return true;
            case MotionEvent.ACTION_UP:
                if (draggingSeek) finishScrub(edgePosition(event.getX()));
                else {
                    edgeScrub = false;
                    showTimelineBriefly = true;
                    clock.removeCallbacks(hideTimeline);
                    clock.postDelayed(hideTimeline, 1600);
                    updateTimeline();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishTimelinePreview();
                return true;
            default: return true;
        }
    }

    private int edgePosition(float x) {
        float start = a.dp(14);
        float f = (x - start) / Math.max(1, scrubEdge.getWidth() - 2 * start);
        return Math.round(Math.max(0f, Math.min(1f, f)) * player.duration());
    }

    private void seekPreview(int target) {
        scrubTarget = target;
        updateTime(target, player.duration());
        long now = SystemClock.uptimeMillis();
        if (now - lastSeekAt > 125) {
            lastSeekAt = now;
            player.seek(target);
        }
    }

    private void finishScrub(int target) {
        draggingSeek = false;
        edgeScrub = false;
        showTimelineBriefly = true;
        player.seek(target);
        clock.removeCallbacks(hideTimeline);
        clock.postDelayed(hideTimeline, 1200);
        updateTimeline();
    }

    private void finishTimelinePreview() {
        clock.removeCallbacks(hideTimeline);
        showTimelineBriefly = false;
        draggingSeek = false;
        edgeScrub = false;
        updateTimeline();
    }

    private boolean handleTouch(MotionEvent e) {
        if (moving || disposed) return true;
        int type = e.getActionMasked();
        if (type == MotionEvent.ACTION_DOWN) {
            live.animate().cancel();
            preview.animate().cancel();
            resetDrag();
            downX = e.getRawX();
            downY = e.getRawY();
            axis = 0;
            prevOffset = 0;
            if (velocity != null) velocity.recycle();
            velocity = VelocityTracker.obtain();
        }
        if (velocity != null) {
            MotionEvent raw = MotionEvent.obtain(e);
            raw.offsetLocation(e.getRawX() - e.getX(), e.getRawY() - e.getY());
            velocity.addMovement(raw);
            raw.recycle();
        }
        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
        if (type == MotionEvent.ACTION_MOVE && axis == 0 && !held
                && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
            axis = Math.abs(dy) >= Math.abs(dx) * 1.12f ? 1 : 2;
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            gestures.onTouchEvent(cancel);
            cancel.recycle();
        }
        if (axis == 0) gestures.onTouchEvent(e);
        if (type == MotionEvent.ACTION_MOVE) {
            if (axis == 1) {
                int direction = dy < 0 ? 1 : -1;
                if (a.neighbor(direction) == null) {
                    preview.setVisibility(INVISIBLE);
                    live.setTranslationY(dy * .14f);
                } else {
                    setPreview(direction);
                    float delta = Math.max(-getHeight(), Math.min(getHeight(), dy));
                    live.setTranslationY(delta);
                    preview.setTranslationY(delta + direction * getHeight());
                    prevOffset = direction;
                }
            } else if (axis == 2 && a.swipeTrash) {
                trashHint.setVisibility(dx < -slop ? VISIBLE : GONE);
                trashHint.setAlpha(Math.max(0, Math.min(1, (-dx - slop) / a.dp(52))));
                live.setTranslationX(Math.max(-a.dp(39), Math.min(0, dx * .22f)));
            }
        }
        if (type == MotionEvent.ACTION_UP || type == MotionEvent.ACTION_CANCEL) {
            if (velocity != null) velocity.computeCurrentVelocity(1000);
            float vy = velocity == null ? 0 : velocity.getYVelocity();
            if (velocity != null) { velocity.recycle(); velocity = null; }
            endHold();
            if (type == MotionEvent.ACTION_UP && axis == 1) {
                sampleWatch();
                int direction = dy < 0 ? 1 : -1;
                float threshold = Math.min(a.dp(104), getHeight() * .21f);
                if ((a.neighbor(direction) != null || a.canFinishFeed(direction)) && (Math.abs(dy) >= threshold
                        || (Math.abs(dy) >= a.dp(24) && Math.abs(vy) >= a.dp(740) && vy * dy > 0))) {
                    animateStep(direction, vy);
                    axis = 0;
                    return true;
                }
            }
            boolean trash = type == MotionEvent.ACTION_UP && axis == 2 && a.swipeTrash
                    && dx < -Math.max(a.dp(92), getWidth() * .25f);
            trashHint.setVisibility(GONE);
            if (axis != 0) settle();
            axis = 0;
            if (trash) {
                performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                a.trash(video);
            }
        }
        return true;
    }

    private void endHold() {
        if (!held) return;
        held = false;
        speed.setVisibility(GONE);
        player.resetSpeed();
    }

    private void setPreview(int direction) {
        int target = a.index() + direction;
        VideoLibrary.Video next = a.neighbor(direction);
        if (next == null) return;
        preview.setVisibility(VISIBLE);
        if (previewIndex == target && next.key.equals(previewKey)) return;
        previewIndex = target;
        previewKey = next.key;
        previewLandscape = next.landscape();
        previewArt.setFit(a.fit || previewLandscape);
        previewArt.setBitmap(a.cached(next));
        previewTitle.setText(next.title);
        previewAlbum.setText(next.album);
        a.thumbnail(next, bitmap -> {
            if (!disposed && next.key.equals(previewKey)) previewArt.setBitmap(bitmap);
        });
    }

    private void animateStep(int direction) { animateStep(direction, 0f); }

    private void animateStep(int direction, float velocityY) {
        if (disposed || moving) return;
        if (a.canFinishFeed(direction)) {
            a.step(direction);
            return;
        }
        if (a.neighbor(direction) == null) return;
        if (!Motion.enabled() || getHeight() == 0) {
            a.step(direction);
            resetDrag();
            return;
        }
        moving = true;
        setPreview(direction);
        float rest = live.getTranslationY() + direction * getHeight();
        preview.setTranslationY(rest);
        float speed = velocityY * direction < 0f ? Math.abs(velocityY) : 0f;
        int duration = Math.max(145, Math.min(290, (int) (1000f * Math.abs(rest) / (a.dp(1780) + speed))));
        preview.animate().translationY(0).setDuration(duration).setInterpolator(Motion.ENTER).start();
        live.animate().translationY(-direction * getHeight()).setDuration(duration).setInterpolator(Motion.ENTER)
                .withEndAction(() -> {
                    if (disposed || !moving) return;
                    a.step(direction);
                    resetDrag();
                }).start();
    }

    private void settle() {
        if (!Motion.enabled()) { resetDrag(); return; }
        int duration = Math.max(120, Math.min(205, 120 + (int) (Math.abs(live.getTranslationY()) / 8)));
        if (preview.getVisibility() == VISIBLE) {
            preview.animate().translationY(prevOffset * getHeight()).setInterpolator(Motion.SETTLE).setDuration(duration).start();
        }
        live.animate().translationX(0).translationY(0).setInterpolator(Motion.SETTLE).setDuration(duration)
                .withEndAction(() -> {
                    if (axis == 0 && !moving) preview.setVisibility(INVISIBLE);
                }).start();
    }

    private void resetDrag() {
        live.animate().cancel();
        preview.animate().cancel();
        live.setTranslationX(0); live.setTranslationY(0);
        preview.setVisibility(INVISIBLE);
        trashHint.setVisibility(GONE);
        moving = false;
    }
}
