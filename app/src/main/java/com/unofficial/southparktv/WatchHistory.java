package com.unofficial.southparktv;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** One durable last-watched entry, alongside the existing per-episode resume positions. */
final class WatchHistory {
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

    static Entry snapshot(Catalog.Episode episode, int season, String seasonPath,
                          long position, long duration, boolean ended) {
        boolean completed = ended || duration > 10000 && position >= duration - 10000;
        return new Entry(episode, season, seasonPath, completed ? 0 : Math.max(0, position),
                Math.max(0, duration), completed);
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
