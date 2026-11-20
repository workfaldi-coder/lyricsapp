package com.local.spotifyyoutubeoverlay;

import android.service.notification.NotificationListenerService;

public class MediaMonitorService extends NotificationListenerService {
    // Registered to grant MediaSessionManager.getActiveSessions() access
    // for reading live Spotify and YouTube Music track info & exact timestamps.
}
