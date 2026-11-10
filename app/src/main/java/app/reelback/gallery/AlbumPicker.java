package app.reelback.gallery;

import static app.reelback.gallery.MainActivity.*;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class AlbumPicker extends LinearLayout {
    private final MainActivity app;
    private final boolean setup;
    private final Map<String, Integer> choices;
    private final List<VideoLibrary.Album> suggested = new ArrayList<>(), others = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();
    private final TextView count, done;
    private final Rows adapter = new Rows();
    private boolean expanded;

    AlbumPicker(MainActivity app, FeedSources sources, Collection<VideoLibrary.Album> albums,
                Runnable cancel, Consumer<Map<String, Integer>> save) {
        super(app);
        this.app = app;
        setup = !sources.configured();
        choices = sources.draft();
        setOrientation(VERTICAL);
        setBackgroundColor(BG);

        LinearLayout head = app.column();
        head.setPadding(app.dp(22), app.dp(10), app.dp(22), app.dp(9));
        LinearLayout top = app.row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = app.text("HOME FEED", 11, VIOLET, "sans-serif-medium");
        label.setLetterSpacing(.17f);
        top.addView(label, new LayoutParams(0, WRAP, 1f));
        if (cancel != null) top.addView(app.iconButton("close", "Cancel album changes", cancel),
                new LayoutParams(app.dp(46), app.dp(40)));
        else top.setMinimumHeight(app.dp(30));
        head.addView(top, new LayoutParams(MATCH, WRAP));
        TextView title = app.text("Choose your albums", 32, PAPER, "sans-serif-medium");
        title.setAccessibilityHeading(true);
        head.addView(title, new LayoutParams(MATCH, WRAP));
        TextView explanation = app.text("Home plays unwatched videos from the albums you pick.", 15, SUB, "sans-serif");
        explanation.setLineSpacing(app.dp(3), 1);
        addWithGap(head, explanation, 8);

        LinearLayout quick = app.row();
        addAction(quick, "Use suggestions", () -> setModes(-1));
        addAction(quick, "Select all", () -> {
            expanded = true;
            setModes(FeedSources.ALL);
        });
        addAction(quick, "Clear", () -> setModes(FeedSources.OFF));
        addWithGap(head, quick, 14);
        addView(head, new LayoutParams(MATCH, WRAP));

        ListView list = new ListView(app);
        list.setDivider(null);
        list.setItemsCanFocus(true);
        list.setPadding(app.dp(16), 0, app.dp(16), app.dp(8));
        list.setClipToPadding(false);
        list.setAdapter(adapter);
        addView(list, new LayoutParams(MATCH, 0, 1f));

        LinearLayout footer = app.column();
        footer.setPadding(app.dp(22), app.dp(11), app.dp(22), app.dp(12));
        footer.setBackgroundColor(PANEL);
        count = app.text("", 13, SUB, "sans-serif-medium");
        count.setGravity(Gravity.CENTER);
        footer.addView(count, new LayoutParams(MATCH, WRAP));
        done = app.pillButton("", () -> save.accept(choices));
        addWithGap(footer, done, 9);
        addView(footer, new LayoutParams(MATCH, WRAP));
        updateAlbums(albums);
        if (others.stream().anyMatch(album -> mode(album) != FeedSources.OFF)) {
            expanded = true;
            rebuildRows();
        }
    }

    void updateAlbums(Collection<VideoLibrary.Album> albums) {
        suggested.clear();
        others.clear();
        for (VideoLibrary.Album album : albums) {
            if (album.suggestedMode == FeedSources.OFF) others.add(album);
            else {
                suggested.add(album);
                if (setup) choices.putIfAbsent(album.key, album.suggestedMode);
            }
        }
        Comparator<VideoLibrary.Album> byName = Comparator.comparing(album -> album.title, String.CASE_INSENSITIVE_ORDER);
        suggested.sort(byName);
        others.sort(byName);
        if (suggested.isEmpty()) expanded = true;
        rebuildRows();
    }

    private void addAction(LinearLayout target, String title, Runnable action) {
        TextView b = app.text(title, 12, PAPER, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(app.dp(42));
        b.setPadding(app.dp(3), app.dp(6), app.dp(3), app.dp(6));
        app.click(b, EDGE, 12, action);
        LayoutParams p = new LayoutParams(0, WRAP, title.equals("Use suggestions") ? 1.4f : 1f);
        if (target.getChildCount() > 0) p.leftMargin = app.dp(6);
        target.addView(b, p);
    }

    private void addWithGap(LinearLayout parent, View view, int gap) {
        LayoutParams p = new LayoutParams(MATCH, WRAP);
        p.topMargin = app.dp(gap);
        parent.addView(view, p);
    }

    private int mode(VideoLibrary.Album album) { return choices.getOrDefault(album.key, FeedSources.OFF); }

    private void setModes(int mode) {
        for (List<VideoLibrary.Album> group : List.of(suggested, others)) {
            for (VideoLibrary.Album album : group) {
                if (mode == FeedSources.ALL && mode(album) != FeedSources.OFF) continue;
                int selected = mode < 0 ? album.suggestedMode : mode;
                if (mode == FeedSources.ALL && album.suggestedMode == FeedSources.PORTRAIT) {
                    selected = FeedSources.PORTRAIT;
                }
                choices.put(album.key, selected);
            }
        }
        rebuildRows();
    }

    private void setMode(VideoLibrary.Album album, int mode) {
        choices.put(album.key, mode);
        rebuildRows();
    }

    private void orientation(VideoLibrary.Album album) {
        String[] options = {"All orientations", "Portrait only", "Exclude album"};
        int[] modes = {FeedSources.ALL, FeedSources.PORTRAIT, FeedSources.OFF};
        int checked = mode(album) == FeedSources.ALL ? 0 : mode(album) == FeedSources.PORTRAIT ? 1 : 2;
        new AlertDialog.Builder(app)
                .setTitle(album.title + " videos")
                .setSingleChoiceItems(options, checked, (dialog, index) -> {
                    setMode(album, modes[index]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void rebuildRows() {
        rows.clear();
        if (!suggested.isEmpty()) {
            rows.add("Suggested albums");
            rows.addAll(suggested);
        }
        if (!others.isEmpty()) {
            rows.add("Other albums (" + others.size() + ")");
            if (expanded) rows.addAll(others);
        }
        if (rows.isEmpty()) rows.add("No video albums found");
        adapter.notifyDataSetChanged();
        int picked = 0;
        long videos = 0;
        for (List<VideoLibrary.Album> group : List.of(suggested, others)) {
            for (VideoLibrary.Album album : group) {
                int mode = mode(album);
                if (mode == FeedSources.OFF) continue;
                picked++;
                videos += mode == FeedSources.PORTRAIT ? album.portraits : album.count;
            }
        }
        count.setText(picked + (picked == 1 ? " album" : " albums") + "  ·  " + videos + (videos == 1 ? " video" : " videos"));
        done.setText(setup ? (videos == 0 ? "Finish setup" : "Start watching") : "Save changes");
    }

    private final class Rows extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return rows.get(p) instanceof VideoLibrary.Album ? 0 : 1; }
        @Override public boolean isEnabled(int p) { return false; }

        @Override public View getView(int p, View recycled, ViewGroup parent) {
            Object item = getItem(p);
            if (item instanceof VideoLibrary.Album) {
                AlbumRow row = recycled instanceof AlbumRow ? (AlbumRow) recycled : new AlbumRow();
                row.bind((VideoLibrary.Album) item);
                return row;
            }
            String name = (String) item;
            boolean expandable = name.startsWith("Other albums");
            TextView heading = app.text(name, 14, expandable ? PAPER : VIOLET, "sans-serif-medium");
            heading.setPadding(app.dp(9), app.dp(14), app.dp(9), app.dp(10));
            heading.setAccessibilityHeading(true);
            if (expandable) {
                heading.setText(name + (expanded ? "   ▴" : "   ▾"));
                heading.setContentDescription(name + (expanded ? ", expanded" : ", collapsed"));
                app.click(heading, Color.TRANSPARENT, 9, () -> {
                    expanded = !expanded;
                    rebuildRows();
                });
            }
            return heading;
        }
    }

    private final class AlbumRow extends LinearLayout {
        private final CheckBox check;
        private final TextView title, details, options;
        private final Glyph icon;
        private VideoLibrary.Album album;

        AlbumRow() {
            super(app);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(app.dp(5), app.dp(5), app.dp(5), app.dp(5));
            setMinimumHeight(app.dp(64));
            check = new CheckBox(app);
            check.setGravity(Gravity.CENTER);
            check.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{VIOLET, SUB}));
            check.setOnClickListener(v -> toggle());
            addView(check, new LayoutParams(app.dp(42), app.dp(48)));
            FrameLayout mark = new FrameLayout(app);
            mark.setBackground(app.round(PANEL, 10));
            icon = new Glyph(app, "albums", VIOLET, 19);
            mark.addView(icon, app.place(MATCH, MATCH, Gravity.CENTER));
            addView(mark, new LayoutParams(app.dp(38), app.dp(38)));
            LinearLayout labels = app.column();
            labels.setGravity(Gravity.CENTER_VERTICAL);
            labels.setPadding(app.dp(9), app.dp(4), app.dp(5), app.dp(4));
            title = app.text("", 15, PAPER, "sans-serif-medium");
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(title);
            details = app.text("", 12, SUB, "sans-serif");
            details.setPadding(0, app.dp(4), 0, 0);
            labels.addView(details);
            app.click(labels, Color.TRANSPARENT, 9, this::toggle);
            addView(labels, new LayoutParams(0, MATCH, 1f));
            options = app.text("Orientation", 12, VIOLET, "sans-serif-medium");
            options.setGravity(Gravity.CENTER);
            options.setMinHeight(app.dp(44));
            options.setPadding(app.dp(8), 0, app.dp(8), 0);
            app.click(options, Color.TRANSPARENT, 9, () -> orientation(album));
            addView(options, new LayoutParams(WRAP, WRAP));
        }

        void bind(VideoLibrary.Album album) {
            this.album = album;
            int mode = mode(album);
            title.setText(album.title);
            check.setChecked(mode != FeedSources.OFF);
            check.setContentDescription("Include " + album.title);
            options.setContentDescription("Video options for " + album.title);
            options.setVisibility(mode == FeedSources.OFF ? GONE : VISIBLE);
            icon.update("albums", mode == FeedSources.OFF ? SUB : VIOLET);
            if (mode == FeedSources.PORTRAIT) {
                details.setText("Portrait only  ·  " + album.portraits + " of " + album.count);
            } else {
                details.setText(album.count + (album.count == 1 ? " video" : " videos"));
            }
        }

        private void toggle() {
            if (mode(album) != FeedSources.OFF) setMode(album, FeedSources.OFF);
            else setMode(album, album.suggestedMode == FeedSources.PORTRAIT ? FeedSources.PORTRAIT : FeedSources.ALL);
        }
    }
}
