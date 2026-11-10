package app.reelback.gallery;

import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;

final class VideoPlayer implements TextureView.SurfaceTextureListener {
    interface Events {
        void stateChanged();
        void firstFrame();
    }

    private final MainActivity context;
    private final TextureView view;
    private final Events events;
    private final AudioManager audio;
    private final AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
    private final AudioFocusRequest focusRequest;
    private MediaPlayer player;
    private Surface surface;
    private VideoLibrary.Video video;
    private boolean foreground, allowed = true, fit, muted, focus, ready, failed, seeking, opening;
    private int width, height, resume, seekTarget, mediaDuration, playbackRevision;
    private float speed = 1f, playbackRate = 1f;

    VideoPlayer(MainActivity context, TextureView view, Events events) {
        this.context = context;
        this.view = view;
        this.events = events;
        audio = context.getSystemService(AudioManager.class);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0 && focus) pause();
                }).build();
        view.setSurfaceTextureListener(this);
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> transform());
        if (view.isAvailable() && view.getSurfaceTexture() != null) {
            surface = new Surface(view.getSurfaceTexture());
        }
    }

    void open(VideoLibrary.Video next, int position) {
        release();
        video = next;
        resume = Math.max(position, 0);
        allowed = true;
        failed = false;
        speed = 1f;
        openIfPossible();
    }

    void close() { video = null; resume = 0; failed = false; release(); }

    void setForeground(boolean value) {
        if (foreground == value) return;
        foreground = value;
        if (value) {
            if (player == null && !failed) openIfPossible();
            startIfPossible();
        } else {
            resume = position();
            stopPlaying();
            release();
        }
    }

    void setFit(boolean value) { fit = value; transform(); }

    boolean setMuted(boolean value) {
        muted = value;
        if (muted) abandonFocus();
        if (!muted && playing() && !requestFocus()) pause();
        if (player != null) try { player.setVolume(muted ? 0 : 1, muted ? 0 : 1); }
        catch (RuntimeException ignored) { }
        return muted;
    }

    boolean setSpeed(float value) {
        if (!playing()) return false;
        try {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(value).setPitch(1f));
            speed = value;
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    void setPlaybackRate(float rate) {
        playbackRate = rate;
        if (playing()) setSpeed(rate);
    }

    void resetSpeed() { if (speed != playbackRate) setSpeed(playbackRate); }
    boolean playing() {
        if (!ready || player == null) return false;
        try { return player.isPlaying(); } catch (RuntimeException ignored) { return false; }
    }
    boolean failed() { return failed; }
    boolean ready() { return ready; }
    boolean seeking() { return seeking; }
    int playbackRevision() { return playbackRevision; }
    boolean intendsToPlay() { return allowed && !failed; }

    void toggle() {
        if (failed && video != null) { open(video, resume); return; }
        if (allowed) pause(); else play();
    }
    void play() { allowed = true; startIfPossible(); }
    void pause() { allowed = false; stopPlaying(); }

    int duration() {
        if (ready && mediaDuration > 0) return mediaDuration;
        return video == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, video.duration));
    }

    int position() {
        if (seeking) return seekTarget;
        if (ready && player != null) {
            try { return Math.max(0, player.getCurrentPosition()); } catch (RuntimeException ignored) { }
        }
        return resume;
    }

    void seek(int ms) {
        if (!ready || player == null) return;
        int position = Math.max(0, Math.min(ms, Math.max(0, duration() - 1)));
        try {
            playbackRevision++;
            seeking = true;
            seekTarget = position;
            player.seekTo(position, MediaPlayer.SEEK_CLOSEST);
        } catch (RuntimeException ignored) { seeking = false; }
    }

    private boolean requestFocus() {
        if (muted || focus) return true;
        focus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focus;
    }
    private void abandonFocus() {
        if (focus) {
            focus = false;
            audio.abandonAudioFocusRequest(focusRequest);
        }
    }

    private void startIfPossible() {
        if (!foreground || !ready || !allowed || seeking || playing()) {
            events.stateChanged();
            return;
        }
        try {
            if (requestFocus()) {
                player.start();
                resetSpeed();
            } else allowed = false;
        } catch (RuntimeException e) { error(); return; }
        events.stateChanged();
    }

    private void stopPlaying() {
        resetSpeed();
        if (playing()) try { player.pause(); } catch (RuntimeException ignored) { }
        abandonFocus();
        events.stateChanged();
    }

    private void openIfPossible() {
        if (surface == null || video == null || !foreground || player != null || opening) {
            events.stateChanged();
            return;
        }
        opening = true;
        int revision = playbackRevision;
        context.openFile(video, file -> {
            if (revision != playbackRevision || !foreground || surface == null) return;
            opening = false;
            if (file == null) error();
            else prepare(file);
        });
        events.stateChanged();
    }

    private void prepare(AssetFileDescriptor file) {
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(attrs);
            mp.setSurface(surface);
            mp.setDataSource(file);
            mp.setLooping(true);
            mp.setVolume(muted ? 0 : 1, muted ? 0 : 1);
            mp.setOnVideoSizeChangedListener((p, w, h) -> {
                if (player == p) { width = w; height = h; transform(); }
            });
            mp.setOnPreparedListener(p -> {
                if (player != p) return;
                ready = true;
                mediaDuration = Math.max(0, p.getDuration());
                width = p.getVideoWidth();
                height = p.getVideoHeight();
                transform();
                int where = resume;
                resume = 0;
                if (where > 0 && where < duration() - 350) seek(where);
                startIfPossible();
            });
            mp.setOnSeekCompleteListener(p -> {
                if (player == p) {
                    seeking = false;
                    startIfPossible();
                }
            });
            mp.setOnInfoListener((p, code, extra) -> {
                if (player == p && code == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    events.firstFrame();
                    return true;
                }
                return false;
            });
            mp.setOnErrorListener((p, a, b) -> {
                if (player == p) error();
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) { error(); }
    }

    private void error() {
        release();
        failed = true;
        allowed = false;
        events.stateChanged();
    }

    private void release() {
        abandonFocus();
        if (player != null) {
            player.release();
            player = null;
        }
        ready = false;
        seeking = false;
        opening = false;
        mediaDuration = 0;
        playbackRevision++;
        speed = 1f;
        width = height = 0;
    }

    private void transform() {
        int vw = view.getWidth(), vh = view.getHeight();
        if (vw == 0 || vh == 0 || width == 0 || height == 0) {
            view.setTransform(null);
            return;
        }
        float sx = (float) vw / width, sy = (float) vh / height;
        boolean wide = video != null && video.width > 0 && video.height > 0 ? video.landscape() : width > height;
        float scale = fit || wide ? Math.min(sx, sy) : Math.max(sx, sy);
        Matrix m = new Matrix();
        m.setScale(scale / sx, scale / sy, vw / 2f, vh / 2f);
        view.setTransform(m);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (surface != null) surface.release();
        surface = new Surface(texture);
        openIfPossible();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { transform(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        resume = position();
        release();
        if (surface != null) { surface.release(); surface = null; }
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
}
