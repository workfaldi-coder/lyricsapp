package com.local.spotifyyoutubeoverlay;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private TextView tvOverlayPerm;
    private TextView tvNotifPerm;
    private TextView tvAccessPerm;
    private Button btnToggleOverlay;
    private TextView tvOffsetValue;
    private TextView tvSourceValue;
    private Switch swDarkPill;
    private Switch swClickThrough;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OverlayState.load(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }

    private GradientDrawable roundedBg(int fillColor, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#121212"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Spotify & YouTube Overlay");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Transparent floating lyrics for Spotify & YT Music + live YouTube CC subtitles. Zero API keys.");
        subtitle.setTextColor(Color.parseColor("#AAAAAA"));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        // Main Start / Stop Button
        btnToggleOverlay = new Button(this);
        btnToggleOverlay.setAllCaps(false);
        btnToggleOverlay.setTextSize(16);
        btnToggleOverlay.setTypeface(Typeface.DEFAULT_BOLD);
        btnToggleOverlay.setTextColor(Color.BLACK);
        btnToggleOverlay.setPadding(dp(16), dp(14), dp(16), dp(14));
        btnToggleOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleOverlayService();
            }
        });
        root.addView(btnToggleOverlay, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Permissions Card
        LinearLayout permCard = createCard(root, "1. Permissions Setup");

        tvOverlayPerm = addPermRow(permCard, "Display Over Other Apps (Required)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });

        tvNotifPerm = addPermRow(permCard, "Notification Access (For Spotify & YT Music)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });

        tvAccessPerm = addPermRow(permCard, "Accessibility Service (For YouTube CC)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        // Source Mode Card
        LinearLayout sourceCard = createCard(root, "2. Active Source");
        tvSourceValue = new TextView(this);
        tvSourceValue.setTextColor(Color.parseColor("#1DB954"));
        tvSourceValue.setTextSize(13);
        tvSourceValue.setPadding(0, 0, 0, dp(10));
        sourceCard.addView(tvSourceValue);

        LinearLayout srcRow = createButtonRow(sourceCard);
        addSmallButton(srcRow, "Auto", new View.OnClickListener() {
            @Override public void onClick(View v) { setSource(OverlayState.SOURCE_AUTO); }
        });
        addSmallButton(srcRow, "YouTube", new View.OnClickListener() {
            @Override public void onClick(View v) { setSource(OverlayState.SOURCE_YOUTUBE_ONLY); }
        });
        addSmallButton(srcRow, "Spotify", new View.OnClickListener() {
            @Override public void onClick(View v) { setSource(OverlayState.SOURCE_SPOTIFY_ONLY); }
        });

        // Appearance Card
        LinearLayout styleCard = createCard(root, "3. Appearance & Behavior");

        TextView lblColor = sectionSubLabel("Text Color");
        styleCard.addView(lblColor);
        LinearLayout colorRow = createButtonRow(styleCard);
        addColorChip(colorRow, "Green", OverlayState.COLOR_GREEN);
        addColorChip(colorRow, "White", OverlayState.COLOR_WHITE);
        addColorChip(colorRow, "Yellow", OverlayState.COLOR_YELLOW);
        addColorChip(colorRow, "Cyan", OverlayState.COLOR_CYAN);

        TextView lblSize = sectionSubLabel("Text Size");
        lblSize.setPadding(0, dp(12), 0, dp(6));
        styleCard.addView(lblSize);
        LinearLayout sizeRow = createButtonRow(styleCard);
        addSmallButton(sizeRow, "Small", new View.OnClickListener() {
            @Override public void onClick(View v) { setTextSize(17f); }
        });
        addSmallButton(sizeRow, "Medium", new View.OnClickListener() {
            @Override public void onClick(View v) { setTextSize(22f); }
        });
        addSmallButton(sizeRow, "Large", new View.OnClickListener() {
            @Override public void onClick(View v) { setTextSize(28f); }
        });

        swDarkPill = addSwitchRow(styleCard, "Subtle Dark Backdrop", OverlayState.showDarkPill,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        OverlayState.showDarkPill = isChecked;
                        OverlayState.save(MainActivity.this);
                        sendServiceAction(OverlayService.ACTION_REFRESH_STYLE);
                    }
                });

        swClickThrough = addSwitchRow(styleCard, "Click-Through Mode (Pass Touches)", OverlayState.clickThrough,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        OverlayState.clickThrough = isChecked;
                        OverlayState.save(MainActivity.this);
                        sendServiceAction(OverlayService.ACTION_REFRESH_STYLE);
                    }
                });

        // Sync & Position Card
        LinearLayout syncCard = createCard(root, "4. Timing Sync & Position");
        tvOffsetValue = new TextView(this);
        tvOffsetValue.setTextColor(Color.parseColor("#CCCCCC"));
        tvOffsetValue.setTextSize(13);
        tvOffsetValue.setPadding(0, 0, 0, dp(8));
        syncCard.addView(tvOffsetValue);

        LinearLayout syncRow = createButtonRow(syncCard);
        addSmallButton(syncRow, "-0.5s", new View.OnClickListener() {
            @Override public void onClick(View v) { adjustOffset(-500); }
        });
        addSmallButton(syncRow, "Reset", new View.OnClickListener() {
            @Override public void onClick(View v) {
                OverlayState.spotifyOffsetMs = 0L;
                OverlayState.save(MainActivity.this);
                refreshStatus();
            }
        });
        addSmallButton(syncRow, "+0.5s", new View.OnClickListener() {
            @Override public void onClick(View v) { adjustOffset(500); }
        });

        Button btnResetPos = new Button(this);
        btnResetPos.setText("Reset Overlay Position to Bottom");
        btnResetPos.setAllCaps(false);
        btnResetPos.setTextColor(Color.WHITE);
        btnResetPos.setBackground(roundedBg(Color.parseColor("#2A2A2A"), 10));
        LinearLayout.LayoutParams rpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rpLp.topMargin = dp(10);
        btnResetPos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendServiceAction(OverlayService.ACTION_RESET_POS);
                Toast.makeText(MainActivity.this, "Overlay position reset", Toast.LENGTH_SHORT).show();
            }
        });
        syncCard.addView(btnResetPos, rpLp);

        TextView tip = new TextView(this);
        tip.setText("Tip: Spotify & YouTube Music lyrics work even when minimized. For YouTube videos, turn on CC and use normal, split-screen, or Picture-in-Picture mode.");
        tip.setTextColor(Color.parseColor("#888888"));
        tip.setTextSize(12);
        tip.setPadding(0, dp(14), 0, 0);
        root.addView(tip);

        setContentView(scroll);
    }

    private LinearLayout createCard(LinearLayout parent, String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(Color.parseColor("#1E1E1E"), 14));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(14);
        parent.addView(card, lp);

        TextView tv = new TextView(this);
        tv.setText(heading);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, dp(10));
        card.addView(tv);
        return card;
    }

    private TextView sectionSubLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#BBBBBB"));
        tv.setTextSize(13);
        tv.setPadding(0, 0, 0, dp(6));
        return tv;
    }

    private TextView addPermRow(LinearLayout card, String label, View.OnClickListener onGrantClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(13);
        textCol.addView(tvLabel);

        TextView tvStatus = new TextView(this);
        tvStatus.setTextSize(12);
        textCol.addView(tvStatus);
        row.addView(textCol, colLp);

        Button btn = new Button(this);
        btn.setText("Open");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setBackground(roundedBg(Color.parseColor("#333333"), 8));
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        btn.setOnClickListener(onGrantClick);
        row.addView(btn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
        ));

        card.addView(row);
        return tvStatus;
    }

    private LinearLayout createButtonRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private void addSmallButton(LinearLayout row, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setBackground(roundedBg(Color.parseColor("#2E2E2E"), 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setOnClickListener(listener);
        row.addView(b, lp);
    }

    private void addColorChip(LinearLayout row, String label, final int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(roundedBg(color, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OverlayState.textColor = color;
                OverlayState.save(MainActivity.this);
                sendServiceAction(OverlayService.ACTION_REFRESH_STYLE);
            }
        });
        row.addView(b, lp);
    }

    private Switch addSwitchRow(LinearLayout parent, String label, boolean initial,
                                CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(2));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);

        parent.addView(row);
        return sw;
    }

    private void setSource(int mode) {
        OverlayState.sourceMode = mode;
        OverlayState.save(this);
        refreshStatus();
    }

    private void setTextSize(float sp) {
        OverlayState.textSizeSp = sp;
        OverlayState.save(this);
        sendServiceAction(OverlayService.ACTION_REFRESH_STYLE);
    }

    private void adjustOffset(long deltaMs) {
        OverlayState.spotifyOffsetMs += deltaMs;
        OverlayState.save(this);
        refreshStatus();
    }

    private void toggleOverlayService() {
        if (OverlayState.overlayRunning) {
            stopService(new Intent(this, OverlayService.class));
            OverlayState.overlayRunning = false;
            refreshStatus();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps' first", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        Intent intent = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_START);
        startForegroundService(intent);
        OverlayState.overlayRunning = true;
        refreshStatus();
    }

    private void sendServiceAction(String action) {
        if (!OverlayState.overlayRunning) return;
        Intent intent = new Intent(this, OverlayService.class).setAction(action);
        startForegroundService(intent);
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        ComponentName cn = new ComponentName(this, MediaMonitorService.class);
        return flat.contains(cn.flattenToString()) || flat.contains(getPackageName());
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName cn = new ComponentName(this, YouTubeCaptionService.class);
        return enabled.contains(cn.flattenToString()) || enabled.contains(getPackageName());
    }

    private void refreshStatus() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        boolean hasNotif = isNotificationListenerEnabled();
        boolean hasAccess = isAccessibilityServiceEnabled();

        tvOverlayPerm.setText(hasOverlay ? "Granted" : "Not granted — tap Open");
        tvOverlayPerm.setTextColor(hasOverlay ? Color.parseColor("#1DB954") : Color.parseColor("#FF6B6B"));

        tvNotifPerm.setText(hasNotif ? "Granted" : "Not granted — required for Spotify");
        tvNotifPerm.setTextColor(hasNotif ? Color.parseColor("#1DB954") : Color.parseColor("#FFB84D"));

        tvAccessPerm.setText(hasAccess ? "Granted" : "Optional — enable for YouTube CC");
        tvAccessPerm.setTextColor(hasAccess ? Color.parseColor("#1DB954") : Color.parseColor("#AAAAAA"));

        if (OverlayState.overlayRunning) {
            btnToggleOverlay.setText("Stop Floating Overlay");
            btnToggleOverlay.setBackground(roundedBg(Color.parseColor("#E55353"), 12));
            btnToggleOverlay.setTextColor(Color.WHITE);
        } else {
            btnToggleOverlay.setText("Start Floating Overlay");
            btnToggleOverlay.setBackground(roundedBg(Color.parseColor("#1DB954"), 12));
            btnToggleOverlay.setTextColor(Color.BLACK);
        }

        String srcText = "Current: Auto (YouTube + Spotify/YT Music)";
        if (OverlayState.sourceMode == OverlayState.SOURCE_YOUTUBE_ONLY) {
            srcText = "Current: YouTube Subtitles Only";
        } else if (OverlayState.sourceMode == OverlayState.SOURCE_SPOTIFY_ONLY) {
            srcText = "Current: Spotify / YT Music Lyrics Only";
        }
        tvSourceValue.setText(srcText);

        tvOffsetValue.setText(String.format(Locale.US, "Spotify Sync Offset: %+.1fs", OverlayState.spotifyOffsetMs / 1000.0));
        swDarkPill.setChecked(OverlayState.showDarkPill);
        swClickThrough.setChecked(OverlayState.clickThrough);
    }
}
