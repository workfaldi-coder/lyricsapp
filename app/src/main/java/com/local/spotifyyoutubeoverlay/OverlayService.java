package com.local.spotifyyoutubeoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    public static final String ACTION_START = "com.local.spotifyyoutubeoverlay.START";
    public static final String ACTION_STOP = "com.local.spotifyyoutubeoverlay.STOP";
    public static final String ACTION_REFRESH_STYLE = "com.local.spotifyyoutubeoverlay.REFRESH_STYLE";
    public static final String ACTION_TOGGLE_CLICKTHROUGH = "com.local.spotifyyoutubeoverlay.TOGGLE_CLICKTHROUGH";
    public static final String ACTION_RESET_POS = "com.local.spotifyyoutubeoverlay.RESET_POS";

    private static final String CHANNEL_ID = "lyrics_overlay_channel";
    private static final int NOTIF_ID = 1001;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private OutlinedLyricsView lyricsView;
    private MediaSessionManager mediaSessionManager;
    private ComponentName listenerComponent;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private boolean hasReceivedAnyMedia = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!OverlayState.overlayRunning) return;
            updateOverlayText();
            uiHandler.postDelayed(this, 100);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        OverlayState.load(this);
        createNotificationChannel();

        Notification notif = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notif);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this, MediaMonitorService.class);

        if (Settings.canDrawOverlays(this)) {
            createOverlayWindow();
            OverlayState.overlayRunning = true;
            uiHandler.post(tickRunnable);
        } else {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_TOGGLE_CLICKTHROUGH.equals(action)) {
                OverlayState.clickThrough = !OverlayState.clickThrough;
                OverlayState.save(this);
                applyWindowFlagsAndStyle();
                updateNotification();
            } else if (ACTION_REFRESH_STYLE.equals(action)) {
                applyWindowFlagsAndStyle();
                updateNotification();
            } else if (ACTION_RESET_POS.equals(action)) {
                resetPosition();
            }
        }
        return START_STICKY;
    }

    private void createOverlayWindow() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int heightPx = (int) (135 * dm.density);
        int defaultY = Math.max(60, dm.heightPixels - heightPx - (int) (96 * dm.density));
        int savedY = OverlayState.getSavedY(this, defaultY);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (OverlayState.clickThrough) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                heightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 0;
        layoutParams.y = savedY;

        lyricsView = new OutlinedLyricsView(this);
        lyricsView.setDisplayText("Play Spotify, YT Music, or YouTube (CC on)  |  Drag to move");
        lyricsView.setOnTouchListener(new View.OnTouchListener() {
            private int initialY;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (OverlayState.clickThrough) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = layoutParams.y;
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int newY = initialY + (int) (event.getRawY() - initialTouchY);
                        layoutParams.y = Math.max(0, newY);
                        windowManager.updateViewLayout(lyricsView, layoutParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        OverlayState.saveY(OverlayService.this, layoutParams.y);
                        return true;
                    default:
                        return false;
                }
            }
        });

        windowManager.addView(lyricsView, layoutParams);
    }

    private void applyWindowFlagsAndStyle() {
        if (lyricsView == null || layoutParams == null || windowManager == null) return;
        lyricsView.applyStyle(OverlayState.textColor, OverlayState.textSizeSp, OverlayState.showDarkPill);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (OverlayState.clickThrough) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        layoutParams.flags = flags;
        try {
            windowManager.updateViewLayout(lyricsView, layoutParams);
        } catch (Exception ignored) {}
    }

    private void resetPosition() {
        if (lyricsView == null || layoutParams == null || windowManager == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int heightPx = (int) (135 * dm.density);
        layoutParams.y = Math.max(60, dm.heightPixels - heightPx - (int) (96 * dm.density));
        OverlayState.saveY(this, layoutParams.y);
        try {
            windowManager.updateViewLayout(lyricsView, layoutParams);
        } catch (Exception ignored) {}
    }

    private void updateOverlayText() {
        if (lyricsView == null) return;

        MediaController musicController = null;
        boolean youtubePlaying = false;

        try {
            if (mediaSessionManager != null) {
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComponent);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        PlaybackState ps = mc.getPlaybackState();
                        if (ps == null || ps.getState() != PlaybackState.STATE_PLAYING) {
                            continue;
                        }
                        String pkg = mc.getPackageName();
                        if (pkg == null) continue;
                        if (pkg.equals("com.google.android.youtube") || pkg.equals("app.revanced.android.youtube")) {
                            youtubePlaying = true;
                        } else if (pkg.equals("com.spotify.music")
                                || pkg.equals("com.spotify.lite")
                                || pkg.equals("com.google.android.apps.youtube.music")) {
                            musicController = mc;
                        }
                    }
                }
            }
        } catch (SecurityException ignored) {
            // Notification listener permission not granted yet
        }

        String ytText;
        long ytAgeMs;
        synchronized (OverlayState.LOCK) {
            ytText = OverlayState.ytCaptionText;
            ytAgeMs = System.currentTimeMillis() - OverlayState.ytCaptionUpdatedAtMs;
        }

        boolean ytActive = (youtubePlaying && ytAgeMs < 15000L) || (!ytText.isEmpty() && ytAgeMs < 5000L);
        boolean musicActive = (musicController != null);

        int mode = OverlayState.sourceMode;
        boolean useYt = false;
        boolean useMusic = false;

        if (mode == OverlayState.SOURCE_YOUTUBE_ONLY) {
            useYt = ytActive;
        } else if (mode == OverlayState.SOURCE_SPOTIFY_ONLY) {
            useMusic = musicActive;
        } else {
            if (ytActive) {
                useYt = true;
            } else if (musicActive) {
                useMusic = true;
            }
        }

        if (useYt) {
            hasReceivedAnyMedia = true;
            lyricsView.setDisplayText(ytText);
            return;
        }

        if (useMusic) {
            hasReceivedAnyMedia = true;
            handleMusicController(musicController);
            return;
        }

        if (hasReceivedAnyMedia) {
            lyricsView.setDisplayText("");
        }
    }

    private void handleMusicController(MediaController mc) {
        MediaMetadata meta = mc.getMetadata();
        PlaybackState state = mc.getPlaybackState();
        if (meta == null || state == null) return;

        String track = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (track == null || track.trim().isEmpty()) return;
        if (artist == null) artist = "";

        final String cleanTrack = track.trim();
        final String cleanArtist = artist.trim();
        final String trackKey = cleanTrack + "|||" + cleanArtist;

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
            bgExecutor.execute(new Runnable() {
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

        String displayLine = "";
        for (int i = 0; i < linesCopy.size(); i++) {
            OverlayState.LyricLine line = linesCopy.get(i);
            if (currentMs >= line.timeMs) {
                displayLine = line.text;
            } else {
                break;
            }
        }
        lyricsView.setDisplayText(displayLine);
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Lyrics & Subtitle Overlay",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Keeps the transparent lyrics and subtitle overlay running.");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggleCtIntent = new Intent(this, OverlayService.class).setAction(ACTION_TOGGLE_CLICKTHROUGH);
        PendingIntent toggleCtPi = PendingIntent.getService(
                this, 1, toggleCtIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String ctLabel = OverlayState.clickThrough ? "Unlock Drag" : "Click-Through";

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle("Lyrics & Subtitle Overlay Active")
                .setContentText(OverlayState.clickThrough
                        ? "Click-through ON (tap Unlock Drag to move)"
                        : "Drag the lyrics on screen or tap to customize")
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, ctLabel, toggleCtPi).build())
                .addAction(new Notification.Action.Builder(null, "Stop", stopPi).build())
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification());
        }
    }

    @Override
    public void onDestroy() {
        OverlayState.overlayRunning = false;
        uiHandler.removeCallbacks(tickRunnable);
        bgExecutor.shutdownNow();
        if (lyricsView != null && windowManager != null) {
            try {
                windowManager.removeView(lyricsView);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
