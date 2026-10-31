package com.unofficial.southparktv;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppUpdateTest {
    private String metadata() {
        return "{\"schemaVersion\":1,\"applicationId\":\"com.unofficial.southparktv\",\"versionCode\":8,\"versionName\":\"0.10.0\",\"minSdk\":26,\"apkUrl\":\"https://github.com/JoseLuna12/southparkapp/releases/download/v0.10.0/app.apk\",\"sha256\":\"" + "a".repeat(64) + "\",\"sizeBytes\":1234}";
    }
    @Test public void parsesReleaseContract() throws Exception {
        AppUpdate.Release r = AppUpdate.parse(metadata());
        assertEquals(8, r.code()); assertEquals("0.10.0", r.name()); assertEquals(1234L, r.size());
    }
    @Test public void rejectsInvalidMetadata() throws Exception {
        for (String invalid : new String[]{"{}", metadata().replace("schemaVersion\":1", "schemaVersion\":2"),
                metadata().replace("versionCode\":8", "versionCode\":2147483648"),
                metadata().replace("sizeBytes\":1234", "sizeBytes\":0"),
                metadata().replace("com.unofficial.southparktv", "other.app"), metadata().replace("a".repeat(64), "bad")}) {
            try { AppUpdate.parse(invalid); fail("Accepted invalid metadata"); } catch (java.io.IOException | org.json.JSONException expected) { }
        }
    }
    @Test public void confinesAssetsToProject() {
        assertTrue(AppUpdate.assetUrl("https://github.com/JoseLuna12/southparkapp/releases/download/v1/app.apk"));
        for (String url : new String[]{"http://github.com/JoseLuna12/southparkapp/releases/download/v1/a.apk",
                "https://github.com/evil/southparkapp/releases/download/v1/a.apk",
                "https://github.com@evil.com/JoseLuna12/southparkapp/releases/download/v1/a.apk",
                "https://github.com:443/JoseLuna12/southparkapp/releases/download/v1/a.apk",
                "https://github.com/JoseLuna12/southparkapp/releases/download/../a.apk"}) assertFalse(AppUpdate.assetUrl(url));
    }
}
