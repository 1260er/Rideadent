package de.pritcloud.rideadent;

import java.util.Set;

public final class Logic {
    private Logic() {}

    public static boolean shouldRun(Set<String> connected, Set<String> selected) {
        for (String address : connected) {
            if (selected.contains(address)) return true;
        }
        return false;
    }

    // Only a definitely powered-on Bluetooth radio may activate a
    // previously recorded device. An unknown radio state is fail-closed.
    static Set<String> effectiveActive(Set<String> recorded, boolean radioOn) {
        return radioOn ? recorded : Set.of();
    }

    public static boolean shouldRejectCall(boolean runtimeEnabled,
                                           boolean enabled,
                                           boolean connected) {
        return runtimeEnabled && enabled && connected;
    }

    public static boolean smsCooldownElapsed(long previous, long now) {
        return previous == 0L || now - previous >= 600_000L;
    }

    public static boolean shouldWatchBlitzer(boolean runtimeEnabled,
                                             boolean enabled,
                                             boolean connected) {
        return runtimeEnabled && enabled && connected;
    }

    public static boolean shouldRunDriveService(boolean runtimeEnabled,
                                                boolean blitzerEnabled,
                                                boolean blitzerConnected,
                                                boolean callEnabled,
                                                boolean callConnected) {
        return shouldWatchBlitzer(
                runtimeEnabled, blitzerEnabled, blitzerConnected)
                || shouldRejectCall(
                runtimeEnabled, callEnabled, callConnected);
    }

    // Send a start/stop intent only when the selected, connected Blitzer
    // automation really crosses the enabled/disabled boundary.
    public static boolean shouldSendBlitzerToggleCommand(
            boolean runtimeEnabled, boolean connected,
            boolean previouslyEnabled, boolean nowEnabled) {
        return shouldWatchBlitzer(runtimeEnabled, previouslyEnabled, connected)
                != shouldWatchBlitzer(runtimeEnabled, nowEnabled, connected);
    }

    public static boolean shouldRestartMissingNotification(
            boolean runtimeEnabled,
            boolean enabled,
            boolean connected,
            boolean notificationPresent,
            long nowElapsed,
            long retryAfterElapsed) {
        return shouldWatchBlitzer(runtimeEnabled, enabled, connected)
                && !notificationPresent
                && nowElapsed >= retryAfterElapsed;
    }
}
