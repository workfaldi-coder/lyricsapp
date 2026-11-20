package com.local.spotifyyoutubeoverlay;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;

public class LyricsDreamService extends DreamService {
    private AmoledLyricsView amoledView;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (amoledView == null) return;
            MediaSyncHelper.Snapshot snap = MediaSyncHelper.pollMusic(LyricsDreamService.this);
            if (snap.musicPlaying) {
                amoledView.updateContent(snap.prevLine, snap.currentLine, snap.nextLine, snap.trackTitle, snap.trackArtist);
                uiHandler.postDelayed(this, 120);
            } else {
                amoledView.updateContent("", "", "", "Play Spotify or YouTube Music", "");
                uiHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        OverlayState.load(this);
        setInteractive(true);
        setFullscreen(true);
        setScreenBright(false);

        amoledView = new AmoledLyricsView(this);
        amoledView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        setContentView(amoledView);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        uiHandler.removeCallbacks(tickRunnable);
        uiHandler.post(tickRunnable);
    }

    @Override
    public void onDreamingStopped() {
        uiHandler.removeCallbacks(tickRunnable);
        super.onDreamingStopped();
    }
}
