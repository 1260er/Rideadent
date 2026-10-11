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
    }

    @Override
    public void onListenerDisconnected() {
        connectedInstance = null;
        Prefs.note(this, "Blitzer-Benachrichtigungszugriff getrennt");
    }

    @Override
    public void onDestroy() {
        if (connectedInstance == this) connectedInstance = null;
        super.onDestroy();
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
