package com.unofficial.southparktv;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class WatchHistoryTest {
    private static final Catalog.Episode MAJOR = new Catalog.Episode("major", "Major Boobage", "S12 • E3",
            "A Heavy Metal homage.", "22:00", "", "/episodes/4r4367/major", false);
    private static final Catalog.Episode CANADA = new Catalog.Episode("canada", "Canada On Strike", "S12 • E4",
            "Canada goes on strike.", "22:00", "", "/episodes/u1yv8w/canada", false);
    private static final String SEASON = "/seasons/south-park/gfstdd/season-12";

    private static android.content.SharedPreferences preferences(java.util.Map<String, Object> values) {
        var pending = new java.util.HashMap<String, Object>();
        var editor = (android.content.SharedPreferences.Editor) java.lang.reflect.Proxy.newProxyInstance(
                WatchHistoryTest.class.getClassLoader(), new Class<?>[]{android.content.SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) { pending.put((String) args[0], args[1]); return proxy; }
                    if (method.getName().equals("apply")) { values.putAll(pending); pending.clear(); return null; }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                WatchHistoryTest.class.getClassLoader(), new Class<?>[]{android.content.SharedPreferences.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "edit" -> editor;
                    case "contains" -> values.containsKey(args[0]);
                    case "getString", "getBoolean" -> values.getOrDefault(args[0], args[1]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test public void watchedEpisodesSurviveNewHistoryAndPartialRewatch() {
        var values = new java.util.HashMap<String, Object>();
        var preferences = preferences(values);
        assertFalse(WatchHistory.isWatched(preferences, "major"));
        // 22:00 crossed at 80% (17:36). That completion clears resume; the partial rewatch below restores it.
        WatchHistory.save(preferences, WatchHistory.snapshot(MAJOR, 12, SEASON, 1056000, 1320000, false));
        assertEquals(0L, values.get("major"));
        assertTrue(WatchHistory.isWatched(preferences, "major"));
        WatchHistory.save(preferences, WatchHistory.snapshot(CANADA, 12, SEASON, 45000, 1320000, false));
        WatchHistory.save(preferences, WatchHistory.snapshot(MAJOR, 12, SEASON, 45000, 1320000, false));
        assertTrue(WatchHistory.isWatched(preferences(values), "major"));
        assertFalse(WatchHistory.isWatched(preferences, "canada"));
        WatchHistory.setWatched(preferences, "major", false);
        assertFalse(WatchHistory.isWatched(preferences(values), "major"));
        WatchHistory.setWatched(preferences, "canada", true);
        assertTrue(WatchHistory.isWatched(preferences(values), "canada"));
        assertEquals(45000L, values.get("major"));
    }

    @Test public void migratesLegacyCompletionWithoutOverridingManualUnwatched() throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("lastEpisode", WatchHistory.encode(new WatchHistory.Entry(MAJOR, 12, SEASON, 0, 1320000, true)));
        var preferences = preferences(values);
        assertTrue(WatchHistory.isWatched(preferences, "major"));
        WatchHistory.setWatched(preferences, "major", false);
        assertFalse(WatchHistory.isWatched(preferences, "major"));
        WatchHistory.save(preferences, WatchHistory.snapshot(CANADA, 12, SEASON, 45000, 1320000, false));
        assertFalse(WatchHistory.isWatched(preferences, "major"));
        values.remove("watched:major");
        values.put("lastEpisode", WatchHistory.encode(new WatchHistory.Entry(MAJOR, 12, SEASON, 0, 1320000, true)));
        WatchHistory.save(preferences, WatchHistory.snapshot(CANADA, 12, SEASON, 45000, 1320000, false));
        assertTrue(WatchHistory.isWatched(preferences, "major"));
    }

    @Test public void encodesActualEpisodeAndResumeMetadata() throws Exception {
        JSONObject json = new JSONObject(WatchHistory.encode(new WatchHistory.Entry(MAJOR, 12, SEASON, 65000, 1320000, false)));
        assertEquals("Major Boobage", json.getString("title"));
        assertEquals("major", json.getString("id"));
        assertEquals(12, json.getInt("season"));
        assertEquals(SEASON, json.getString("seasonPath"));
        assertEquals(65000, json.getLong("positionMs"));
        assertFalse(json.getBoolean("completed"));
    }

    @Test public void decodesIndependentlyWrittenSavedHistory() throws Exception {
        WatchHistory.Entry entry = WatchHistory.decode("""
                {"id":"canada","title":"Canada On Strike","label":"S12 • E4",
                 "path":"/episodes/u1yv8w/canada","season":12,
                 "seasonPath":"/seasons/south-park/gfstdd/season-12",
                 "positionMs":90000,"durationMs":1320000,"completed":false}
                """);
        assertEquals("Canada On Strike", entry.episode().title());
        assertEquals("/episodes/u1yv8w/canada", entry.episode().path());
        assertEquals("S12 • E4", entry.episode().label());
        assertEquals(90000, entry.positionMs());
        assertEquals(1320000, entry.durationMs());
        assertEquals(12, entry.season());
        assertFalse(entry.completed());
    }

    @Test public void savesPartialProgressAndClampsInvalidPosition() {
        WatchHistory.Entry partial = WatchHistory.snapshot(MAJOR, 12, SEASON, 45000, 1320000, false);
        assertEquals(45000, partial.positionMs());
        assertFalse(partial.completed());
        assertEquals(0, WatchHistory.snapshot(MAJOR, 12, SEASON, -1, 1320000, false).positionMs());
    }

    @Test public void unknownDurationDoesNotMeanCompleted() {
        WatchHistory.Entry entry = WatchHistory.snapshot(MAJOR, 12, SEASON, 25000, -9223372036854775807L, false);
        assertEquals(25000, entry.positionMs());
        assertEquals(0, entry.durationMs());
        assertFalse(entry.completed());
    }

    @Test public void twentyTwoMinuteEpisodeCompletesAtEightyPercentAndKeepsEarlierResume() {
        // 22:00 = 1_320_000 ms. 80% is 17:36 = 1_056_000. Two minutes remain at 20:00, after 80%.
        WatchHistory.Entry opening = WatchHistory.snapshot(MAJOR, 12, SEASON, 15000, 1320000, false);
        assertFalse(opening.completed());
        assertEquals(15000, opening.positionMs());

        WatchHistory.Entry halfway = WatchHistory.snapshot(MAJOR, 12, SEASON, 660000, 1320000, false);
        assertFalse(halfway.completed());
        assertEquals(660000, halfway.positionMs());

        WatchHistory.Entry before = WatchHistory.snapshot(MAJOR, 12, SEASON, 1055999, 1320000, false);
        assertFalse(before.completed());
        assertEquals(1055999, before.positionMs());

        WatchHistory.Entry crossed = WatchHistory.snapshot(MAJOR, 12, SEASON, 1056000, 1320000, false);
        assertTrue(crossed.completed());
        assertEquals(0, crossed.positionMs());
        assertEquals(1320000, crossed.durationMs());

        WatchHistory.Entry credits = WatchHistory.snapshot(MAJOR, 12, SEASON, 1200000, 1320000, false);
        assertTrue(credits.completed());
        assertEquals(0, credits.positionMs());
    }

    @Test public void endedPlaybackCompletesEvenBeforeTheThreshold() {
        WatchHistory.Entry endedEarly = WatchHistory.snapshot(MAJOR, 12, SEASON, 300000, 1320000, true);
        assertTrue(endedEarly.completed());
        assertEquals(0, endedEarly.positionMs());
        WatchHistory.Entry endedUnknown = WatchHistory.snapshot(MAJOR, 12, SEASON, 0, 0, true);
        assertTrue(endedUnknown.completed());
        assertEquals(0, endedUnknown.positionMs());
    }

    @Test public void shortDurationDoesNotAutoCompleteUntilPlaybackEnds() {
        // 45s intro is under the one-minute minimum, even at ~89% and inside a two-minute remainder.
        WatchHistory.Entry intro = WatchHistory.snapshot(MAJOR, 12, SEASON, 40000, 45000, false);
        assertFalse(intro.completed());
        assertEquals(40000, intro.positionMs());
        assertFalse(WatchHistory.snapshot(MAJOR, 12, SEASON, 59000, 59999, false).completed());

        // Exactly one minute uses 80% (48s), not "two minutes remain" (true for the whole item).
        WatchHistory.Entry beforeMinute = WatchHistory.snapshot(MAJOR, 12, SEASON, 47999, 60000, false);
        assertFalse(beforeMinute.completed());
        assertEquals(47999, beforeMinute.positionMs());
        WatchHistory.Entry minute = WatchHistory.snapshot(MAJOR, 12, SEASON, 48000, 60000, false);
        assertTrue(minute.completed());
        assertEquals(0, minute.positionMs());

        assertTrue(WatchHistory.snapshot(MAJOR, 12, SEASON, 45000, 45000, true).completed());
    }

    @Test public void twoMinuteTailCompletesOnlyAfterHalfwayAndBeforeEightyPercent() {
        // 8:00 = 480_000. Two minutes left is 6:00 = 360_000, which is before 80% (6:24 = 384_000).
        WatchHistory.Entry beforeTail = WatchHistory.snapshot(MAJOR, 12, SEASON, 359999, 480000, false);
        assertFalse(beforeTail.completed());
        assertEquals(359999, beforeTail.positionMs());
        WatchHistory.Entry tail = WatchHistory.snapshot(MAJOR, 12, SEASON, 360000, 480000, false);
        assertTrue(tail.completed());
        assertEquals(0, tail.positionMs());

        // 3:00 = 180_000. Two minutes left is 1:00, still before halfway (1:30), so it stays resumable.
        WatchHistory.Entry earlyTail = WatchHistory.snapshot(MAJOR, 12, SEASON, 60000, 180000, false);
        assertFalse(earlyTail.completed());
        assertEquals(60000, earlyTail.positionMs());
        assertFalse(WatchHistory.snapshot(MAJOR, 12, SEASON, 89999, 180000, false).completed());
        WatchHistory.Entry half = WatchHistory.snapshot(MAJOR, 12, SEASON, 90000, 180000, false);
        assertTrue(half.completed());
        assertEquals(0, half.positionMs());

        // 90s is inside a two-minute remainder from the first second; only 80% (72s) counts.
        assertFalse(WatchHistory.snapshot(MAJOR, 12, SEASON, 1000, 90000, false).completed());
        assertFalse(WatchHistory.snapshot(MAJOR, 12, SEASON, 71999, 90000, false).completed());
        WatchHistory.Entry ninety = WatchHistory.snapshot(MAJOR, 12, SEASON, 72000, 90000, false);
        assertTrue(ninety.completed());
        assertEquals(0, ninety.positionMs());
    }

    @Test public void recommendsCanadaAfterMajorAndDoesNotWrapOrGuessMissingEpisode() {
        assertEquals("Canada On Strike", WatchHistory.next(List.of(MAJOR, CANADA), "major").title());
        assertNull(WatchHistory.next(List.of(MAJOR, CANADA), "canada"));
        assertNull(WatchHistory.next(List.of(MAJOR, CANADA), "missing"));
        assertNull(WatchHistory.next(List.of(), "major"));
    }

    @Test public void doesNotSilentlySkipALockedNextEpisode() {
        Catalog.Episode locked = new Catalog.Episode("locked", "Locked episode", "S12 • E4", "", "22:00", "", "/episodes/locked", true);
        assertEquals("locked", WatchHistory.next(List.of(MAJOR, locked, CANADA), "major").id());
        assertTrue(WatchHistory.next(List.of(MAJOR, locked, CANADA), "major").locked());
    }

    @Test public void rejectsHistoryPointingOffSite() {
        assertThrows(IllegalArgumentException.class, () -> WatchHistory.decode("""
                {"id":"bad","title":"bad","label":"bad","path":"https://evil.example/video",
                 "season":12,"seasonPath":"/seasons/south-park/s12","positionMs":0,"durationMs":0,"completed":false}
                """));
    }
}
