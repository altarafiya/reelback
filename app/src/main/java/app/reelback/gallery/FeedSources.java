package app.reelback.gallery;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class FeedSources {
    static final int OFF = 0, ALL = 1, PORTRAIT = 2;
    private static final String SET_UP = "feedAlbumsConfigured";
    private static final String ALL_KEYS = "feedAllAlbums";
    private static final String PORTRAIT_KEYS = "feedPortraitAlbums";
    private static final Pattern APP_FOLDER = Pattern.compile(
            "(tiktok|musically|ssandroidugctrill|aweme|instagram|facebook|snaptube|vidmate|tubemate|"
                    + "newpipe|ytdlnis|youtube.*download|seal(?:downloads?|videos?)?|reels?|fbvideos?)");

    private final SharedPreferences prefs;
    private final Map<String, Integer> modes = new HashMap<>();
    private boolean configured;

    FeedSources(SharedPreferences prefs) {
        this.prefs = prefs;
        configured = prefs.getBoolean(SET_UP, false);
        for (String key : prefs.getStringSet(ALL_KEYS, java.util.Collections.emptySet())) modes.put(key, ALL);
        for (String key : prefs.getStringSet(PORTRAIT_KEYS, java.util.Collections.emptySet())) modes.put(key, PORTRAIT);
    }

    boolean configured() { return configured; }

    Map<String, Integer> draft() { return new HashMap<>(modes); }

    boolean includes(VideoLibrary.Video clip) {
        int mode = modes.getOrDefault(clip.albumKey, OFF);
        return mode == ALL || mode == PORTRAIT && clip.portrait();
    }

    void save(Map<String, Integer> choices) {
        Set<String> all = new HashSet<>(), portrait = new HashSet<>();
        modes.clear();
        for (Map.Entry<String, Integer> entry : choices.entrySet()) {
            if (entry.getValue() == ALL) all.add(entry.getKey());
            else if (entry.getValue() == PORTRAIT) portrait.add(entry.getKey());
            else continue;
            modes.put(entry.getKey(), entry.getValue());
        }
        configured = true;
        prefs.edit().putBoolean(SET_UP, true).putStringSet(ALL_KEYS, all)
                .putStringSet(PORTRAIT_KEYS, portrait).apply();
    }

    static int suggestion(String title, String path) {
        String folder = clean(title);
        if (folder.equals("camera") || folder.equals("dcim")) return PORTRAIT;
        if (APP_FOLDER.matcher(folder).find()) return ALL;
        for (String part : path.split("/")) {
            if (APP_FOLDER.matcher(clean(part)).find()) return ALL;
        }
        return OFF;
    }

    private static String clean(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
