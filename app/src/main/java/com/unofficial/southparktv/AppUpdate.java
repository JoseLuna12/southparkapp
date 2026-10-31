package com.unofficial.southparktv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;

final class AppUpdate {
    static final String REPO = "JoseLuna12/southparkapp";
    static final long MAX_APK = 200L * 1024 * 1024;
    record Release(int code, String name, int sdk, String url, String hash, long size) { }

    static boolean assetUrl(String value) {
        try {
            URI u = new URI(value);
            return "https".equals(u.getScheme()) && "github.com".equals(u.getHost())
                    && u.getPort() == -1 && u.getUserInfo() == null && u.getQuery() == null
                    && u.getFragment() == null && u.getPath().startsWith("/" + REPO + "/releases/download/")
                    && !u.getPath().contains("..");
        } catch (Exception e) { return false; }
    }

    static Release parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        long code = o.getLong("versionCode");
        Release r = new Release((int) code, o.getString("versionName"), o.getInt("minSdk"),
                o.getString("apkUrl"), o.getString("sha256"), o.getLong("sizeBytes"));
        if (o.getInt("schemaVersion") != 1 || !"com.unofficial.southparktv".equals(o.getString("applicationId"))
                || code < 1 || code > Integer.MAX_VALUE || r.sdk < 26 || r.name.isEmpty() || r.name.length() > 80
                || r.size <= 0 || r.size > MAX_APK || !r.hash.matches("[a-fA-F0-9]{64}") || !assetUrl(r.url))
            throw new IOException("Invalid release");
        return r;
    }

    static HttpURLConnection open(String address) throws Exception {
        URL url = new URL(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || url.getPort() != -1
                    || !(host.equals("api.github.com") || host.equals("github.com") || host.equals("release-assets.githubusercontent.com")))
                throw new IOException("Invalid host");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(5000); c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "SouthParkTV-Updater");
            int status = c.getResponseCode();
            if (status == 200) return c;
            String location = c.getHeaderField("Location");
            c.disconnect();
            if (status < 300 || status > 399 || location == null) throw new IOException("HTTP " + status);
            url = new URL(url, location);
        }
        throw new IOException("Redirect limit");
    }

    static byte[] bytes(String url, int max) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            long start = System.nanoTime();
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() - start > 30_000_000_000L || out.size() + n > max) throw new IOException("Cancelled or too large");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    static Release check() throws Exception {
        JSONObject release = new JSONObject(new String(bytes("https://api.github.com/repos/" + REPO + "/releases/latest", 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8));
        if (release.getBoolean("draft") || release.getBoolean("prerelease")) throw new IOException("Not stable");
        JSONArray assets = release.getJSONArray("assets");
        String metadata = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if ("update.json".equals(a.getString("name"))) metadata = a.getString("browser_download_url");
        }
        if (metadata == null || !assetUrl(metadata)) throw new IOException("Missing metadata");
        Release r = parse(new String(bytes(metadata, 65536), java.nio.charset.StandardCharsets.UTF_8));
        boolean found = false;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (r.url.equals(a.getString("browser_download_url")) && r.size == a.getLong("size")) found = true;
        }
        if (!found) throw new IOException("Missing APK");
        return r;
    }

    @SuppressWarnings("deprecation")
    static void verify(Context context, File file, Release r) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) digest.update(b, 0, n);
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        PackageManager pm = context.getPackageManager();
        PackageInfo apk = pm.getPackageArchiveInfo(file.getPath(), PackageManager.GET_SIGNATURES);
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        if (file.length() != r.size || !hash.toString().equalsIgnoreCase(r.hash) || apk == null
                || !context.getPackageName().equals(apk.packageName) || apk.versionCode != r.code
                || apk.versionCode <= installed.versionCode || !r.name.equals(apk.versionName)
                || apk.applicationInfo == null || apk.applicationInfo.minSdkVersion != r.sdk || r.sdk > Build.VERSION.SDK_INT
                || apk.signatures == null || apk.signatures.length == 0 || !Arrays.equals(apk.signatures, installed.signatures))
            throw new IOException("APK verification failed");
    }

    static File download(Context context, Release r) throws Exception {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Storage unavailable");
        File file = new File(dir, "update.apk");
        boolean complete = false;
        try {
            if (dir.getUsableSpace() < r.size * 2 + 16 * 1024 * 1024) throw new IOException("Insufficient space");
            HttpURLConnection c = open(r.url);
            long start = System.nanoTime();
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                byte[] b = new byte[32768]; long total = 0; int n;
                while ((n = in.read(b)) != -1) {
                    total += n;
                    if (Thread.currentThread().isInterrupted() || total > r.size || System.nanoTime() - start > 600_000_000_000L)
                        throw new IOException("Cancelled or too large");
                    out.write(b, 0, n);
                }
            } finally { c.disconnect(); }
            verify(context, file, r);
            complete = true;
            return file;
        } finally { if (!complete) file.delete(); }
    }
}
