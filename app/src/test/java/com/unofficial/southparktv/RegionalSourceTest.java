package com.unofficial.southparktv;

import org.junit.Test;
import static org.junit.Assert.*;

public class RegionalSourceTest {
    @Test public void currentSourcePreservesLegacyStorageWithoutCopying() {
        assertSame(RegionalSource.US, RegionalSource.requireEnabled("us"));
        assertEquals("playback", RegionalSource.US.historyFile());
        assertEquals("MainActivity", RegionalSource.US.catalogFile());
        assertEquals("playback:latam", RegionalSource.LATAM.historyFile());
        assertEquals("catalog:latam", RegionalSource.LATAM.catalogFile());
    }

    @Test public void spanishUsesPublishedOfficialLinkAndRejectsEnglishFallback() {
        assertEquals("es-419", RegionalSource.LATAM.language);
        assertSame(RegionalSource.LATAM, RegionalSource.requireEnabled("latam"));
        assertEquals("https://southpark.cc.com/es/seasons/south-park",
                RegionalSource.LATAM.page("/es/seasons/south-park"));
        assertEquals("https://southpark.cc.com/es/episodios/940f8z/south-america-cartman-consigue-una-sonda-anal-temporada-1-ep-1",
                RegionalSource.LATAM.page("/es/episodios/940f8z/south-america-cartman-consigue-una-sonda-anal-temporada-1-ep-1"));
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.LATAM.page("/episodes/a"));
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.LATAM.page("https://www.southparkstudios.com/es/seasons/south-park"));
        assertFalse(RegionalSource.LATAM.service("https://topaz.paramount.tech/topaz/api/a"));
        assertNull(RegionalSource.LATAM.paginationPath);
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.requireEnabled("unknown"));
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.requireEnabled(null));
    }

    @Test public void spanishHistoryRestoresOnlyUnderItsOwnProfile() throws Exception {
        String saved = """
                {"id":"same-id","title":"Cartman consigue una sonda anal","label":"T1 E1",
                 "path":"/es/episodios/940f8z/south-america-cartman-consigue-una-sonda-anal-temporada-1-ep-1",
                 "season":1,"seasonPath":"/es/seasons/south-park","positionMs":45000,
                 "durationMs":1320000,"completed":false}
                """;
        WatchHistory.Entry entry = WatchHistory.decode(saved, RegionalSource.LATAM);
        assertEquals(45000, entry.positionMs());
        assertEquals("same-id", entry.episode().id());
        assertThrows(IllegalArgumentException.class, () -> WatchHistory.decode(saved, RegionalSource.US));
    }

    @Test public void pagesAndServicesUseExactSecureOriginsAndPaths() {
        assertEquals("https://www.southparkstudios.com/episodes/example",
                RegionalSource.US.page("/episodes/example"));
        for (String address : new String[]{
                "https://www.southparkstudios.com.evil.test/episodes/a",
                "https://user@www.southparkstudios.com/episodes/a",
                "https://www.southparkstudios.com:8443/episodes/a",
                "http://www.southparkstudios.com/episodes/a",
                "/es/episodes/a", "/seasons/south-park-foreign/a", "/episodes/../other",
                "/episodes/%2e%2e/other"}) {
            assertThrows(address, IllegalArgumentException.class, () -> RegionalSource.US.page(address));
        }
        assertEquals("https://www.southparkstudios.com/api/context/mgid%3Aarc%3Aseason/episode/2/10",
                RegionalSource.US.page("/api/context/mgid%3Aarc%3Aseason/episode/2/10"));
        assertTrue(RegionalSource.US.service("https://topaz.paramount.tech/topaz/api/example/mica.json"));
        assertFalse(RegionalSource.US.service("https://topaz.paramount.tech/other"));
        assertFalse(RegionalSource.US.service("https://user@topaz.paramount.tech/topaz/api/a"));
        assertThrows(IllegalArgumentException.class,
                () -> RegionalSource.US.checkDownload("https://evil.test/image.jpg"));
    }
}
