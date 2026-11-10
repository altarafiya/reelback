package app.reelback.gallery;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

final class WatchHistory extends SQLiteOpenHelper {
    WatchHistory(Context context) {
        super(context.getApplicationContext(), "reelback_history.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE seen (video_key TEXT PRIMARY KEY, watched_at INTEGER NOT NULL) WITHOUT ROWID");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    Map<String, Long> read() {
        Map<String, Long> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().query("seen", new String[]{"video_key", "watched_at"},
                null, null, null, null, null)) {
            while (c.moveToNext()) out.put(c.getString(0), c.getLong(1));
        }
        return out;
    }

    void record(String key, long now) {
        ContentValues values = new ContentValues(2);
        values.put("video_key", key);
        values.put("watched_at", now);
        getWritableDatabase().insertWithOnConflict("seen", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    void forget(String key) {
        getWritableDatabase().delete("seen", "video_key = ?", new String[]{key});
    }
}
