package com.local.spotifyyoutubeoverlay;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaSyncHelper {
    public static final class Snapshot {
        public boolean musicPlaying;
        public boolean youtubePlaying;
        public String trackTitle = "";
        public String trackArtist = "";
        public String prevLine = "";
        public String currentLine = "";
        public String nextLine = "";
    }

    private static final ExecutorService FETCH_EXECUTOR = Executors.newSingleThreadExecutor();

    private MediaSyncHelper() {}

    public static Snapshot pollMusic(Context ctx) {
        Snapshot snap = new Snapshot();
        MediaSessionManager msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) {
            OverlayState.isMusicActivelyPlaying = false;
            return snap;
        }

        ComponentName listener = new ComponentName(ctx, MediaMonitorService.class);
        MediaController musicController = null;

        try {
            List<MediaController> controllers = msm.getActiveSessions(listener);
            if (controllers != null) {
                for (MediaController mc : controllers) {
                    PlaybackState ps = mc.getPlaybackState();
                    if (ps == null || ps.getState() != PlaybackState.STATE_PLAYING) {
                        continue;
                    }
                    String pkg = mc.getPackageName();
                    if (pkg == null) continue;
                    if (pkg.equals("com.google.android.youtube") || pkg.equals("app.revanced.android.youtube")) {
                        snap.youtubePlaying = true;
                    } else if (pkg.equals("com.spotify.music")
                            || pkg.equals("com.spotify.lite")
                            || pkg.equals("com.google.android.apps.youtube.music")) {
                        musicController = mc;
                    }
                }
            }
        } catch (SecurityException ignored) {
            // Notification access not yet granted
        }

        if (musicController == null) {
            OverlayState.isMusicActivelyPlaying = false;
            return snap;
        }

        MediaMetadata meta = musicController.getMetadata();
        PlaybackState state = musicController.getPlaybackState();
        if (meta == null || state == null) {
            OverlayState.isMusicActivelyPlaying = false;
            return snap;
        }

        String track = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (track == null || track.trim().isEmpty()) {
            OverlayState.isMusicActivelyPlaying = false;
            return snap;
        }
        if (artist == null) artist = "";

        final String cleanTrack = track.trim();
        final String cleanArtist = artist.trim();
        final String trackKey = cleanTrack + "|||" + cleanArtist;

        snap.musicPlaying = true;
        snap.trackTitle = cleanTrack;
        snap.trackArtist = cleanArtist;
        OverlayState.isMusicActivelyPlaying = true;

        boolean needFetch = false;
        List<OverlayState.LyricLine> linesCopy;

        synchronized (OverlayState.LOCK) {
            if (!trackKey.equals(OverlayState.currentTrackKey)) {
                OverlayState.currentTrackKey = trackKey;
                OverlayState.currentLyrics = new ArrayList<>();
                OverlayState.currentLyrics.add(
                        new OverlayState.LyricLine(0, "\u266A " + cleanTrack + (cleanArtist.isEmpty() ? "" : " \u2014 " + cleanArtist) + " \u266A")
                );
                needFetch = true;
            }
            linesCopy = new ArrayList<>(OverlayState.currentLyrics);
        }

        if (needFetch) {
            FETCH_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    List<OverlayState.LyricLine> fetched = LyricsFetcher.fetchSyncedLyrics(cleanTrack, cleanArtist);
                    synchronized (OverlayState.LOCK) {
                        if (trackKey.equals(OverlayState.currentTrackKey)) {
                            OverlayState.currentLyrics = fetched;
                        }
                    }
                }
            });
        }

        long basePos = state.getPosition();
        long elapsedSinceUpdate = Math.max(0L, SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime());
        float speed = state.getPlaybackSpeed();
        if (speed <= 0f) speed = 1f;
        long currentMs = Math.max(0L, basePos + (long) (elapsedSinceUpdate * speed) + OverlayState.spotifyOffsetMs);

        int activeIdx = -1;
        for (int i = 0; i < linesCopy.size(); i++) {
            if (currentMs >= linesCopy.get(i).timeMs) {
                activeIdx = i;
            } else {
                break;
            }
        }

        if (activeIdx >= 0) {
            snap.currentLine = linesCopy.get(activeIdx).text;

            // Find nearest non-empty previous line
            for (int k = activeIdx - 1; k >= 0; k--) {
                String prevCandidate = linesCopy.get(k).text;
                if (prevCandidate != null && !prevCandidate.isEmpty()) {
                    snap.prevLine = prevCandidate;
                    break;
                }
            }

            // Find nearest non-empty next line
            for (int j = activeIdx + 1; j < linesCopy.size(); j++) {
                String nextCandidate = linesCopy.get(j).text;
                if (nextCandidate != null && !nextCandidate.isEmpty()) {
                    snap.nextLine = nextCandidate;
                    break;
                }
            }
        } else {
            // Song intro before first timestamp: show title in center and upcoming first line below
            snap.currentLine = "\u266A " + cleanTrack + " \u266A";
            for (int j = 0; j < linesCopy.size(); j++) {
                String firstCandidate = linesCopy.get(j).text;
                if (firstCandidate != null && !firstCandidate.isEmpty()) {
                    snap.nextLine = firstCandidate;
                    break;
                }
            }
        }

        return snap;
    }
}
