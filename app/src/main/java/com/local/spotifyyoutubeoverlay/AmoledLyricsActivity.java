package com.local.spotifyyoutubeoverlay;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public class AmoledLyricsActivity extends Activity {
    private AmoledLyricsView amoledView;
    private GestureDetector gestureDetector;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long pausedSinceMs = 0L;
    private float dragStartY = 0f;
    private float dragStartBrightness = 0.12f;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;

            MediaSyncHelper.Snapshot snap = MediaSyncHelper.pollMusic(AmoledLyricsActivity.this);
            if (snap.musicPlaying) {
                pausedSinceMs = 0L;
                amoledView.updateContent(snap.prevLine, snap.currentLine, snap.nextLine, snap.trackTitle, snap.trackArtist);
                uiHandler.postDelayed(this, 120);
            } else {
                long now = System.currentTimeMillis();
                if (pausedSinceMs == 0L) {
                    pausedSinceMs = now;
                }
                amoledView.updateContent("", "Playback paused", "Screen will sleep shortly", "", "");

                // Auto-sleep if music stays paused for 30 seconds so battery isn't wasted
                if (now - pausedSinceMs >= 30000L) {
                    OverlayState.amoledLastDismissedMs = now;
                    finish();
                    return;
                }
                uiHandler.postDelayed(this, 600);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OverlayState.load(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        amoledView = new AmoledLyricsView(this);
        setContentView(amoledView);

        applyBrightness(OverlayState.amoledBrightness);
        hideSystemBars();

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                OverlayState.amoledLastDismissedMs = System.currentTimeMillis();
                finish();
                return true;
            }
        });

        amoledView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dragStartY = event.getRawY();
                        dragStartBrightness = OverlayState.amoledBrightness;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaY = dragStartY - event.getRawY();
                        if (Math.abs(deltaY) > 20f) {
                            float nextB = dragStartBrightness + (deltaY / 900f);
                            nextB = Math.max(0.02f, Math.min(0.65f, nextB));
                            applyBrightness(nextB);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        OverlayState.save(AmoledLyricsActivity.this);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void applyBrightness(float level) {
        OverlayState.amoledBrightness = level;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = level;
        getWindow().setAttributes(lp);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        OverlayState.amoledActive = true;
        pausedSinceMs = 0L;
        hideSystemBars();
        uiHandler.removeCallbacks(tickRunnable);
        uiHandler.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        OverlayState.amoledActive = false;
        uiHandler.removeCallbacks(tickRunnable);
    }

    @Override
    protected void onDestroy() {
        OverlayState.amoledActive = false;
        uiHandler.removeCallbacks(tickRunnable);
        super.onDestroy();
    }
}
