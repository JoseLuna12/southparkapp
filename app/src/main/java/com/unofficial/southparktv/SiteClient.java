package com.unofficial.southparktv;

import android.os.Build;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SiteClient {
    final RegionalSource source;
    SiteClient() { this(RegionalSource.US); }
    SiteClient(RegionalSource source) {
        if (!source.enabled()) throw new IllegalArgumentException("Source is not verified");
        this.source = source;
    }
    static final String USER_AGENT = "SouthParkTV/0.2 (Android " + Build.VERSION.RELEASE + "; Android TV)";
    private static final Pattern NEXT = Pattern.compile("^(/api/context/[^/]+/episode/)(\\d+)(/\\d+)$");
    record Playback(String cmsId, String videoId, String authToken, Map<String, String> adParameters) { }

    record NextEpisode(Catalog.Episode episode, Catalog.Season season) { }

    NextEpisode next(Catalog.Episode current, Catalog.Season season, List<Catalog.Episode> cached,
                     List<Catalog.Season> availableSeasons) throws Exception {
        List<Catalog.Episode> listed = cached == null ? episodes(season) : cached;
        Catalog.Episode following = WatchHistory.next(listed, current.id());
        if (following != null) return new NextEpisode(following, season);
        if (listed.isEmpty() || !listed.get(listed.size() - 1).id().equals(current.id())) return null;
        Catalog.Season nextSeason = availableSeasons.stream().filter(s -> s.number() > season.number()).findFirst().orElse(null);
        if (nextSeason == null) return null;
        List<Catalog.Episode> nextSeasonEpisodes = episodes(nextSeason);
        return nextSeasonEpisodes.isEmpty() ? null : new NextEpisode(nextSeasonEpisodes.get(0), nextSeason);
    }

    List<Catalog.Season> seasons() throws Exception {
        String path = source.seasonsPath;
        android.util.Log.i("SouthParkSource", "Catalog source=" + source.id
                + " page=" + source.page(path));
        return Catalog.seasons(Catalog.pageData(get(source.page(path))), path, source);
    }

    List<Catalog.Episode> episodes(Catalog.Season season) throws Exception {
        JSONObject data = Catalog.pageData(get(source.page(season.path())));
        Catalog.SeasonPage page = Catalog.seasonPage(data, source);
        Map<String, Catalog.Episode> episodes = new LinkedHashMap<>();
        for (Catalog.Episode episode : page.episodes()) episodes.put(episode.id(), episode);
        String next = page.nextPage();
        // Fetch every page, not just the website's initial ten cards.
        for (int count = 0; !next.isEmpty(); count++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Request cancelled.");
            if (count >= 20) throw new IOException("The episode guide has too many pages. Please retry after updating the app.");
            if (source.paginationPath == null) throw new IOException("Official pagination is not verified for this source.");
            Matcher matcher = NEXT.matcher(next);
            if (!matcher.matches()) throw new IOException("The official site's pagination format changed.");
            List<Catalog.Episode> batch = Catalog.episodes(new JSONObject(get(source.page(next))), source);
            if (batch.isEmpty()) break;
            int previous = episodes.size();
            for (Catalog.Episode episode : batch) episodes.put(episode.id(), episode);
            if (previous == episodes.size()) throw new IOException("The official site repeated an episode page.");
            int size = Integer.parseInt(matcher.group(3).substring(1));
            if (batch.size() < size) break;
            next = matcher.group(1) + (Integer.parseInt(matcher.group(2)) + 1) + matcher.group(3);
        }
        return new ArrayList<>(episodes.values());
    }

    Playback playback(Catalog.Episode episode) throws Exception {
        if (episode.locked()) throw new IOException("This episode is not available for free playback on the official site.");
        JSONObject detail = Catalog.videoDetail(Catalog.pageData(get(source.page(episode.path()))));
        if (detail.optBoolean("authRequired")) throw new IOException("The official site requires a provider login for this episode.");
        String service = detail.getString("videoServiceUrl").split("\\?", 2)[0];
        Uri uri = Uri.parse(service);
        if (!source.service(service)) throw new IOException("Unrecognized official playback service.");
        String session = UUID.randomUUID().toString();
        Uri micaUri = uri.buildUpon().appendQueryParameter("clientPlatform", source.platform)
                .appendQueryParameter("deviceType", "tv").appendQueryParameter("os", "Android")
                .appendQueryParameter("lat", "1").appendQueryParameter("ssus", session).build();
        JSONObject mica = new JSONObject(get(micaUri.toString()));
        JSONObject stream = mica.optJSONObject("stitchedstream");
        JSONObject ima = mica.optJSONObject("imasdk");
        if (ima == null || stream == null || !"googledai".equals(stream.optString("adstitchingprovider")))
            throw new IOException("The official service did not offer a supported ad-aware stream. It may be unavailable in your region.");
        if (!"hls".equals(stream.optString("manifesttype"))) throw new IOException("The official stream format is not supported yet.");
        Map<String, String> params = new LinkedHashMap<>();
        JSONObject supplied = ima.optJSONObject("freewheelparams");
        if (supplied != null) {
            java.util.Iterator<String> keys = supplied.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = supplied.optString(key, "");
                // Unresolved macros are not consent signals. Never invent consent strings.
                if (!value.contains("{") && !value.isEmpty()) params.put(key, value);
            }
        }
        params.put("npa", "1");
        params.put("rdp", "1");
        params.put("imafw__fw_is_lat", "1");
        params.put("imafw__vmn_ar", "0");
        params.put("imafw__fw_playid", UUID.randomUUID().toString());
        params.put("imafw__fw_h_referer", source.page(episode.path()));
        return new Playback(ima.getString("cmsid"), ima.getString("vid"), ima.getString("authtoken"), params);
    }

    static byte[] download(String address, int maxBytes) throws IOException {
        return download(RegionalSource.US, address, maxBytes);
    }

    static byte[] download(RegionalSource source, String address, int maxBytes) throws IOException {
        URL url = new URL(address);
        for (int redirect = 0; redirect < 6; redirect++) {
            try { source.checkDownload(url.toString()); }
            catch (IllegalArgumentException e) {
                android.util.Log.i("SouthParkSource", "Rejected redirect source="
                        + source.id + " host=" + url.getHost());
                throw new IOException("Unsupported official address.", e);
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Invalid redirect from the official service.");
                    url = new URL(url, location);
                    continue;
                }
                if (status != 200) throw new IOException("Official service returned HTTP " + status + ". Check availability in your region.");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Request cancelled.");
                        if (output.size() + length > maxBytes) throw new IOException("Official response is too large.");
                        output.write(buffer, 0, length);
                    }
                    return output.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects from the official service.");
    }

    private String get(String address) throws IOException {
        return new String(download(source, address, 8 * 1024 * 1024), StandardCharsets.UTF_8);
    }
}
