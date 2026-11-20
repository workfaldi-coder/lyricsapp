package com.local.spotifyyoutubeoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public final class OverlayState {
    public static final int SOURCE_AUTO = 0;
    public static final int SOURCE_YOUTUBE_ONLY = 1;
    public static final int SOURCE_SPOTIFY_ONLY = 2;

    public static final int COLOR_GREEN = Color.rgb(29, 185, 84);
    public static final int COLOR_WHITE = Color.WHITE;
    public static final int COLOR_YELLOW = Color.rgb(255, 224, 64);
    public static final int COLOR_CYAN = Color.rgb(0, 229, 255);

    private static final String PREFS = "overlay_prefs";

    public static final class LyricLine implements Comparable<LyricLine> {
        public final int timeMs;
        public final String text;

        public LyricLine(int timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }

        @Override
        public int compareTo(LyricLine other) {
            return Integer.compare(this.timeMs, other.timeMs);
        }
    }

    public static final Object LOCK = new Object();

    // User settings
    public static volatile int sourceMode = SOURCE_AUTO;
    public static volatile int textColor = COLOR_GREEN;
    public static volatile float textSizeSp = 22f;
    public static volatile boolean useMonospaceFont = false;
    public static volatile boolean showThreeLines = false;
    public static volatile boolean showDarkPill = false;
    public static volatile boolean clickThrough = false;
    public static volatile long spotifyOffsetMs = 0L;
    public static volatile boolean amoledAutoOnScreenOff = false;
    public static volatile float amoledBrightness = 0.12f;

    // Runtime flags
    public static volatile boolean overlayRunning = false;
    public static volatile boolean amoledActive = false;
    public static volatile long amoledLastDismissedMs = 0L;
    public static volatile boolean isMusicActivelyPlaying = false;

    // Live YouTube caption state (updated by YouTubeCaptionService)
    public static String ytCaptionText = "";
    public static long ytCaptionUpdatedAtMs = 0L;

    // Cached Spotify / YT Music synced lyrics
    public static String currentTrackKey = "";
    public static List<LyricLine> currentLyrics = new ArrayList<>();

    private OverlayState() {}

    public static void load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sourceMode = sp.getInt("sourceMode", SOURCE_AUTO);
        textColor = sp.getInt("textColor", COLOR_GREEN);
        textSizeSp = sp.getFloat("textSizeSp", 22f);
        useMonospaceFont = sp.getBoolean("useMonospaceFont", false);
        showThreeLines = sp.getBoolean("showThreeLines", false);
        showDarkPill = sp.getBoolean("showDarkPill", false);
        clickThrough = sp.getBoolean("clickThrough", false);
        spotifyOffsetMs = sp.getLong("spotifyOffsetMs", 0L);
        amoledAutoOnScreenOff = sp.getBoolean("amoledAutoOnScreenOff", false);
        amoledBrightness = sp.getFloat("amoledBrightness", 0.12f);
    }

    public static void save(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
            .putInt("sourceMode", sourceMode)
            .putInt("textColor", textColor)
            .putFloat("textSizeSp", textSizeSp)
            .putBoolean("useMonospaceFont", useMonospaceFont)
            .putBoolean("showThreeLines", showThreeLines)
            .putBoolean("showDarkPill", showDarkPill)
            .putBoolean("clickThrough", clickThrough)
            .putLong("spotifyOffsetMs", spotifyOffsetMs)
            .putBoolean("amoledAutoOnScreenOff", amoledAutoOnScreenOff)
            .putFloat("amoledBrightness", amoledBrightness)
            .apply();
    }

    public static int getSavedY(Context ctx, int fallbackY) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("overlayY", fallbackY);
    }

    public static void saveY(Context ctx, int y) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("overlayY", y).apply();
    }

    public static void updateYouTubeCaption(String text) {
        synchronized (LOCK) {
            ytCaptionText = text != null ? text : "";
            ytCaptionUpdatedAtMs = System.currentTimeMillis();
        }
    }
}
