package com.unofficial.southparktv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses data sent to the public website. Does not execute website JavaScript. */
final class Catalog {
    static final String BASE = "https://www.southparkstudios.com";
    private static final Pattern DATA = Pattern.compile("window\\.__DATA__\\s*=\\s*");

    record Season(int number, String path) { }
    record Episode(String id, String title, String label, String description,
                   String duration, String image, String path, boolean locked) { }
    record SeasonPage(List<Episode> episodes, String nextPage) { }

    static JSONObject pageData(String html) throws JSONException {
        Matcher matcher = DATA.matcher(html);
        if (!matcher.find()) throw new JSONException("The official site's page data has changed.");
        Object result = new JSONTokener(html.substring(matcher.end())).nextValue();
        if (!(result instanceof JSONObject)) throw new JSONException("Invalid page data.");
        return (JSONObject) result;
    }

    static List<Season> seasons(JSONObject page, String currentPath) throws JSONException {
        Map<Integer, Season> result = new LinkedHashMap<>();
        visit(page, object -> {
            int number = object.optInt("seasonNumber", 0);
            if (number > 0 && object.has("label") && object.has("url")) {
                String path = string(object, "url");
                if (path.isEmpty()) path = currentPath;
                if (path.startsWith("/seasons/south-park")) result.put(number, new Season(number, path));
            }
        });
        if (result.isEmpty()) throw new JSONException("No seasons were found on the official site.");
        List<Season> seasons = new ArrayList<>(result.values());
        seasons.sort(Comparator.comparingInt(Season::number));
        return seasons;
    }

    static SeasonPage seasonPage(JSONObject page) throws JSONException {
        List<JSONObject> guides = new ArrayList<>();
        visit(page, object -> {
            if (object.has("isEpisodes") && object.has("items")) guides.add(object);
        });
        if (guides.isEmpty()) throw new JSONException("No episode guide was found on this season page.");
        JSONObject guide = guides.get(0);
        if (!guide.optBoolean("isEpisodes")) {
            JSONObject filters = guide.optJSONObject("filters");
            JSONArray choices = filters == null ? null : filters.optJSONArray("items");
            if (choices != null) for (int i = 0; i < choices.length(); i++) {
                String url = string(choices.getJSONObject(i), "url");
                if (url.contains("/episode/")) return new SeasonPage(new ArrayList<>(), url);
            }
            // The current Season 29 page offers clips only, not full episodes.
            return new SeasonPage(new ArrayList<>(), "");
        }
        JSONObject more = guide.optJSONObject("loadMore");
        return new SeasonPage(episodes(guide), more == null ? "" : string(more, "url"));
    }

    static List<Episode> episodes(JSONObject feed) throws JSONException {
        JSONArray items = feed.getJSONArray("items");
        List<Episode> episodes = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (!"episode".equals(item.optString("entityType"))) continue;
            JSONObject meta = item.getJSONObject("meta");
            JSONObject media = item.getJSONObject("media");
            JSONObject image = media.optJSONObject("image");
            JSONObject header = meta.optJSONObject("header");
            JSONObject title = header == null ? null : header.optJSONObject("title");
            String path = string(item, "url");
            String locked = string(media, "lockedLabel");
            episodes.add(new Episode(item.getString("id"), meta.getString("subHeader"),
                    title == null ? "" : title.optString("text", ""), meta.optString("description", ""),
                    media.optString("duration", ""), image == null ? "" : image.optString("url", ""),
                    path, !locked.isEmpty() || !path.startsWith("/episodes/")));
        }
        return episodes;
    }

    static JSONObject videoDetail(JSONObject page) throws JSONException {
        List<JSONObject> details = new ArrayList<>();
        visit(page, object -> {
            JSONObject detail = object.optJSONObject("videoDetail");
            if (detail != null && detail.has("videoServiceUrl")) details.add(detail);
        });
        if (details.isEmpty()) throw new JSONException("The official site did not provide playback information.");
        return details.get(0);
    }

    static String officialPage(String path) {
        URI uri = URI.create(BASE).resolve(path);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"www.southparkstudios.com".equalsIgnoreCase(uri.getHost())
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
            throw new IllegalArgumentException("Only official South Park pages are supported.");
        return uri.toString();
    }

    // Android's org.json can stringify JSONObject.NULL as "null", unlike the JVM test library.
    private static String string(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key, "");
    }

    private interface Visitor { void accept(JSONObject object); }
    private static void visit(Object value, Visitor visitor) {
        if (value instanceof JSONObject object) {
            visitor.accept(object);
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) visit(object.opt(keys.next()), visitor);
        } else if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) visit(array.opt(i), visitor);
        }
    }
}
