package com.unofficial.southparktv;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SiteClientTest {
    private static final Catalog.Season SEASON = new Catalog.Season(12, "/seasons/south-park/gfstdd/season-12");
    private static final Catalog.Episode MAJOR = new Catalog.Episode("major", "Major Boobage", "S12 • E3",
            "", "22:00", "", "/episodes/major", false);
    private static final Catalog.Episode CANADA = new Catalog.Episode("canada", "Canada On Strike", "S12 • E4",
            "", "22:00", "", "/episodes/canada", false);

    @Test public void nextUsesTheFollowingOfficialEntry() throws Exception {
        SiteClient.NextEpisode next = new SiteClient().next(MAJOR, SEASON, List.of(MAJOR, CANADA), List.of(SEASON));
        assertEquals("canada", next.episode().id());
        assertEquals(12, next.season().number());
    }

    @Test public void finalEpisodeDoesNotWrapToTheFirstEpisode() throws Exception {
        assertNull(new SiteClient().next(CANADA, SEASON, List.of(MAJOR, CANADA), List.of(SEASON)));
    }

    @Test public void missingCurrentEpisodeDoesNotInventASuccessor() throws Exception {
        assertNull(new SiteClient().next(MAJOR, SEASON, List.of(CANADA), List.of(SEASON)));
    }

    @Test public void emptyGuideDoesNotInventAnEpisode() throws Exception {
        assertNull(new SiteClient().next(MAJOR, SEASON, List.of(), List.of(SEASON)));
    }
}
