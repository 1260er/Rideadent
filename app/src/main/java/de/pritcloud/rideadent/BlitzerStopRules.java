package de.pritcloud.rideadent;

/** Pure stop-monitor decisions: no Android state or background side effects. */
final class BlitzerStopRules {
    private BlitzerStopRules() {}

    enum Action { CANCEL_RESTARTED, CONFIRMED, UNKNOWN, RETRY, NOT_CONFIRMED }

    static boolean reconcile(boolean shouldBeRunning, boolean lastStartWasSent) {
        return shouldBeRunning != lastStartWasSent;
    }

    static Action evaluate(boolean shouldBeRunning, Boolean notificationPresent,
                           int retriesDone) {
        if (shouldBeRunning) return Action.CANCEL_RESTARTED;
        if (notificationPresent == null) return Action.UNKNOWN;
        if (!notificationPresent) return Action.CONFIRMED;
        return retriesDone < 2 ? Action.RETRY : Action.NOT_CONFIRMED;
    }
}
