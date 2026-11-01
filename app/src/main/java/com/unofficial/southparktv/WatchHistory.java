package com.unofficial.southparktv;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * One durable last-watched entry, alongside the existing per-episode resume positions.
 *
 * <p>An episode is completed when playback ends, or when progress crosses the watched
 * threshold below. A completed snapshot stores position 0, so Continue offers Watch
 * again from the start — the same outcome as a natural ending. Progress below the
 * threshold keeps the clamped playhead, so Continue resumes there. Saving a completed
 * entry sets the watched flag; a later partial rewatch does not clear it.
 * {@link #setWatched} is the details-dialog toggle and does not move the resume position.
 */
final class WatchHistory {
    /** Intros, bumpers and unknown durations are shorter than this and do not auto-complete. */
    static final long MIN_COMPLETION_DURATION_MS = 60_000L;
    /** Leaving with this much runtime left counts once the viewer is also past halfway. */
    static final long COMPLETION_REMAINING_MS = 120_000L;

    record Entry(Catalog.Episode episode, int season, String seasonPath,
                 long positionMs, long durationMs, boolean completed) { }

    static Entry last(SharedPreferences preferences) {
        try { return decode(preferences.getString("lastEpisode", "")); }
        catch (JSONException | IllegalArgumentException e) { return null; }
    }

    static void save(SharedPreferences preferences, Entry entry) {
        try {
            SharedPreferences.Editor editor = preferences.edit().putString("lastEpisode", encode(entry))
                    .putLong(entry.episode().id(), entry.positionMs());
            Entry previous = last(preferences);
            if (previous != null && previous.completed() && !preferences.contains("watched:" + previous.episode().id())) {
                editor.putBoolean("watched:" + previous.episode().id(), true);
            }
            if (entry.completed()) editor.putBoolean("watched:" + entry.episode().id(), true);
            editor.apply();
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid watch history", e); }
    }

    static boolean isWatched(SharedPreferences preferences, String id) {
        String key = "watched:" + id;
        if (preferences.contains(key)) return preferences.getBoolean(key, false);
        Entry last = last(preferences);
        return last != null && last.completed() && last.episode().id().equals(id);
    }

    static void setWatched(SharedPreferences preferences, String id, boolean watched) {
        preferences.edit().putBoolean("watched:" + id, watched).apply();
    }

    /**
     * Watched threshold for ordinary episodes (about 20–25 minutes): the earlier of
     * 80% of the runtime or two minutes remaining, and never before halfway.
     * On a 22-minute episode, 80% is 17:36, so leaving during the last act counts.
     * The two-minute window is earlier only for runtimes under ten minutes; the
     * halfway floor keeps that window from marking the start of a short extra.
     * Content under one minute completes only when playback actually ends.
     */
    static Entry snapshot(Catalog.Episode episode, int season, String seasonPath,
                          long position, long duration, boolean ended) {
        long safePosition = Math.max(0, position);
        long safeDuration = Math.max(0, duration);
        boolean completed = ended || progressCountsAsWatched(safePosition, safeDuration);
        return new Entry(episode, season, seasonPath, completed ? 0 : safePosition, safeDuration, completed);
    }

    private static boolean progressCountsAsWatched(long position, long duration) {
        if (position <= 0 || duration < MIN_COMPLETION_DURATION_MS) return false;
        if (atLeastEightyPercent(position, duration)) return true;
        if (duration <= COMPLETION_REMAINING_MS) return false;
        return position >= duration - COMPLETION_REMAINING_MS && position >= duration - position;
    }

    private static boolean atLeastEightyPercent(long position, long duration) {
        // First millisecond at or above 4/5 of the runtime. Avoids position * 5 overflow.
        long threshold = (duration / 5) * 4 + (duration % 5 * 4 + 4) / 5;
        return position >= threshold;
    }

    static Catalog.Episode next(List<Catalog.Episode> episodes, String lastId) {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).id().equals(lastId)) return i + 1 < episodes.size() ? episodes.get(i + 1) : null;
        }
        return null;
    }

    static String encode(Entry entry) throws JSONException {
        Catalog.Episode e = entry.episode();
        return new JSONObject().put("id", e.id()).put("title", e.title()).put("label", e.label())
                .put("description", e.description()).put("duration", e.duration()).put("image", e.image())
                .put("path", e.path()).put("locked", e.locked()).put("season", entry.season())
                .put("seasonPath", entry.seasonPath()).put("positionMs", entry.positionMs())
                .put("durationMs", entry.durationMs()).put("completed", entry.completed()).toString();
    }

    static Entry decode(String json) throws JSONException {
        JSONObject value = new JSONObject(json);
        String path = value.getString("path"), seasonPath = value.getString("seasonPath");
        if (!path.startsWith("/episodes/") || !seasonPath.startsWith("/seasons/south-park")
                || value.getInt("season") < 1) throw new IllegalArgumentException("Invalid history page");
        Catalog.officialPage(path);
        Catalog.officialPage(seasonPath);
        return new Entry(new Catalog.Episode(value.getString("id"), value.getString("title"),
                value.getString("label"), value.optString("description", ""), value.optString("duration", ""),
                value.optString("image", ""), path, value.optBoolean("locked")), value.getInt("season"),
                seasonPath, Math.max(0, value.getLong("positionMs")), Math.max(0, value.getLong("durationMs")),
                value.getBoolean("completed"));
    }
}
