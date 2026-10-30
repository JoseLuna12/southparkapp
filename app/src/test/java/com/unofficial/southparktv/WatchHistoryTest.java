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

    @Test public void endingOrReachingLastTenSecondsOffersWatchAgain() {
        WatchHistory.Entry nearEnd = WatchHistory.snapshot(MAJOR, 12, SEASON, 1310000, 1320000, false);
        assertEquals(0, nearEnd.positionMs());
        assertTrue(nearEnd.completed());
        assertFalse(WatchHistory.snapshot(MAJOR, 12, SEASON, 1309999, 1320000, false).completed());
        assertTrue(WatchHistory.snapshot(MAJOR, 12, SEASON, 0, 0, true).completed());
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
