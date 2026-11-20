package com.local.spotifyyoutubeoverlay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsFetcher {
    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\](.*)");

    private LyricsFetcher() {}

    public static List<OverlayState.LyricLine> fetchSyncedLyrics(String track, String artist) {
        List<OverlayState.LyricLine> parsed = null;
        try {
            String exactUrl = "https://lrclib.net/api/get?track_name="
                    + URLEncoder.encode(track, "UTF-8")
                    + "&artist_name="
                    + URLEncoder.encode(artist, "UTF-8");
            String body = httpGet(exactUrl);
            if (body != null && !body.isEmpty()) {
                JSONObject obj = new JSONObject(body);
                String lrc = obj.optString("syncedLyrics", "");
                if (!lrc.isEmpty() && !"null".equals(lrc)) {
                    parsed = parseLrc(lrc);
                }
            }
        } catch (Exception ignored) {}

        if (parsed == null || parsed.isEmpty()) {
            try {
                String query = URLEncoder.encode(track + " " + artist, "UTF-8");
                String searchUrl = "https://lrclib.net/api/search?q=" + query;
                String body = httpGet(searchUrl);
                if (body != null && !body.isEmpty()) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.optJSONObject(i);
                        if (item == null) continue;
                        String lrc = item.optString("syncedLyrics", "");
                        if (!lrc.isEmpty() && !"null".equals(lrc)) {
                            parsed = parseLrc(lrc);
                            if (!parsed.isEmpty()) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (parsed == null || parsed.isEmpty()) {
            List<OverlayState.LyricLine> fallback = new ArrayList<>();
            fallback.add(new OverlayState.LyricLine(0, "No synced lyrics found for: " + track));
            return fallback;
        }
        return parsed;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "SpotifyYouTubeOverlayAndroid/3.0");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
            );
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static List<OverlayState.LyricLine> parseLrc(String lrcText) {
        List<OverlayState.LyricLine> result = new ArrayList<>();
        if (lrcText == null) return result;
        String[] lines = lrcText.split("\\r?\\n");
        for (String line : lines) {
            Matcher m = LRC_LINE.matcher(line);
            if (m.find()) {
                try {
                    int mins = Integer.parseInt(m.group(1));
                    double secs = Double.parseDouble(m.group(2));
                    String text = m.group(3) != null ? m.group(3).trim() : "";
                    int ms = (int) Math.round((mins * 60.0 + secs) * 1000.0);
                    result.add(new OverlayState.LyricLine(ms, text));
                } catch (Exception ignored) {}
            }
        }
        Collections.sort(result);
        return result;
    }
}
