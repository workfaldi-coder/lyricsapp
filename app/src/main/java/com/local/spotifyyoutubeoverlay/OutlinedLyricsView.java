package com.local.spotifyyoutubeoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

public class OutlinedLyricsView extends View {
    private final TextPaint strokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint fillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final TextPaint sideStrokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sideFillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private String prevText = "";
    private String currentText = "";
    private String nextText = "";
    private boolean threeLineRender = false;

    private int fillColor = OverlayState.COLOR_GREEN;
    private float textSizeSp = 22f;
    private boolean useMonospace = false;
    private boolean showDarkPill = false;

    public OutlinedLyricsView(Context context) {
        super(context);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(Color.BLACK);

        fillPaint.setStyle(Paint.Style.FILL);

        sideStrokePaint.setStyle(Paint.Style.STROKE);
        sideStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        sideStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        sideStrokePaint.setColor(Color.argb(190, 0, 0, 0));

        sideFillPaint.setStyle(Paint.Style.FILL);
        sideFillPaint.setColor(Color.argb(165, 225, 225, 225));

        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(Color.argb(185, 16, 16, 16));

        applyStyle(
                OverlayState.textColor,
                OverlayState.textSizeSp,
                OverlayState.useMonospaceFont,
                OverlayState.showDarkPill
        );
    }

    public void setDisplayText(String text) {
        setLyricsLines("", text, "", false);
    }

    public void setLyricsLines(String prev, String current, String next, boolean threeLines) {
        String safePrev = (threeLines && prev != null) ? prev : "";
        String safeCur = current != null ? current : "";
        String safeNext = (threeLines && next != null) ? next : "";

        if (threeLines != threeLineRender
                || !safePrev.equals(prevText)
                || !safeCur.equals(currentText)
                || !safeNext.equals(nextText)) {
            this.threeLineRender = threeLines;
            this.prevText = safePrev;
            this.currentText = safeCur;
            this.nextText = safeNext;
            invalidate();
        }
    }

    public void applyStyle(int color, float sizeSp, boolean mono, boolean darkPill) {
        this.fillColor = color;
        this.textSizeSp = sizeSp;
        this.useMonospace = mono;
        this.showDarkPill = darkPill;

        Typeface family = mono ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        Typeface boldTypeface = Typeface.create(family, Typeface.BOLD);
        Typeface normalTypeface = Typeface.create(family, Typeface.NORMAL);

        strokePaint.setTypeface(boldTypeface);
        fillPaint.setTypeface(boldTypeface);
        sideStrokePaint.setTypeface(normalTypeface);
        sideFillPaint.setTypeface(normalTypeface);

        float mainPx = sp(sizeSp);
        float sidePx = sp(Math.max(12f, sizeSp * 0.72f));
        float mainStrokePx = dp(5.0f);
        float sideStrokePx = dp(3.5f);

        strokePaint.setTextSize(mainPx);
        strokePaint.setStrokeWidth(mainStrokePx);
        fillPaint.setTextSize(mainPx);
        fillPaint.setColor(fillColor);

        sideStrokePaint.setTextSize(sidePx);
        sideStrokePaint.setStrokeWidth(sideStrokePx);
        sideFillPaint.setTextSize(sidePx);

        invalidate();
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

    private StaticLayout buildLayout(String text, TextPaint paint, int maxWidth, int maxLines) {
        StaticLayout.Builder b = StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(dp(2), 1.0f)
                .setIncludePad(false);
        if (maxLines > 0) {
            b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
        }
        return b.build();
    }

    private float maxWidthOf(StaticLayout layout) {
        if (layout == null) return 0f;
        float w = 0f;
        for (int i = 0; i < layout.getLineCount(); i++) {
            w = Math.max(w, layout.getLineWidth(i));
        }
        return w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (prevText.isEmpty() && currentText.isEmpty() && nextText.isEmpty()) {
            return;
        }

        int padH = (int) dp(18);
        int maxTextWidth = Math.max(100, getWidth() - padH * 2);

        if (!threeLineRender) {
            if (currentText.isEmpty()) return;

            StaticLayout strokeLayout = buildLayout(currentText, strokePaint, maxTextWidth, 0);
            StaticLayout fillLayout = buildLayout(currentText, fillPaint, maxTextWidth, 0);

            float contentH = strokeLayout.getHeight();
            float startY = Math.max(dp(10), (getHeight() - contentH) / 2f);

            if (showDarkPill) {
                drawBackdropPill(canvas, maxWidthOf(strokeLayout), startY, startY + contentH);
            }

            canvas.save();
            canvas.translate(padH, startY);
            strokeLayout.draw(canvas);
            fillLayout.draw(canvas);
            canvas.restore();
            return;
        }

        // 3-line mode: active lyric anchored in the center/second slot
        StaticLayout activeStroke = currentText.isEmpty() ? null : buildLayout(currentText, strokePaint, maxTextWidth, 2);
        StaticLayout activeFill = currentText.isEmpty() ? null : buildLayout(currentText, fillPaint, maxTextWidth, 2);

        StaticLayout prevStroke = prevText.isEmpty() ? null : buildLayout(prevText, sideStrokePaint, maxTextWidth, 1);
        StaticLayout prevFill = prevText.isEmpty() ? null : buildLayout(prevText, sideFillPaint, maxTextWidth, 1);

        StaticLayout nextStroke = nextText.isEmpty() ? null : buildLayout(nextText, sideStrokePaint, maxTextWidth, 1);
        StaticLayout nextFill = nextText.isEmpty() ? null : buildLayout(nextText, sideFillPaint, maxTextWidth, 1);

        float gap = dp(7);
        float centerH = activeStroke != null ? activeStroke.getHeight() : sp(textSizeSp);
        float centerTop = (getHeight() - centerH) / 2f;

        float prevTop = prevStroke != null ? (centerTop - gap - prevStroke.getHeight()) : centerTop;
        float nextTop = centerTop + centerH + gap;

        float contentTop = prevStroke != null ? Math.max(dp(4), prevTop) : centerTop;
        float contentBottom = nextStroke != null
                ? Math.min(getHeight() - dp(4), nextTop + nextStroke.getHeight())
                : (centerTop + centerH);

        if (showDarkPill) {
            float maxW = Math.max(maxWidthOf(activeStroke),
                    Math.max(maxWidthOf(prevStroke), maxWidthOf(nextStroke)));
            if (maxW > 0f) {
                drawBackdropPill(canvas, maxW, contentTop, contentBottom);
            }
        }

        if (prevStroke != null && prevFill != null) {
            canvas.save();
            canvas.translate(padH, Math.max(dp(4), prevTop));
            prevStroke.draw(canvas);
            prevFill.draw(canvas);
            canvas.restore();
        }

        if (activeStroke != null && activeFill != null) {
            canvas.save();
            canvas.translate(padH, centerTop);
            activeStroke.draw(canvas);
            activeFill.draw(canvas);
            canvas.restore();
        }

        if (nextStroke != null && nextFill != null) {
            canvas.save();
            canvas.translate(padH, nextTop);
            nextStroke.draw(canvas);
            nextFill.draw(canvas);
            canvas.restore();
        }
    }

    private void drawBackdropPill(Canvas canvas, float maxLineW, float contentTop, float contentBottom) {
        float pillPadX = dp(16);
        float pillPadY = dp(8);
        float pillW = Math.min(getWidth() - dp(12), maxLineW + pillPadX * 2f);
        float left = (getWidth() - pillW) / 2f;
        float top = Math.max(dp(4), contentTop - pillPadY);
        float bottom = Math.min(getHeight() - dp(4), contentBottom + pillPadY);
        pillRect.set(left, top, left + pillW, bottom);
        float radius = dp(14);
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint);
    }
}
