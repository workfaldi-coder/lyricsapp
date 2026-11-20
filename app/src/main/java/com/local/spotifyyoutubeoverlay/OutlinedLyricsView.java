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
import android.util.TypedValue;
import android.view.View;

public class OutlinedLyricsView extends View {
    private final TextPaint strokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint fillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private String currentText = "";
    private int fillColor = OverlayState.COLOR_GREEN;
    private float textSizeSp = 22f;
    private boolean showDarkPill = false;

    public OutlinedLyricsView(Context context) {
        super(context);
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

        strokePaint.setTypeface(bold);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(Color.BLACK);

        fillPaint.setTypeface(bold);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);

        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(Color.argb(185, 16, 16, 16));

        applyStyle(OverlayState.textColor, OverlayState.textSizeSp, OverlayState.showDarkPill);
    }

    public void setDisplayText(String text) {
        String safe = text != null ? text : "";
        if (!safe.equals(currentText)) {
            currentText = safe;
            invalidate();
        }
    }

    public void applyStyle(int color, float sizeSp, boolean darkPill) {
        this.fillColor = color;
        this.textSizeSp = sizeSp;
        this.showDarkPill = darkPill;

        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, sizeSp, getResources().getDisplayMetrics()
        );
        float strokePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5.0f, getResources().getDisplayMetrics()
        );
        strokePaint.setTextSize(px);
        strokePaint.setStrokeWidth(strokePx);
        fillPaint.setTextSize(px);
        fillPaint.setColor(fillColor);
        invalidate();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentText.isEmpty()) {
            return;
        }

        int padH = (int) dp(18);
        int maxTextWidth = Math.max(100, getWidth() - padH * 2);

        StaticLayout strokeLayout = StaticLayout.Builder
                .obtain(currentText, 0, currentText.length(), strokePaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(dp(2), 1.0f)
                .setIncludePad(false)
                .build();

        StaticLayout fillLayout = StaticLayout.Builder
                .obtain(currentText, 0, currentText.length(), fillPaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(dp(2), 1.0f)
                .setIncludePad(false)
                .build();

        float contentH = strokeLayout.getHeight();
        float startY = Math.max(dp(10), (getHeight() - contentH) / 2f);

        if (showDarkPill) {
            float maxLineW = 0f;
            for (int i = 0; i < strokeLayout.getLineCount(); i++) {
                maxLineW = Math.max(maxLineW, strokeLayout.getLineWidth(i));
            }
            float pillPadX = dp(16);
            float pillPadY = dp(8);
            float pillW = Math.min(getWidth() - dp(12), maxLineW + pillPadX * 2f);
            float left = (getWidth() - pillW) / 2f;
            float top = Math.max(dp(4), startY - pillPadY);
            float bottom = Math.min(getHeight() - dp(4), startY + contentH + pillPadY);
            pillRect.set(left, top, left + pillW, bottom);
            float radius = dp(14);
            canvas.drawRoundRect(pillRect, radius, radius, pillPaint);
        }

        canvas.save();
        canvas.translate(padH, startY);
        strokeLayout.draw(canvas);
        fillLayout.draw(canvas);
        canvas.restore();
    }
}
