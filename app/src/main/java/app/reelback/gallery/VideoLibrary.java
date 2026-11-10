package app.reelback.gallery;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class VideoLibrary {
    static final Uri VIDEOS = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);

    static final class Video {
        final Uri uri;
        final String key, title, album, albumKey, mime, path;
        final long duration, date;
        final int width, height;
        Video(Uri uri, String title, String album, String albumKey, String mime, String path,
              long duration, long date, int width, int height, int rotation) {
            this.uri = uri;
            this.key = uri.toString() + "@" + date;
            this.title = title;
            this.album = album;
            this.albumKey = albumKey;
            this.mime = mime;
            this.path = path;
            this.duration = duration;
            this.date = date;
            boolean rotated = Math.floorMod(rotation, 180) == 90;
            this.width = rotated ? height : width;
            this.height = rotated ? width : height;
        }
        boolean portrait() { return width > 0 && height > width; }
        boolean landscape() { return height > 0 && width > height; }
    }

    static final class Album {
        final String title, key;
        final Video cover;
        final int suggestedMode;
        int count, portraits;
        Album(Video video) {
            title = video.album;
            key = video.albumKey;
            cover = video;
            suggestedMode = FeedSources.suggestion(video.album, video.path);
        }
        void add(Video video) {
            count++;
            if (video.portrait()) portraits++;
        }
    }

    static Map<String, Album> group(List<Video> videos) {
        Map<String, Album> out = new LinkedHashMap<>();
        for (Video video : videos) out.computeIfAbsent(video.albumKey, k -> new Album(video)).add(video);
        return out;
    }

    static List<Video> read(ContentResolver resolver) {
        final String[] columns = {"_id", "_display_name", "bucket_display_name", "bucket_id", "mime_type",
                "duration", "date_added", "volume_name", "relative_path", "width", "height", "orientation"};
        List<Video> out = new ArrayList<>();
        try (Cursor c = resolver.query(VIDEOS, columns, null, null, "date_added DESC, _id DESC")) {
            if (c == null) throw new IllegalStateException("Gallery unavailable");
            while (c.moveToNext()) {
                String volume = or(c.getString(7), MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri(volume), c.getLong(0));
                String title = or(c.getString(1), "Untitled video");
                int ext = title.lastIndexOf('.');
                if (ext > 0 && title.length() - ext <= 6) title = title.substring(0, ext);
                String album = c.getString(2);
                String path = or(c.getString(8), "");
                if (album == null || album.trim().isEmpty()) {
                    String[] parts = path.split("/");
                    album = parts.length > 0 ? or(parts[parts.length - 1], "Gallery") : "Gallery";
                }
                out.add(new Video(uri, title, album, volume + ":" + or(c.getString(3), path),
                        or(c.getString(4), "video/*"), path, c.getLong(5), c.getLong(6),
                        c.getInt(9), c.getInt(10), c.getInt(11)));
            }
        }
        return out;
    }

    private static String or(String text, String fallback) {
        return text == null || text.isEmpty() ? fallback : text;
    }

    static final class Thumbnails {
        private final ContentResolver resolver;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ExecutorService workers = Executors.newFixedThreadPool(2);
        private final Map<String, List<Consumer<Bitmap>>> requests = new HashMap<>();
        private final LruCache<String, Bitmap> cache = new LruCache<>(20 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
        };
        private final LruCache<String, Boolean> failures = new LruCache<>(64);
        private boolean closed;

        Thumbnails(ContentResolver resolver) { this.resolver = resolver; }
        Bitmap peek(Video video) { return video == null ? null : cache.get(video.key); }

        void request(Video video, Consumer<Bitmap> callback) {
            if (closed || video == null) return;
            Bitmap cached = cache.get(video.key);
            if (cached != null || failures.get(video.key) != null) {
                if (callback != null) callback.accept(cached);
                return;
            }
            List<Consumer<Bitmap>> pending = requests.get(video.key);
            if (pending != null) {
                if (callback != null) pending.add(callback);
                return;
            }
            List<Consumer<Bitmap>> waiters = new ArrayList<>();
            if (callback != null) waiters.add(callback);
            requests.put(video.key, waiters);
            workers.execute(() -> {
                Bitmap bitmap = null;
                try { bitmap = resolver.loadThumbnail(video.uri, new Size(260, 420), null); }
                catch (Exception ignored) { }
                Bitmap result = bitmap;
                main.post(() -> {
                    List<Consumer<Bitmap>> listeners = requests.remove(video.key);
                    if (closed || listeners == null) return;
                    if (result != null) cache.put(video.key, result);
                    else failures.put(video.key, true);
                    for (Consumer<Bitmap> listener : listeners) listener.accept(result);
                });
            });
        }

        void trim() { cache.evictAll(); }
        void invalidateFailures() { failures.evictAll(); }
        void close() {
            closed = true;
            workers.shutdownNow();
            requests.clear();
            trim();
        }
    }

    private VideoLibrary() { }
}
