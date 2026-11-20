package com.local.spotifyyoutubeoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class AmoledLyricsView extends View {
    private final TextPaint clockPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint currentLyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sideLyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint trackPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Random random = new Random();

    private String clockText = "";
    private String prevLine = "";
    private String currentLine = "";
    private String nextLine = "";
    private String trackLabel = "";
    private boolean showHint = true;

    private boolean lastMono = false;
    private boolean lastThreeLines = false;
    private int lastColor = 0;
    private float lastSizeSp = 0f;

    // Burn-in protection pixel shift offsets (in px)
    private float shiftX = 0f;
    private float shiftY = 0f;
    private long lastShiftMs = 0L;
    private final long createdAtMs = System.currentTimeMillis();

    public AmoledLyricsView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);

        clockPaint.setColor(Color.argb(140, 255, 255, 255));
        clockPaint.setTextSize(sp(42));
        clockPaint.setTextAlign(Paint.Align.CENTER);

        sideLyricPaint.setColor(Color.argb(110, 210, 210, 210));

        trackPaint.setColor(Color.argb(95, 190, 190, 190));
        trackPaint.setTextSize(sp(13));
        trackPaint.setTextAlign(Paint.Align.CENTER);

        hintPaint.setColor(Color.argb(85, 160, 160, 160));
        hintPaint.setTextSize(sp(11));
        hintPaint.setTextAlign(Paint.Align.CENTER);

        applyStyleFromState();
        clockText = clockFormat.format(new Date());
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        );
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics()
        );
    }

    public boolean applyStyleFromState() {
        boolean mono = OverlayState.useMonospaceFont;
        boolean threeLines = OverlayState.showThreeLines;
        int baseColor = OverlayState.textColor;
        float sizeSp = OverlayState.textSizeSp;

        boolean changed = (mono != lastMono)
                || (threeLines != lastThreeLines)
                || (baseColor != lastColor)
                || (sizeSp != lastSizeSp);

        lastMono = mono;
        lastThreeLines = threeLines;
        lastColor = baseColor;
        lastSizeSp = sizeSp;

        Typeface family = mono ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        clockPaint.setTypeface(mono ? Typeface.MONOSPACE : Typeface.create("sans-serif-light", Typeface.NORMAL));
        currentLyricPaint.setTypeface(Typeface.create(family, Typeface.BOLD));
        sideLyricPaint.setTypeface(Typeface.create(family, Typeface.NORMAL));
        trackPaint.setTypeface(Typeface.create(family, Typeface.NORMAL));
        hintPaint.setTypeface(Typeface.create(family, Typeface.NORMAL));

        int softColor = Color.argb(225, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        currentLyricPaint.setColor(softColor);
        currentLyricPaint.setTextSize(sp(Math.max(20f, sizeSp + 2f)));
        sideLyricPaint.setTextSize(sp(Math.max(13f, sizeSp - 5f)));

        return changed;
    }

    public void updateContent(String prvLine, String curLine, String nxtLine, String track, String artist) {
        long now = System.currentTimeMillis();
        String newClock = clockFormat.format(new Date(now));
        String safePrev = prvLine != null ? prvLine : "";
        String safeCur = curLine != null ? curLine : "";
        String safeNxt = nxtLine != null ? nxtLine : "";
        String newTrack = "";
        if (track != null && !track.isEmpty()) {
            newTrack = (artist != null && !artist.isEmpty()) ? (track + "  \u2022  " + artist) : track;
        }

        boolean newShowHint = (now - createdAtMs) < 5500L;
        boolean shifted = false;

        // Burn-in protection: shift content gently every 45 seconds
        if (now - lastShiftMs >= 45000L) {
            lastShiftMs = now;
            shiftX = (random.nextFloat() * 2f - 1f) * dp(14);
            shiftY = (random.nextFloat() * 2f - 1f) * dp(32);
            shifted = true;
        }

        boolean styleChanged = applyStyleFromState();

        if (shifted
                || styleChanged
                || !newClock.equals(clockText)
                || !safePrev.equals(prevLine)
                || !safeCur.equals(currentLine)
                || !safeNxt.equals(nextLine)
                || !newTrack.equals(trackLabel)
                || newShowHint != showHint) {
            clockText = newClock;
            prevLine = safePrev;
            currentLine = safeCur;
            nextLine = safeNxt;
            trackLabel = newTrack;
            showHint = newShowHint;
            invalidate();
        }
    }

    private StaticLayout makeLayout(String text, TextPaint paint, int maxWidth, int maxLines) {
        StaticLayout.Builder b = StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(dp(3), 1.0f)
                .setIncludePad(false);
        if (maxLines > 0) {
            b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
        }
        return b.build();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Pure #000000 black - turns off AMOLED pixels completely
        canvas.drawColor(Color.BLACK);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float centerX = (w / 2f) + shiftX;
        int padH = (int) dp(28);
        int textWidth = Math.max(120, w - padH * 2);

        // 1. Subtle dimmed clock near top
        float clockY = (h * 0.18f) + (shiftY * 0.5f);
        canvas.drawText(clockText, centerX, clockY, clockPaint);

        // 2. Active lyric anchored dead-center (second line in 3-line mode)
        String displayMain = currentLine.isEmpty() ? "\u266A" : currentLine;
        StaticLayout mainLayout = makeLayout(displayMain, currentLyricPaint, textWidth, 3);
        float centerTop = ((h - mainLayout.getHeight()) / 2f) + shiftY;

        if (OverlayState.showThreeLines) {
            float gap = dp(20);

            if (!prevLine.isEmpty()) {
                StaticLayout prevLayout = makeLayout(prevLine, sideLyricPaint, textWidth, 2);
                float prevTop = centerTop - gap - prevLayout.getHeight();
                canvas.save();
                canvas.translate(padH + shiftX, prevTop);
                prevLayout.draw(canvas);
                canvas.restore();
            }

            canvas.save();
            canvas.translate(padH + shiftX, centerTop);
            mainLayout.draw(canvas);
            canvas.restore();

            if (!nextLine.isEmpty()) {
                StaticLayout nextLayout = makeLayout(nextLine, sideLyricPaint, textWidth, 2);
                float nextTop = centerTop + mainLayout.getHeight() + gap;
                canvas.save();
                canvas.translate(padH + shiftX, nextTop);
                nextLayout.draw(canvas);
                canvas.restore();
            }
        } else {
            // Single-line clean mode
            canvas.save();
            canvas.translate(padH + shiftX, centerTop);
            mainLayout.draw(canvas);
            canvas.restore();
        }

        // 3. Subtle track info near bottom
        if (!trackLabel.isEmpty()) {
            float trackY = (h * 0.86f) + (shiftY * 0.4f);
            String clipped = trackLabel.length() > 46 ? trackLabel.substring(0, 45) + "\u2026" : trackLabel;
            canvas.drawText(clipped, centerX, trackY, trackPaint);
        }

        // 4. Temporary gesture hint (auto-hides after 5.5s to stay pure AMOLED black)
        if (showHint) {
            canvas.drawText("Double-tap to exit  \u2022  Swipe up/down for brightness", w / 2f, h - dp(24), hintPaint);
        }
    }
}
