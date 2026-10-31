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

    @Test public void unverifiedSourceCannotResolveOrBeSelected() {
        assertEquals("es-419", RegionalSource.LATAM.language);
        assertFalse(RegionalSource.LATAM.enabled());
        assertNull(RegionalSource.LATAM.origin);
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.requireEnabled("latam"));
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.requireEnabled("unknown"));
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.requireEnabled(null));
        assertThrows(IllegalArgumentException.class, () -> RegionalSource.LATAM.page("/episodes/a"));
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
        assertTrue(RegionalSource.US.service("https://topaz.paramount.tech/topaz/api/example/mica.json"));
        assertFalse(RegionalSource.US.service("https://topaz.paramount.tech/other"));
        assertFalse(RegionalSource.US.service("https://user@topaz.paramount.tech/topaz/api/a"));
        assertThrows(IllegalArgumentException.class,
                () -> RegionalSource.US.checkDownload("https://evil.test/image.jpg"));
    }
}
