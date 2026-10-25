package de.pritcloud.rideadent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Short, bounded post-stop check. No persistent watchdog after driving ends. */
final class BlitzerStopMonitor {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final long FIRST_CHECK_MS = 5_000L;
    private static final long RETRY_CHECK_MS = 10_000L;
    private static int generation;

    private BlitzerStopMonitor() {}

    // Calls to Blitzer.command() serialize with checks on this class monitor.
    static synchronized void cancel() {
        generation++;
        HANDLER.removeCallbacksAndMessages(null);
    }

    static synchronized void arm(Context context) {
        cancel();
        final int expected = generation;
        Context app = context.getApplicationContext();
        HANDLER.postDelayed(() -> check(app, expected, 0), FIRST_CHECK_MS);
    }

    private static boolean wanted(Context c) {
        return Logic.shouldWatchBlitzer(
                Prefs.runtimeEnabled(c),
                Prefs.get(c).getBoolean("blitzer_enabled", false),
                Prefs.connected(c, "blitzer"));
    }

    private static synchronized void check(Context c, int expected, int retriesDone) {
        if (generation != expected) return;
        BlitzerStopRules.Action action = BlitzerStopRules.evaluate(
                wanted(c), BlitzerListener.blitzerNotificationPresent(), retriesDone);
        switch (action) {
            case CANCEL_RESTARTED:
                Prefs.note(c, c.getString(R.string.blitzer_stop_cancelled));
                cancel();
                return;
            case CONFIRMED:
                Prefs.note(c, c.getString(R.string.blitzer_stop_confirmed));
                cancel();
                return;
            case UNKNOWN:
                Prefs.note(c, c.getString(R.string.blitzer_stop_unknown));
                cancel();
                return;
            case NOT_CONFIRMED:
                Prefs.note(c, c.getString(R.string.blitzer_stop_failed));
                cancel();
                return;
            case RETRY:
                // All STARTs use the same lock and cancel pending checks before
                // broadcasting START. Never retry an obsolete STOP after a restart.
                if (!Blitzer.sendStopRetry(c)) {
                    Prefs.note(c, c.getString(R.string.blitzer_stop_failed));
                    cancel();
                    return;
                }
                Prefs.note(c, c.getString(R.string.blitzer_stop_retry, retriesDone + 1));
                HANDLER.postDelayed(() -> check(c, expected, retriesDone + 1),
                        RETRY_CHECK_MS);
        }
    }
}
