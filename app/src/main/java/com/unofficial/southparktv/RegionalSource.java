package com.unofficial.southparktv;

import java.net.URI;

/** Official source configuration. Unverified sources deliberately have no network configuration. */
final class RegionalSource {
    static final RegionalSource US = new RegionalSource("us", "en", "https://www.southparkstudios.com",
            "/seasons/south-park", "/episodes/", "/api/context/", "topaz.paramount.tech", "/topaz/api/",
            "images.paramount.tech", "androidtv");
    static final RegionalSource LATAM = new RegionalSource("latam", "es-419", null, null, null, null,
            null, null, null, null);
    final String id, language, origin, seasonsPath, episodesPath, paginationPath;
    final String serviceHost, servicePath, imageHost, platform;

    private RegionalSource(String id, String language, String origin, String seasonsPath,
                           String episodesPath, String paginationPath, String serviceHost,
                           String servicePath, String imageHost, String platform) {
        this.id = id; this.language = language; this.origin = origin; this.seasonsPath = seasonsPath;
        this.episodesPath = episodesPath; this.paginationPath = paginationPath;
        this.serviceHost = serviceHost; this.servicePath = servicePath;
        this.imageHost = imageHost; this.platform = platform;
    }

    boolean enabled() { return origin != null; }

    static RegionalSource requireEnabled(String id) {
        if (US.id.equals(id)) return US;
        throw new IllegalArgumentException("Unsupported official source");
    }

    // Retain the original US files as its namespace: no copying, deletion or repeat migration.
    String historyFile() { return this == US ? "playback" : "playback:" + id; }
    String catalogFile() { return this == US ? "MainActivity" : "catalog:" + id; }

    private URI secure(String address) {
        if (!enabled()) throw new IllegalArgumentException("Source is not verified");
        URI uri = URI.create(origin).resolve(address).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawPath().contains("%") || uri.getRawPath().contains("\\"))
            throw new IllegalArgumentException("Unsupported official address");
        return uri;
    }

    String page(String address) {
        URI uri = secure(address);
        String path = uri.getPath();
        if (!URI.create(origin).getHost().equalsIgnoreCase(uri.getHost())
                || !(path.equals(seasonsPath) || path.startsWith(seasonsPath + "/")
                || path.startsWith(episodesPath) || path.startsWith(paginationPath)))
            throw new IllegalArgumentException("Unsupported official page");
        return uri.toString();
    }

    boolean service(String address) {
        try {
            URI uri = secure(address);
            return serviceHost.equalsIgnoreCase(uri.getHost()) && uri.getPath().startsWith(servicePath);
        } catch (IllegalArgumentException e) { return false; }
    }

    void checkDownload(String address) {
        URI uri = secure(address);
        if (imageHost.equalsIgnoreCase(uri.getHost()) || service(address)) return;
        page(address);
    }
}
