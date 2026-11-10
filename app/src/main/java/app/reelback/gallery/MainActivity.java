package app.reelback.gallery;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    static final int BG = 0xff100e15, PANEL = 0xff1b1723, EDGE = 0xff30283b, PAPER = 0xfff8f4fb;
    static final int SUB = 0xffbfb6c9, VIOLET = 0xffd1beff, INK = 0xff1b1128, MINT = 0xffcdf49a;
    static final int FEED = 0, SAVED = 1, HISTORY = 2, ALBUMS = 3;
    static final int MATCH = -1, WRAP = -2;
    private static final int ACCESS = 12, TRASH = 13;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService scan = Executors.newSingleThreadExecutor();
    private final ExecutorService historyWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private final Runnable rescan = this::refresh;
    private SharedPreferences prefs;
    private FrameLayout stage;
    private LinearLayout shell, nav;
    private Dialog videoSheet, albumEditor;
    private AlbumPicker setupPicker, editPicker;
    private FeedSources sources;
    private WatchHistory historyDb;
    private boolean historyReady;
    private VideoLibrary.Thumbnails thumbs;
    private List<VideoLibrary.Video> library = new ArrayList<>(), queue = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private final Map<String, Long> watched = new HashMap<>();
    private final LruCache<String, WatchProgress> unfinished = new LruCache<>(64);
    private final Map<String, VideoLibrary.Album> albums = new LinkedHashMap<>();
    private FeedPane pane;
    private int tab = FEED, position, order, scanVersion, lastAccess = -1, gridIndex, gridOffset;
    private long shuffleSeed;
    private String albumKey, feedKey, pendingTrash;
    private int feedPosition;
    private boolean collectionPlayer, active, loaded, loadError, observing, registered, destroyed;
    boolean fit, mute, swipeTrash, clearDisplay;
    float playbackRate = 1f;
    private AlbumReturn albumReturn;
    private GridView grid;
    private ContentObserver observer;
    private Toast toast;
    private static final class AlbumReturn {
        final int tab, queueIndex, gridIndex, gridOffset, position;
        final String albumKey, videoKey;
        final boolean collectionPlayer, playing;
        final Bitmap frame;
        AlbumReturn(MainActivity a) {
            tab = a.tab;
            queueIndex = a.position;
            gridIndex = a.gridIndex;
            gridOffset = a.gridOffset;
            albumKey = a.albumKey;
            collectionPlayer = a.collectionPlayer;
            videoKey = a.pane.video.key;
            position = a.pane.player.position();
            playing = a.pane.player.intendsToPlay();
            frame = a.pane.frameForReturn();
        }
    }
    private final BroadcastReceiver unplugged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (pane != null) pane.player.pause(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.BLACK);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        prefs = getSharedPreferences("reelback", MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites", Collections.emptySet()));
        order = prefs.getInt("order", 2);
        shuffleSeed = prefs.getLong("shuffleSeed", System.nanoTime());
        if (!prefs.contains("shuffleSeed")) prefs.edit().putLong("shuffleSeed", shuffleSeed).apply();
        sources = new FeedSources(prefs);
        historyDb = new WatchHistory(this);
        loadHistory();
        fit = prefs.getBoolean("fit", false);
        mute = prefs.getBoolean("mute", false);
        swipeTrash = prefs.getBoolean("swipeTrash", false);
        playbackRate = prefs.getFloat("playbackRate", 1f);
        feedKey = prefs.getString("feedKey", null);
        feedPosition = prefs.getInt("feedPosition", 0);
        if (state != null) pendingTrash = state.getString("pendingTrash");
        thumbs = new VideoLibrary.Thumbnails(getContentResolver());
        observer = new ContentObserver(ui) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                ui.removeCallbacks(rescan);
                ui.postDelayed(rescan, 240);
            }
        };

        shell = column();
        shell.setBackgroundColor(BG);
        stage = new FrameLayout(this);
        shell.addView(stage, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        nav = row();
        nav.setBackgroundColor(Color.BLACK);
        shell.addView(nav, new LinearLayout.LayoutParams(MATCH, dp(54)));
        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int bottom = clearDisplay ? 0 : safe.bottom;
            v.setPadding(safe.left, pane == null ? safe.top : 0, safe.right, bottom);
            if (pane != null) pane.setSafeTop(insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top);
            return insets;
        });
        setContentView(shell);
        shell.requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unplugged, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(unplugged, f);
        registered = true;
        if (access() > 0) {
            if (!observing) {
                getContentResolver().registerContentObserver(VideoLibrary.VIDEOS, true, observer);
                observing = true;
            }
            if (pane != null && access() == lastAccess && access() == 2 && playbackVisible()) pane.foreground(true);
        }
        refresh();
    }

    @Override protected void onPause() {
        active = false;
        rememberFeed();
        if (pane != null) pane.foreground(false);
        if (registered) { unregisterReceiver(unplugged); registered = false; }
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("pendingTrash", pendingTrash);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (videoSheet != null) videoSheet.dismiss();
        if (albumEditor != null) albumEditor.dismiss();
        ui.removeCallbacks(rescan);
        if (observing) getContentResolver().unregisterContentObserver(observer);
        removePane();
        scan.shutdownNow();
        fileWorker.shutdownNow();
        historyWorker.execute(historyDb::close);
        historyWorker.shutdown();
        thumbs.close();
        if (toast != null) toast.cancel();
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN && thumbs != null) thumbs.trim();
    }

    @Override public void onBackPressed() { goBack(); }

    private void goBack() {
        if (clearDisplay) {
            setClearDisplay(false);
        } else if (collectionPlayer) {
            collectionPlayer = false;
            removePane();
            render();
            animateStage(-1);
        } else if (tab == ALBUMS && albumReturn != null) {
            returnToVideo();
        } else if (tab == ALBUMS && albumKey != null) {
            albumKey = null;
            resetGrid();
            render();
            animateStage(-1);
        } else if (tab != FEED) switchTab(FEED);
        else finish();
    }

    int access() {
        String perm = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return 2;
        if (Build.VERSION.SDK_INT >= 34 && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                == PackageManager.PERMISSION_GRANTED) return 1;
        return 0;
    }

    private void requestAccess() {
        String perm = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (access() == 0 && prefs.getBoolean("requestedAccess", false) && !shouldShowRequestPermissionRationale(perm)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            return;
        }
        prefs.edit().putBoolean("requestedAccess", true).apply();
        if (Build.VERSION.SDK_INT >= 34)
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, ACCESS);
        else requestPermissions(new String[]{perm}, ACCESS);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == ACCESS) {
            thumbs.invalidateFailures();
            if (!observing && access() > 0) {
                getContentResolver().registerContentObserver(VideoLibrary.VIDEOS, true, observer);
                observing = true;
            }
            refresh();
        }
    }

    private void loadHistory() {
        historyWorker.execute(() -> {
            Map<String, Long> records;
            try {
                records = historyDb.read();
            } catch (RuntimeException e) {
                Log.e("Reelback", "Couldn't read watch history", e);
                records = Collections.emptyMap();
            }
            Map<String, Long> loadedRecords = records;
            ui.post(() -> {
                if (destroyed) return;
                watched.putAll(loadedRecords);
                historyReady = true;
                if (loaded) {
                    updateQueue(tab == FEED ? feedKey : null, position);
                    render();
                }
            });
        });
    }

    boolean isWatched(VideoLibrary.Video video) {
        return video != null && watched.containsKey(video.key);
    }

    WatchProgress watchProgress(VideoLibrary.Video video) {
        WatchProgress progress = unfinished.get(video.key);
        if (progress == null) {
            progress = new WatchProgress();
            unfinished.put(video.key, progress);
        }
        return progress;
    }

    void recordWatch(VideoLibrary.Video video) {
        if (destroyed || isWatched(video)) return;
        long now = System.currentTimeMillis();
        watched.put(video.key, now);
        unfinished.remove(video.key);
        historyWorker.execute(() -> {
            try {
                historyDb.record(video.key, now);
            } catch (RuntimeException e) {
                Log.e("Reelback", "Couldn't save watch history", e);
            }
        });
    }

    private void refresh() {
        if (destroyed) return;
        int version = ++scanVersion;
        final int allowed = access();
        if (allowed == 0) {
            lastAccess = 0;
            loaded = false;
            library.clear();
            queue.clear();
            albums.clear();
            thumbs.trim();
            render();
            return;
        }
        if (lastAccess == 0) render();
        scan.execute(() -> {
            List<VideoLibrary.Video> found;
            boolean failed = false;
            try { found = VideoLibrary.read(getContentResolver()); }
            catch (Exception e) {
                Log.w("Reelback", "Couldn't read the video gallery", e);
                found = new ArrayList<>();
                failed = true;
            }
            final List<VideoLibrary.Video> result = found;
            final Map<String, VideoLibrary.Album> grouped = VideoLibrary.group(result);
            final boolean error = failed;
            ui.post(() -> {
                if (destroyed || version != scanVersion) return;
                if (access() != allowed) { refresh(); return; }
                VideoLibrary.Video before = current();
                boolean changed = !loaded || loadError != error || lastAccess != allowed || !sameLibrary(library, result);
                loaded = true;
                lastAccess = allowed;
                loadError = error;
                library = result;
                if (changed) {
                    albums.clear();
                    albums.putAll(grouped);
                    if (setupPicker != null) setupPicker.updateAlbums(albums.values());
                    if (editPicker != null) editPicker.updateAlbums(albums.values());
                    if (albumKey != null && !albums.containsKey(albumKey)) {
                        albumKey = null;
                        if (tab == ALBUMS) collectionPlayer = false;
                    }
                    updateQueue(before == null ? (tab == FEED ? feedKey : null) : before.key, position);
                    render();
                }
                if (pane != null && playbackVisible()) pane.foreground(true);
            });
        });
    }

    private static boolean sameLibrary(List<VideoLibrary.Video> a, List<VideoLibrary.Video> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            VideoLibrary.Video x = a.get(i), y = b.get(i);
            if (!x.key.equals(y.key) || !x.title.equals(y.title) || !x.albumKey.equals(y.albumKey)
                    || !x.album.equals(y.album) || x.duration != y.duration || !x.mime.equals(y.mime)
                    || x.width != y.width || x.height != y.height || !x.path.equals(y.path)) return false;
        }
        return true;
    }

    VideoLibrary.Video current() { return at(position); }
    VideoLibrary.Video at(int index) { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    int index() { return position; }
    int total() { return queue.size(); }
    boolean playbackVisible() { return active && !destroyed && pendingTrash == null && albumEditor == null; }
    boolean inCollection() { return tab != FEED; }
    VideoLibrary.Album albumOf(VideoLibrary.Video video) {
        return video == null ? null : albums.get(video.albumKey);
    }

    private void updateQueue(String prefer, int fallback) {
        String keepPlaying = tab == FEED && pane != null && pane.video != null ? pane.video.key : null;
        updateQueue(prefer, fallback, keepPlaying);
    }

    private void updateQueue(String prefer, int fallback, String keepPlaying) {
        List<VideoLibrary.Video> items = new ArrayList<>();
        for (VideoLibrary.Video video : library) {
            boolean include = switch (tab) {
                case FEED -> sources.includes(video) && (video.key.equals(keepPlaying) || !isWatched(video));
                case SAVED -> favorites.contains(video.key);
                case HISTORY -> isWatched(video);
                case ALBUMS -> video.albumKey.equals(albumKey);
                default -> false;
            };
            if (include) items.add(video);
        }
        if (tab == FEED && order == 1) Collections.reverse(items);
        if (tab == FEED && order == 2) items.sort(Comparator.comparingLong(v -> shuffled(v.key)));
        if (tab == HISTORY) items.sort(Comparator.<VideoLibrary.Video>comparingLong(v -> watched.get(v.key)).reversed());
        queue = items;
        position = Math.max(0, Math.min(queue.size() - 1, fallback));
        if (prefer != null) for (int i = 0; i < queue.size(); i++) {
            if (prefer.equals(queue.get(i).key)) { position = i; break; }
        }
    }

    private long shuffled(String key) {
        long x = shuffleSeed + key.hashCode() * 0x9e3779b97f4a7c15L;
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    private int neighborIndex(int direction) {
        int next = position + direction;
        if (next >= 0 && next < queue.size()) return next;
        if (tab == FEED && queue.size() > 1 && isWatched(current())) return direction > 0 ? 0 : queue.size() - 1;
        return -1;
    }

    VideoLibrary.Video neighbor(int direction) { return at(neighborIndex(direction)); }

    boolean canFinishFeed(int direction) {
        return direction > 0 && tab == FEED && queue.size() == 1 && isWatched(current());
    }

    void step(int direction) {
        if (pane != null) pane.sampleWatch();
        if (canFinishFeed(direction)) {
            queue.clear();
            position = 0;
            storeFeed(null, 0);
            render();
            return;
        }
        int next = neighborIndex(direction);
        if (next < 0) return;
        if (tab == FEED && isWatched(current())) {
            queue.remove(position);
            if (next > position) next--;
        }
        position = next;
        if (pane != null) pane.showVideo(current(), 0);
        if (tab == FEED) storeFeed(current().key, 0);
    }

    private void storeFeed(String key, int when) {
        feedKey = key;
        feedPosition = when;
        prefs.edit().putString("feedKey", key).putInt("feedPosition", when).apply();
    }

    private void rememberFeed() {
        if (tab == FEED && pane != null && pane.video != null) {
            pane.sampleWatch();
            if (isWatched(pane.video)) storeFeed(null, 0);
            else storeFeed(pane.video.key, pane.player.position());
        }
    }

    private void switchTab(int next) {
        if (tab == next && !collectionPlayer && (tab != ALBUMS || albumKey == null)) return;
        albumReturn = null;
        rememberFeed();
        removePane();
        if (tab == next && !collectionPlayer) albumKey = null;
        tab = next;
        collectionPlayer = false;
        resetGrid();
        updateQueue(tab == FEED ? feedKey : null, 0);
        render();
    }

    private void openAlbum(VideoLibrary.Album album) {
        albumKey = album.key;
        resetGrid();
        updateQueue(null, 0);
        render();
        animateStage(1);
    }

    void openVideoAlbum(VideoLibrary.Video video) {
        VideoLibrary.Album target = albumOf(video);
        if (target == null || pane == null || pane.video == null) return;
        if (tab == ALBUMS && target.key.equals(albumKey)) {
            goBack();
            return;
        }
        albumReturn = new AlbumReturn(this);
        rememberFeed();
        removePane();
        tab = ALBUMS;
        collectionPlayer = false;
        openAlbum(target);
    }

    private void returnToVideo() {
        AlbumReturn origin = albumReturn;
        albumReturn = null;
        tab = origin.tab;
        albumKey = origin.albumKey;
        collectionPlayer = origin.collectionPlayer;
        gridIndex = origin.gridIndex;
        gridOffset = origin.gridOffset;
        updateQueue(origin.videoKey, origin.queueIndex, origin.tab == FEED ? origin.videoKey : null);
        render(origin);
        animateStage(-1);
    }

    private void animateStage(int direction) {
        View page = stage.getChildAt(0);
        if (page == null || !Motion.enabled()) return;
        page.animate().cancel();
        page.setAlpha(.65f);
        page.setTranslationX(dp(19) * direction);
        page.animate().alpha(1).translationX(0).setDuration(210).setInterpolator(Motion.ENTER).start();
    }

    private void openCollectionVideo(VideoLibrary.Video video) {
        if (grid != null) {
            gridIndex = grid.getFirstVisiblePosition();
            gridOffset = grid.getChildCount() == 0 ? 0 : grid.getChildAt(0).getTop() - grid.getPaddingTop();
        }
        collectionPlayer = true;
        updateQueue(video.key, 0);
        render();
        animateStage(1);
    }

    private void resetGrid() { gridIndex = 0; gridOffset = 0; }

    boolean isFavorite(VideoLibrary.Video video) { return video != null && favorites.contains(video.key); }

    void favorite(VideoLibrary.Video video, boolean toggle) {
        if (video == null) return;
        boolean added = favorites.add(video.key);
        if (!added && !toggle) return;
        if (!added) favorites.remove(video.key);
        prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
        if (pane != null) pane.updateFavorite(true);
        if (tab == SAVED && !added) {
            notifyText("Removed from Favorites");
            updateQueue(null, position);
            if (queue.isEmpty()) collectionPlayer = false;
            render();
        }
    }

    void share(VideoLibrary.Video video) {
        if (video == null) return;
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType(video.mime)
                .putExtra(Intent.EXTRA_STREAM, video.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri(video.title, video.uri));
        try { startActivity(Intent.createChooser(i, "Share video")); }
        catch (Exception e) { notifyText("No app is available to share this video."); }
    }

    void trash(VideoLibrary.Video video) {
        if (video == null || pendingTrash != null) return;
        try {
            PendingIntent approval = MediaStore.createTrashRequest(getContentResolver(), Collections.singletonList(video.uri), true);
            pendingTrash = video.key;
            if (pane != null) pane.foreground(false);
            startIntentSenderForResult(approval.getIntentSender(), TRASH, null, 0, 0, 0);
        } catch (Exception e) {
            pendingTrash = null;
            if (pane != null && active) pane.foreground(true);
            notifyText("Couldn't move this video to Trash.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == TRASH) {
            String key = pendingTrash;
            pendingTrash = null;
            if (result == RESULT_OK) {
                if (key != null && favorites.remove(key)) prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
                notifyText("Moved to Trash");
            }
            refresh();
        }
    }

    private void toggleMute() {
        mute = !mute;
        prefs.edit().putBoolean("mute", mute).apply();
        if (pane != null) pane.player.setMuted(mute);
    }

    void videoOptions(VideoLibrary.Video clip) {
        if (clip == null || pane == null || videoSheet != null || !active) return;
        LinearLayout list = column();
        addSheetItem(list, clearDisplay ? "expand-exit" : "expand",
                clearDisplay ? "Exit Clear display" : "Clear display", null, Color.WHITE,
                () -> setClearDisplay(!clearDisplay));
        addSheetItem(list, "speed", "Playback speed", rateText(playbackRate), Color.WHITE, this::speedOptions);
        addSheetItem(list, mute ? "muted" : "sound", mute ? "Unmute video" : "Mute video", null, Color.WHITE,
                this::toggleMute);
        addSheetItem(list, isFavorite(clip) ? "heart-filled" : "heart",
                isFavorite(clip) ? "Remove from Favorites" : "Add to Favorites", null, Color.WHITE,
                () -> favorite(clip, true));
        boolean watchedAlready = isWatched(clip);
        addSheetItem(list, "history", watchedAlready ? "Mark as unwatched" : "Mark as watched",
                null, Color.WHITE, () -> setWatchedFromMenu(clip, !watchedAlready));
        addSheetItem(list, "details", "Video details", null, Color.WHITE, () -> videoDetails(clip));
        addSheetItem(list, "forward", "Share video", null, Color.WHITE, () -> share(clip));
        addSheetItem(list, "trash", "Move to Trash", null, 0xffff637e, () -> trash(clip));
        addSheetItem(list, "sliders", "Settings", null, Color.WHITE, this::settings);
        showVideoSheet("Video options", list);
    }

    private void setWatchedFromMenu(VideoLibrary.Video clip, boolean mark) {
        if (pane == null || pane.video == null || !pane.video.key.equals(clip.key)) return;
        if (mark) {
            recordWatch(clip);
            if (tab == FEED) step(1);
            notifyText("Moved to History");
            return;
        }

        pane.stopTrackingCurrent();
        watched.remove(clip.key);
        unfinished.remove(clip.key);
        historyWorker.execute(() -> {
            try {
                historyDb.forget(clip.key);
            } catch (RuntimeException e) {
                Log.e("Reelback", "Couldn't update watch history", e);
            }
        });
        if (tab == HISTORY) {
            collectionPlayer = false;
            removePane();
            updateQueue(null, 0);
            render();
            animateStage(-1);
        } else {
            pane.refreshNeighbors();
        }
        notifyText(sources.includes(clip) ? "Available in Home again" : "Marked as unwatched");
    }

    private void videoDetails(VideoLibrary.Video clip) {
        LinearLayout list = column();
        list.setPadding(dp(14), dp(12), dp(14), dp(6));
        TextView title = text(clip.title, 18, PAPER, "sans-serif-medium");
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        list.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        list.addView(settingsRow("Album", clip.album));
        list.addView(settingsRow("Duration", time(clip.duration)));
        if (clip.width > 0 && clip.height > 0) {
            String shape = clip.portrait() ? "Portrait" : clip.landscape() ? "Landscape" : "Square";
            list.addView(settingsRow("Frame", clip.width + " × " + clip.height + "  ·  " + shape));
        }
        if (!clip.path.isEmpty()) list.addView(settingsRow("Folder", clip.path));
        showVideoSheet("Video details", list);
    }

    private void speedOptions() {
        LinearLayout list = column();
        for (float rate : new float[]{.5f, .75f, 1f, 1.25f, 1.5f, 2f}) {
            boolean selected = rate == playbackRate;
            addSheetItem(list, selected ? "check" : "", rateText(rate) + (rate == 1f ? "  Normal" : ""),
                    selected ? "Selected" : null, Color.WHITE, () -> {
                        playbackRate = rate;
                        prefs.edit().putFloat("playbackRate", rate).apply();
                        if (pane != null) pane.player.setPlaybackRate(rate);
                    });
        }
        showVideoSheet("Playback speed", list);
    }

    private static String rateText(float rate) {
        return (rate == (int) rate ? String.valueOf((int) rate) : String.valueOf(rate)) + "×";
    }

    private void addSheetItem(LinearLayout list, String icon, String label, String detail, int color, Runnable action) {
        LinearLayout item = row();
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(7), dp(12), dp(7));
        item.setMinimumHeight(dp(48));
        item.addView(new Glyph(this, icon, color, 21), new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = text(label, 15, color, "sans-serif-medium");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        p.leftMargin = dp(11);
        item.addView(title, p);
        if (detail != null) item.addView(text(detail, 13, 0xffb5b5b5, "sans-serif"));
        item.setContentDescription(detail == null ? label : label + ", " + detail);
        click(item, Color.TRANSPARENT, 5, () -> {
            Dialog old = videoSheet;
            videoSheet = null;
            if (old != null) old.dismiss();
            action.run();
        });
        list.addView(item, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void showVideoSheet(String title, LinearLayout list) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        videoSheet = dialog;
        LinearLayout panel = column();
        int color = 0xff191919;
        GradientDrawable background = round(color, 0);
        float corner = dp(16);
        background.setCornerRadii(new float[]{corner,corner,corner,corner,0,0,0,0});
        panel.setBackground(background);
        panel.setPadding(dp(8), dp(8), dp(8), dp(10));
        View handle = new View(this);
        handle.setBackground(round(0xff555555, 3));
        LinearLayout.LayoutParams grip = new LinearLayout.LayoutParams(dp(31), dp(3));
        grip.gravity = Gravity.CENTER_HORIZONTAL;
        grip.bottomMargin = dp(3);
        panel.addView(handle, grip);
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(13), 0, dp(1), 0);
        TextView name = text(title, 19, Color.WHITE, "sans-serif-medium");
        name.setAccessibilityHeading(true);
        header.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        header.addView(iconButton("close", "Close menu", dialog::dismiss), new LinearLayout.LayoutParams(dp(48), dp(44)));
        panel.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));
        View line = new View(this);
        line.setBackgroundColor(0xff303030);
        panel.addView(line, new LinearLayout.LayoutParams(MATCH, dp(1)));
        panel.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(panel, new ScrollView.LayoutParams(MATCH, WRAP));
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(d -> {
            if (videoSheet == dialog) videoSheet = null;
        });
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            w.setNavigationBarColor(color);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(.46f);
            if (Motion.enabled()) w.setWindowAnimations(R.style.SheetMotion);
            int limit = Math.max(dp(100), (int) (shell.getHeight() * .88f));
            int wpx = Math.min(dp(540), shell.getWidth());
            panel.measure(View.MeasureSpec.makeMeasureSpec(wpx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            w.setLayout(wpx, Math.min(panel.getMeasuredHeight(), limit));
        }
        dialog.show();
    }

    void prefetch(VideoLibrary.Video video) { thumbs.request(video, null); }
    void openFile(VideoLibrary.Video video, Consumer<AssetFileDescriptor> result) {
        fileWorker.execute(() -> {
            AssetFileDescriptor opened = null;
            try {
                opened = getContentResolver().openAssetFileDescriptor(video.uri, "r");
            } catch (IOException | SecurityException e) {
                Log.w("Reelback", "Couldn't open a video", e);
            }
            AssetFileDescriptor descriptor = opened;
            ui.post(() -> {
                try (AssetFileDescriptor file = descriptor) {
                    if (!destroyed) result.accept(file);
                } catch (IOException e) {
                    Log.w("Reelback", "Couldn't close a video descriptor", e);
                }
            });
        });
    }
    void thumbnail(VideoLibrary.Video video, java.util.function.Consumer<android.graphics.Bitmap> display) {
        thumbs.request(video, display);
    }
    android.graphics.Bitmap cached(VideoLibrary.Video video) { return thumbs.peek(video); }

    private void removePane() {
        if (pane == null) return;
        pane.dispose();
        pane = null;
        clearDisplay = false;
    }

    private void render() { render(null); }

    private void render(AlbumReturn resume) {
        int access = access();
        if (access != 0 && loaded && historyReady && !loadError && !sources.configured()) {
            if (setupPicker == null) {
                stage.removeAllViews();
                setupPicker = new AlbumPicker(this, sources, albums.values(), null, this::saveSources);
                stage.addView(setupPicker, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            renderNav(false);
            syncSystemBars();
            return;
        }
        setupPicker = null;
        boolean hasPlayer = access != 0 && loaded && historyReady && !loadError
                && !queue.isEmpty() && (tab == FEED || collectionPlayer);
        if (hasPlayer) {
            if (pane == null) {
                stage.removeAllViews();
                grid = null;
                pane = new FeedPane(this);
                stage.addView(pane, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            VideoLibrary.Video video = current();
            if (pane.video == null || !pane.video.key.equals(video.key)) {
                boolean returning = resume != null && video.key.equals(resume.videoKey);
                int startAt = returning ? resume.position : tab == FEED && video.key.equals(feedKey) ? feedPosition : 0;
                pane.showVideo(video, startAt, returning ? resume.frame : null);
                if (returning && !resume.playing) pane.player.pause();
                if (tab == FEED && !video.key.equals(feedKey)) storeFeed(video.key, 0);
            } else {
                pane.video = video;
                pane.refreshNeighbors();
            }
            pane.foreground(playbackVisible());
        } else {
            if (collectionPlayer && loaded && queue.isEmpty()) collectionPlayer = false;
            removePane();
            stage.removeAllViews();
            grid = null;
            if (access == 0) stage.addView(welcome(), new FrameLayout.LayoutParams(MATCH, MATCH));
            else if (!loaded || !historyReady) stage.addView(empty("feed", "Finding your videos", "Looking for videos in your gallery.", null, null));
            else if (loadError) stage.addView(empty("feed", "Gallery unavailable", "Reelback couldn’t read your gallery. Try refreshing.", "Try again", this::refresh));
            else if (tab == FEED) stage.addView(homeEmpty());
            else stage.addView(collection(), new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        renderNav(access != 0 && sources.configured() && !clearDisplay);
        syncSystemBars();
    }

    private View homeEmpty() {
        if (library.isEmpty()) return empty("feed", "No videos yet",
                access() == 1 ? "Allow more videos to see them here." : "Videos you record or download can appear here.",
                access() == 1 ? "Manage video access" : "Refresh", access() == 1 ? this::requestAccess : this::refresh);
        for (VideoLibrary.Video video : library) {
            if (sources.includes(video)) return empty("history", "You’re caught up",
                    "Find the videos you watched in History, or pick more albums in Settings.",
                    "View History", () -> switchTab(HISTORY));
        }
        return empty("albums", "Pick albums for Home", "Choose which folders to include in your feed.",
                "Choose albums", this::openAlbumEditor);
    }

    private void saveSources(Map<String, Integer> choices) {
        sources.save(choices);
        VideoLibrary.Video before = current();
        updateQueue(before == null ? feedKey : before.key, position);
        if (tab == FEED && current() == null) storeFeed(null, 0);
        render();
    }

    private void openAlbumEditor() {
        if (albumEditor != null || !loaded || loadError || access() == 0) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        albumEditor = dialog;
        if (pane != null) pane.foreground(false);
        editPicker = new AlbumPicker(this, sources, albums.values(), dialog::dismiss, choices -> {
            saveSources(choices);
            dialog.dismiss();
        });
        editPicker.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        dialog.setContentView(editPicker);
        dialog.setOnDismissListener(d -> {
            albumEditor = null;
            editPicker = null;
            if (!destroyed && pane != null) pane.foreground(playbackVisible());
        });
        Window w = dialog.getWindow();
        if (w != null) {
            w.setDecorFitsSystemWindows(false);
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(BG);
            w.setNavigationBarContrastEnforced(false);
            w.setLayout(MATCH, MATCH);
        }
        dialog.show();
        if (w != null) w.setLayout(MATCH, MATCH);
        editPicker.requestApplyInsets();
    }

    private void renderNav(boolean visible) {
        nav.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        nav.removeAllViews();
        String[] names = {"Home", "Favorites", "History", "Albums"};
        String[] icons = {"home", "bookmark", "history", "albums"};
        for (int i = 0; i < names.length; i++) {
            final int target = i;
            boolean selected = i == tab;
            LinearLayout button = column();
            button.setGravity(Gravity.CENTER);
            int color = selected ? Color.WHITE : 0xffa0a0a0;
            String icon = selected && i < 2 ? icons[i] + "-filled" : icons[i];
            Glyph glyph = new Glyph(this, icon, color, 22);
            button.addView(glyph, new LinearLayout.LayoutParams(dp(29), dp(26)));
            TextView label = text(names[i], 10, color, selected ? "sans-serif-medium" : "sans-serif");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.topMargin = dp(3);
            button.addView(label, lp);
            button.setSelected(selected);
            button.setContentDescription(names[i] + (selected ? ", selected" : ""));
            click(button, Color.TRANSPARENT, 0, () -> switchTab(target));
            Motion.press(button, glyph);
            nav.addView(button, new LinearLayout.LayoutParams(0, MATCH, 1f));
        }
    }

    void setClearDisplay(boolean clear) {
        if (pane == null || clearDisplay == clear) return;
        clearDisplay = clear;
        pane.applyClearDisplay();
        renderNav(!clear && access() != 0);
        syncSystemBars();
    }

    private void syncSystemBars() {
        shell.setBackgroundColor(pane == null && access() == 0 ? BG : Color.BLACK);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (clearDisplay) controller.hide(WindowInsets.Type.systemBars());
            else controller.show(WindowInsets.Type.systemBars());
        }
        shell.requestApplyInsets();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && clearDisplay && shell != null) syncSystemBars();
    }

    private View collection() {
        LinearLayout root = column();
        root.setBackgroundColor(0xff101010);
        boolean albumList = tab == ALBUMS && albumKey == null;
        VideoLibrary.Album profile = tab == ALBUMS ? albums.get(albumKey) : null;
        String title = tab == SAVED ? "Favorites" : tab == HISTORY ? "History" : profile != null ? profile.title : "Albums";
        int count = albumList ? albums.size() : queue.size();
        String countText = count + (albumList ? (count == 1 ? " album" : " albums") : (count == 1 ? " video" : " videos"));
        LinearLayout head = column();
        head.setPadding(dp(16), dp(4), dp(16), dp(profile != null ? 18 : 15));
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, profile != null ? 24 : 28, Color.WHITE, "sans-serif-medium");
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setAccessibilityHeading(true);
        if (profile != null) {
            line.addView(iconButton("left", albumReturn == null ? "Back to albums" : "Back to video", this::goBack),
                    new LinearLayout.LayoutParams(dp(48), dp(48)));
            line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        } else {
            line.addView(heading, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        if (albumList) line.addView(iconButton("filter", "Choose feed albums", this::openAlbumEditor),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        line.addView(iconButton("sliders", "Settings", this::settings), new LinearLayout.LayoutParams(dp(48), dp(48)));
        head.addView(line, new LinearLayout.LayoutParams(MATCH, WRAP));
        if (profile != null) {
            AlbumAvatar cover = new AlbumAvatar(this, 78, false);
            cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            cover.bind(profile);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(78), dp(78));
            cp.gravity = Gravity.CENTER_HORIZONTAL;
            head.addView(cover, cp);
            heading.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(MATCH, WRAP);
            hp.topMargin = dp(9);
            head.addView(heading, hp);
        }
        TextView summary = text(countText, 13, 0xffb5b5b5, "sans-serif");
        if (profile != null) summary.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.topMargin = dp(profile != null ? 6 : 0);
        head.addView(summary, sp);
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        if (count == 0) {
            String emptyIcon = albumList ? "albums" : tab == HISTORY ? "history" : "heart";
            String emptyTitle = albumList ? "No albums yet" : tab == HISTORY ? "Nothing watched yet" : "Keep the good ones";
            String emptyBody = albumList ? "Gallery folders with videos will appear here." : tab == HISTORY
                    ? "A video appears here after 80% of it plays."
                    : "Tap the heart on a video to find it here anytime.";
            root.addView(empty(emptyIcon, emptyTitle, emptyBody,
                    "Go to Feed", () -> switchTab(FEED)), new LinearLayout.LayoutParams(MATCH, 0, 1f));
            return root;
        }
        List<VideoLibrary.Album> allAlbums = new ArrayList<>(albums.values());
        List<VideoLibrary.Video> clips = new ArrayList<>(queue);
        grid = new GridView(this);
        grid.setPadding(dp(albumList ? 12 : 2), 0, dp(albumList ? 12 : 2), dp(10));
        grid.setClipToPadding(false);
        grid.setHorizontalSpacing(dp(albumList ? 8 : 3));
        grid.setVerticalSpacing(dp(albumList ? 8 : 3));
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(albumList ? 148 : 106));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return albumList ? allAlbums.size() : clips.size(); }
            @Override public Object getItem(int p) { return albumList ? allAlbums.get(p) : clips.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View recycled, ViewGroup parent) {
                Tile tile = recycled instanceof Tile ? (Tile) recycled : new Tile(albumList);
                VideoLibrary.Album album = albumList ? allAlbums.get(p) : null;
                VideoLibrary.Video clip = albumList ? album.cover : clips.get(p);
                tile.bind(clip, album);
                return tile;
            }
        });
        grid.setOnItemClickListener((parent, tile, p, id) -> {
            if (albumList) openAlbum(allAlbums.get(p));
            else openCollectionVideo(clips.get(p));
        });
        if (gridIndex > 0 || gridOffset != 0) grid.setSelectionFromTop(gridIndex, gridOffset);
        root.addView(grid, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    private final class Tile extends FrameLayout {
        final ImageView art;
        final TextView title, badge;
        final boolean albumTile;
        Tile(boolean albumTile) {
            super(MainActivity.this);
            this.albumTile = albumTile;
            setBackground(round(0xff242424, albumTile ? 12 : 4));
            setClipToOutline(true);
            art = new ImageView(MainActivity.this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(art, place(MATCH, MATCH, Gravity.FILL));
            View gradient = new View(MainActivity.this);
            gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xe9000000, 0x68000000, 0x00000000}));
            addView(gradient, place(MATCH, dp(72), Gravity.BOTTOM));
            badge = text("", 11, PAPER, "sans-serif-medium");
            badge.setTextColor(0xffdcdcdc);
            badge.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
            FrameLayout.LayoutParams bp = place(WRAP, WRAP, Gravity.BOTTOM | Gravity.START);
            bp.setMarginStart(dp(8));
            bp.bottomMargin = dp(8);
            addView(badge, bp);
            title = text("", albumTile ? 15 : 12, PAPER, "sans-serif-medium");
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams tp = place(MATCH, WRAP, Gravity.BOTTOM);
            tp.setMargins(dp(8), 0, dp(8), dp(24));
            addView(title, tp);
        }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            if (width == 0 && grid != null) width = grid.getColumnWidth();
            if (width == 0) width = dp(105);
            int height = Math.round(width * (albumTile ? 1.04f : 1.44f));
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        void bind(VideoLibrary.Video video, VideoLibrary.Album album) {
            setTag(video.key);
            art.setImageBitmap(thumbs.peek(video));
            title.setText(album == null ? video.title : album.title);
            badge.setText(album == null ? time(video.duration) : album.count + (album.count == 1 ? " video" : " videos"));
            setContentDescription(title.getText() + ", " + badge.getText());
            thumbs.request(video, image -> {
                if (video.key.equals(getTag())) art.setImageBitmap(image);
            });
        }
    }

    private View welcome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout center = column();
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(24), dp(12), dp(24), dp(22));
        LinearLayout content = column();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        center.addView(content, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout brand = row();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(new Glyph(this, "logo", VIOLET, 24), new LinearLayout.LayoutParams(dp(32), dp(36)));
        TextView wordmark = text("reelback.", 25, PAPER, "sans-serif-black");
        brand.addView(wordmark, new LinearLayout.LayoutParams(WRAP, WRAP));
        content.addView(brand, new LinearLayout.LayoutParams(WRAP, WRAP));
        View art = new WelcomeArt(this);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(art, new LinearLayout.LayoutParams(MATCH, dp(218)));
        TextView kicker = text("A SECOND LOOK", 11, VIOLET, "sans-serif-medium");
        kicker.setLetterSpacing(.19f);
        content.addView(kicker, new LinearLayout.LayoutParams(WRAP, WRAP));
        TextView title = text("The videos you\nmeant to rewatch.", 37, PAPER, "sans-serif-medium");
        title.setGravity(Gravity.CENTER);
        title.setAccessibilityHeading(true);
        title.setLineSpacing(dp(1), 1);
        gapAdd(content, title, 12);
        TextView subtitle = text("Swipe through the videos already on your phone, and keep your favorites close.", 16, SUB, "sans-serif");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxWidth(dp(345));
        subtitle.setLineSpacing(dp(4), 1);
        gapAdd(content, subtitle, 14);
        TextView button = pillButton("Open my gallery", this::requestAccess);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(MATCH, dp(54));
        bp.topMargin = dp(29);
        LinearLayout buttonWrap = column();
        buttonWrap.setGravity(Gravity.CENTER);
        buttonWrap.addView(button, bp);
        content.addView(buttonWrap, new LinearLayout.LayoutParams(Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(48)), WRAP));
        LinearLayout privacy = row();
        privacy.setGravity(Gravity.CENTER_VERTICAL);
        privacy.addView(new Glyph(this, "lock", MINT, 15), new LinearLayout.LayoutParams(dp(22), dp(22)));
        privacy.addView(text("On this device  ·  No account needed", 12, SUB, "sans-serif"));
        gapAdd(content, privacy, 12);
        scroll.addView(center, new ScrollView.LayoutParams(MATCH, MATCH));
        return scroll;
    }

    private View empty(String icon, String title, String body, String action, Runnable handler) {
        LinearLayout column = column();
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(30), dp(20), dp(30), dp(20));
        FrameLayout mark = new FrameLayout(this);
        mark.setBackground(round(PANEL, 22));
        mark.addView(new Glyph(this, icon, VIOLET, 29), place(MATCH, MATCH, Gravity.CENTER));
        column.addView(mark, new LinearLayout.LayoutParams(dp(65), dp(65)));
        TextView head = text(title, 26, PAPER, "sans-serif-medium");
        head.setGravity(Gravity.CENTER);
        gapAdd(column, head, 16);
        TextView subtitle = text(body, 15, SUB, "sans-serif");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(3), 1);
        subtitle.setMaxWidth(dp(330));
        gapAdd(column, subtitle, 9);
        if (action != null) gapAdd(column, pillButton(action, handler), 19);
        return column;
    }

    void settings() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FeedPane source = pane;
        VideoLibrary.Video before = source == null ? null : source.video;
        boolean resume = source != null && source.player.intendsToPlay();
        if (source != null) source.player.pause();
        LinearLayout panel = column();
        panel.setBackground(stroke(PANEL, 25, EDGE));
        panel.setPadding(dp(18), dp(13), dp(18), dp(20));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("Settings", 25, PAPER, "sans-serif-medium");
        heading.setAccessibilityHeading(true);
        top.addView(heading, new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(iconButton("close", "Close settings", dialog::dismiss), new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(top);
        LinearLayout albumChoice = settingsRow("Feed albums", "Choose folders and video orientations for Home");
        albumChoice.addView(new Glyph(this, "right", SUB, 20), new LinearLayout.LayoutParams(dp(36), dp(44)));
        click(albumChoice, Color.TRANSPARENT, 12, () -> {
            dialog.dismiss();
            openAlbumEditor();
        });
        gapAddFull(panel, albumChoice, 2);
        gapAdd(panel, sectionLabel("FEED ORDER"), 8);
        LinearLayout choices = row();
        String[] names = {"Newest", "Oldest", "Shuffle"};
        TextView[] option = new TextView[3];
        for (int j = 0; j < names.length; j++) {
            final int which = j;
            TextView b = text(names[j], 14, order == j ? INK : PAPER, "sans-serif-medium");
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            click(b, order == j ? VIOLET : EDGE, 13, () -> {
                changeOrder(which);
                for (int k = 0; k < option.length; k++) {
                    option[k].setSelected(k == order);
                    option[k].setTextColor(k == order ? INK : PAPER);
                    option[k].setBackground(ripple(k == order ? VIOLET : EDGE, 13));
                }
            });
            b.setSelected(order == j);
            option[j] = b;
            LinearLayout.LayoutParams choiceLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            choiceLp.leftMargin = dp(j == 0 ? 0 : 6);
            choices.addView(b, choiceLp);
        }
        gapAddFull(panel, choices, 8);
        gapAddFull(panel, switchRow("Fit every video", "Show the full frame for portrait clips too", fit, value -> {
            fit = value;
            prefs.edit().putBoolean("fit", fit).apply();
            if (pane != null) pane.applyFit();
        }), 10);
        gapAddFull(panel, switchRow("Swipe left to trash", "Android asks you before moving a video", swipeTrash, value -> {
            swipeTrash = value;
            prefs.edit().putBoolean("swipeTrash", value).apply();
        }), 3);
        String desc = access() == 1 ? "Only your selected videos are visible" : "Control which videos Reelback can see";
        LinearLayout permission = settingsRow("Manage video access", desc);
        permission.addView(new Glyph(this, "right", SUB, 20), new LinearLayout.LayoutParams(dp(36), dp(44)));
        click(permission, Color.TRANSPARENT, 12, () -> {
            dialog.dismiss();
            if (access() == 1 || access() == 0) requestAccess();
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        });
        gapAddFull(panel, permission, 3);
        TextView note = text("Tap to pause and seek. Hold the center for video options, or hold either side while playing for 2×.", 12, SUB, "sans-serif");
        note.setLineSpacing(dp(4), 1);
        gapAdd(panel, note, 10);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(panel, new ScrollView.LayoutParams(MATCH, WRAP));
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(d -> {
            if (resume && source != null && source == pane && source.video != null && before != null
                    && source.video.key.equals(before.key) && active) source.player.play();
        });
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(.66f);
            w.setGravity(Gravity.CENTER);
            int height = Math.min(dp(530), (int) (getWindow().getDecorView().getHeight() * .88));
            scroll.setFillViewport(false);
            w.setLayout(Math.min(dp(460), getResources().getDisplayMetrics().widthPixels - dp(28)),
                    height < dp(515) ? height : WRAP);
        }
    }

    private void changeOrder(int value) {
        order = value;
        if (order == 2) shuffleSeed = System.nanoTime();
        prefs.edit().putInt("order", order).putLong("shuffleSeed", shuffleSeed).apply();
        if (tab == FEED) {
            VideoLibrary.Video before = current();
            updateQueue(before == null ? null : before.key, position);
            if (pane != null) pane.refreshNeighbors();
        }
    }

    private LinearLayout settingsRow(String title, String body) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(58));
        LinearLayout labels = column();
        labels.addView(text(title, 15, PAPER, "sans-serif-medium"));
        TextView detail = text(body, 12, SUB, "sans-serif");
        detail.setPadding(0, dp(4), 0, 0);
        labels.addView(detail);
        r.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return r;
    }

    private LinearLayout switchRow(String title, String desc, boolean checked, java.util.function.Consumer<Boolean> action) {
        LinearLayout row = settingsRow(title, desc);
        Switch sw = new Switch(this);
        sw.setContentDescription(title);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((button, value) -> action.accept(value));
        row.addView(sw, new LinearLayout.LayoutParams(WRAP, dp(48)));
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        return row;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 11, VIOLET, "sans-serif-medium");
        t.setLetterSpacing(.14f);
        return t;
    }

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    TextView text(String value, float size, int color, String family) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }
    LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }
    FrameLayout.LayoutParams place(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(width, height, gravity);
    }
    GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radius));
        d.setColor(color);
        return d;
    }
    GradientDrawable stroke(int color, int radius, int edge) {
        GradientDrawable d = round(color, radius);
        d.setStroke(dp(1), edge);
        return d;
    }
    RippleDrawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x32ffffff), round(color, radius), round(Color.WHITE, radius));
    }
    void click(View target, int color, int radius, Runnable action) {
        target.setBackground(ripple(color, radius));
        target.setOnClickListener(v -> action.run());
        target.setFocusable(true);
    }
    FrameLayout iconButton(String icon, String label, Runnable action) {
        FrameLayout button = new FrameLayout(this);
        button.addView(new Glyph(this, icon, PAPER, 20), place(MATCH, MATCH, Gravity.CENTER));
        button.setContentDescription(label);
        click(button, Color.TRANSPARENT, 0, action);
        return button;
    }
    TextView pillButton(String label, Runnable action) {
        TextView t = text(label, 15, INK, "sans-serif-medium");
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(21), dp(13), dp(21), dp(13));
        t.setMinHeight(dp(48));
        click(t, VIOLET, 15, action);
        return t;
    }
    private void gapAdd(LinearLayout parent, View view, int gap) { gapAdd(parent, view, gap, WRAP); }
    private void gapAddFull(LinearLayout parent, View view, int gap) { gapAdd(parent, view, gap, MATCH); }
    private void gapAdd(LinearLayout parent, View view, int gap, int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, WRAP);
        lp.topMargin = dp(gap);
        parent.addView(view, lp);
    }
    void notifyText(String message) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }
    static String time(long milliseconds) {
        long s = Math.max(0, milliseconds) / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
