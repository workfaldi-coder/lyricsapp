package com.local.spotifyyoutubeoverlay;

import android.accessibilityservice.AccessibilityService;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class YouTubeCaptionService extends AccessibilityService {
    private static final Pattern TIMESTAMP_ONLY = Pattern.compile("^\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*/\\s*\\d{1,2}:\\d{2}(?::\\d{2})?)?$");

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!OverlayState.overlayRunning) {
            return;
        }
        CharSequence pkg = event != null ? event.getPackageName() : null;
        if (pkg != null) {
            String p = pkg.toString();
            if (!p.equals("com.google.android.youtube") && !p.equals("app.revanced.android.youtube")) {
                return;
            }
        }

        String found = findCaptionAcrossWindows();
        if (found != null) {
            OverlayState.updateYouTubeCaption(found);
        }
    }

    private String findCaptionAcrossWindows() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo win : windows) {
                AccessibilityNodeInfo root = win.getRoot();
                if (root == null) continue;
                CharSequence pkg = root.getPackageName();
                if (pkg != null) {
                    String p = pkg.toString();
                    if (p.equals("com.google.android.youtube") || p.equals("app.revanced.android.youtube")) {
                        String caption = extractCaptionFromNodeTree(root);
                        if (caption != null) {
                            return caption;
                        }
                    }
                }
            }
        }

        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null && activeRoot.getPackageName() != null) {
            String p = activeRoot.getPackageName().toString();
            if (p.equals("com.google.android.youtube") || p.equals("app.revanced.android.youtube")) {
                return extractCaptionFromNodeTree(activeRoot);
            }
        }
        return null;
    }

    private String extractCaptionFromNodeTree(AccessibilityNodeInfo root) {
        List<String> lines = new ArrayList<>();
        boolean sawSubtitleContainer = traverse(root, lines, false);
        if (!lines.isEmpty()) {
            return TextUtils.join("\n", lines);
        }
        if (sawSubtitleContainer) {
            return "";
        }
        return null;
    }

    private boolean traverse(AccessibilityNodeInfo node, List<String> outLines, boolean insideSubtitle) {
        if (node == null) return false;

        String resId = node.getViewIdResourceName();
        String cls = node.getClassName() != null ? node.getClassName().toString().toLowerCase(Locale.US) : "";
        boolean isSubNode = insideSubtitle;

        if (resId != null) {
            String lowerId = resId.toLowerCase(Locale.US);
            if (lowerId.contains("subtitle") || lowerId.contains("caption")) {
                isSubNode = true;
            }
        }
        if (cls.contains("subtitle") || cls.contains("caption")) {
            isSubNode = true;
        }

        boolean sawContainer = isSubNode;

        if (isSubNode) {
            CharSequence txt = node.getText();
            if (txt == null || txt.length() == 0) {
                txt = node.getContentDescription();
            }
            if (txt != null) {
                String cleaned = txt.toString().trim();
                if (!cleaned.isEmpty() && !isUiNoise(cleaned)) {
                    if (!outLines.contains(cleaned)) {
                        outLines.add(cleaned);
                    }
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (traverse(child, outLines, isSubNode)) {
                    sawContainer = true;
                }
            }
        }
        return sawContainer;
    }

    private boolean isUiNoise(String text) {
        if (TIMESTAMP_ONLY.matcher(text).matches()) return true;
        String low = text.toLowerCase(Locale.US);
        return low.equals("cc")
                || low.equals("captions")
                || low.equals("subtitles")
                || low.equals("turn off captions")
                || low.equals("turn on captions");
    }

    @Override
    public void onInterrupt() {
        // No-op
    }
}
