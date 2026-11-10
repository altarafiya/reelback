package app.reelback.gallery;

import java.util.Map;
import java.util.TreeMap;

final class WatchProgress {
    private final TreeMap<Integer, Integer> ranges = new TreeMap<>();
    private int covered;

    boolean watched(int duration) { return duration > 0 && (long) covered * 5 >= (long) duration * 4; }

    void add(int start, int end) {
        if (end <= start) return;
        Map.Entry<Integer, Integer> previous = ranges.floorEntry(start);
        if (previous != null && previous.getValue() >= start) {
            start = previous.getKey();
            end = Math.max(end, previous.getValue());
        }
        Map.Entry<Integer, Integer> next;
        while ((next = ranges.ceilingEntry(start)) != null && next.getKey() <= end) {
            covered -= next.getValue() - next.getKey();
            end = Math.max(end, next.getValue());
            ranges.remove(next.getKey());
        }
        ranges.put(start, end);
        covered += end - start;
    }
}
