package de.pritcloud.rideadent;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Read-only notification source for the foreground watchdog.
 * Null means that Android has not connected our listener, or status is unavailable.
 */
public final class BlitzerListener extends NotificationListenerService {
    private static final String BLITZER_PACKAGE = "de.blitzer.plus";
    private static volatile BlitzerListener connectedInstance;

    @Override
    public void onListenerConnected() {
        connectedInstance = this;
        Prefs.note(this, "Blitzer-Benachrichtigungszugriff verbunden");
        signalNotificationChange();
    }

    @Override
    public void onListenerDisconnected() {
        connectedInstance = null;
        Prefs.note(this, "Blitzer-Benachrichtigungszugriff getrennt");
        signalNotificationChange();
    }

    @Override
    public void onDestroy() {
        if (connectedInstance == this) {
            connectedInstance = null;
            signalNotificationChange();
        }
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        if (notification != null
                && BLITZER_PACKAGE.equals(notification.getPackageName())) {
            signalNotificationChange();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) {
        if (notification != null
                && BLITZER_PACKAGE.equals(notification.getPackageName())) {
            signalNotificationChange();
        }
    }

    // This key is an event signal only, never a cached running indicator.
    // The Activity rechecks the Android notification listener before showing green.
    private void signalNotificationChange() {
        getSharedPreferences("service_state", MODE_PRIVATE)
                .edit()
                .putLong("blitzer_notification_event",
                        android.os.SystemClock.elapsedRealtimeNanos())
                .apply();
    }

    /**
     * A visible Blitzer.de notification is the user-selected running indicator.
     * It is not an Android guarantee that the application's warning engine works.
     */
    static Boolean blitzerNotificationPresent() {
        BlitzerListener listener = connectedInstance;
        if (listener == null) return null;
        try {
            StatusBarNotification[] active = listener.getActiveNotifications();
            if (active == null) return null;
            for (StatusBarNotification notification : active) {
                if (BLITZER_PACKAGE.equals(notification.getPackageName())) return true;
            }
            return false;
        } catch (RuntimeException exception) {
            Prefs.note(listener, "Blitzer-Status nicht lesbar: "
                    + exception.getClass().getSimpleName());
            return null;
        }
    }
}
