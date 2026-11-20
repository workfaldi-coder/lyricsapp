package com.local.spotifyyoutubeoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

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
    private PowerManager powerManager;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean hasReceivedAnyMedia = false;
    private boolean isScreenOn = true;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                isScreenOn = false;
                uiHandler.removeCallbacks(tickRunnable);

                if (OverlayState.amoledActive) {
                    OverlayState.amoledLastDismissedMs = System.currentTimeMillis();
                    return;
                }

                long sinceDismiss = System.currentTimeMillis() - OverlayState.amoledLastDismissedMs;
                if (OverlayState.amoledAutoOnScreenOff && sinceDismiss > 4000L) {
                    MediaSyncHelper.Snapshot snap = MediaSyncHelper.pollMusic(OverlayService.this);
                    if (snap.musicPlaying) {
                        launchAmoledScreensaver();
                    }
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                isScreenOn = true;
                if (OverlayState.overlayRunning) {
                    uiHandler.removeCallbacks(tickRunnable);
                    uiHandler.post(tickRunnable);
                }
            }
        }
    };

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!OverlayState.overlayRunning || !isScreenOn) return;

            if (OverlayState.amoledActive) {
                if (lyricsView != null && lyricsView.getVisibility() != View.GONE) {
                    lyricsView.setVisibility(View.GONE);
                }
                uiHandler.postDelayed(this, 500);
                return;
            } else if (lyricsView != null && lyricsView.getVisibility() != View.VISIBLE) {
                lyricsView.setVisibility(View.VISIBLE);
            }

            boolean active = updateOverlayText();
            uiHandler.postDelayed(this, active ? 100 : 1000);
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
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            isScreenOn = powerManager.isInteractive();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }

        if (Settings.canDrawOverlays(this)) {
            createOverlayWindow();
            OverlayState.overlayRunning = true;
            if (isScreenOn) {
                uiHandler.post(tickRunnable);
            }
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

    @SuppressWarnings("deprecation")
    private void launchAmoledScreensaver() {
        try {
            if (powerManager != null) {
                PowerManager.WakeLock wl = powerManager.newWakeLock(
                        PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "SpotifyYouTubeOverlay:AmoledWake"
                );
                wl.acquire(2500L);
            }
        } catch (Exception ignored) {}

        Intent i = new Intent(this, AmoledLyricsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private int desiredOverlayHeightPx() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int heightDp = OverlayState.showThreeLines ? 210 : 135;
        return (int) (heightDp * dm.density);
    }

    private void createOverlayWindow() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int heightPx = desiredOverlayHeightPx();
        int defaultY = Math.max(60, dm.heightPixels - heightPx - (int) (96 * dm.density));
        int savedY = Math.min(OverlayState.getSavedY(this, defaultY), Math.max(0, dm.heightPixels - heightPx));

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
        lyricsView.applyStyle(
                OverlayState.textColor,
                OverlayState.textSizeSp,
                OverlayState.useMonospaceFont,
                OverlayState.showDarkPill
        );

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int desiredH = desiredOverlayHeightPx();
        layoutParams.height = desiredH;
        layoutParams.y = Math.max(0, Math.min(layoutParams.y, Math.max(0, dm.heightPixels - desiredH)));

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (OverlayState.clickThrough) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        layoutParams.flags = flags;
        try {
            windowManager.updateViewLayout(lyricsView, layoutParams);
        } catch (Exception ignored) {}

        // Refresh displayed lyrics layout immediately when switching 1-line <-> 3-line
        updateOverlayText();
    }

    private void resetPosition() {
        if (lyricsView == null || layoutParams == null || windowManager == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int heightPx = desiredOverlayHeightPx();
        layoutParams.height = heightPx;
        layoutParams.y = Math.max(60, dm.heightPixels - heightPx - (int) (96 * dm.density));
        OverlayState.saveY(this, layoutParams.y);
        try {
            windowManager.updateViewLayout(lyricsView, layoutParams);
        } catch (Exception ignored) {}
    }

    private boolean updateOverlayText() {
        if (lyricsView == null) return false;

        MediaSyncHelper.Snapshot snap = MediaSyncHelper.pollMusic(this);

        String ytText;
        long ytAgeMs;
        synchronized (OverlayState.LOCK) {
            ytText = OverlayState.ytCaptionText;
            ytAgeMs = System.currentTimeMillis() - OverlayState.ytCaptionUpdatedAtMs;
        }

        boolean ytActive = (snap.youtubePlaying && ytAgeMs < 15000L) || (!ytText.isEmpty() && ytAgeMs < 5000L);
        boolean musicActive = snap.musicPlaying;

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
            return true;
        }

        if (useMusic) {
            hasReceivedAnyMedia = true;
            if (OverlayState.showThreeLines) {
                lyricsView.setLyricsLines(snap.prevLine, snap.currentLine, snap.nextLine, true);
            } else {
                lyricsView.setDisplayText(snap.currentLine);
            }
            return true;
        }

        if (hasReceivedAnyMedia) {
            lyricsView.setDisplayText("");
        }
        return false;
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

        Intent amoledIntent = new Intent(this, AmoledLyricsActivity.class);
        amoledIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent amoledPi = PendingIntent.getActivity(
                this, 3, amoledIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggleCtIntent = new Intent(this, OverlayService.class).setAction(ACTION_TOGGLE_CLICKTHROUGH);
        PendingIntent toggleCtPi = PendingIntent.getService(
                this, 1, toggleCtIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String ctLabel = OverlayState.clickThrough ? "Unlock" : "Pass-Thru";

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle("Lyrics & Subtitle Overlay Active")
                .setContentText(OverlayState.amoledAutoOnScreenOff
                        ? "AMOLED auto-screensaver ON  |  Tap for settings"
                        : "Tap AMOLED for blank-screen lyrics or tap to customize")
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "AMOLED", amoledPi).build())
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
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {}
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
