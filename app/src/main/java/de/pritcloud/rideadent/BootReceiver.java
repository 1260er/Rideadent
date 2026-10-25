package de.pritcloud.rideadent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Aktiviert die Automatik nach einem Neustart optional wieder und entfernt
 * veraltete Laufzeitdaten aus der vorherigen Gerätesitzung.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            boolean autoStart =
                    Prefs.get(context).getBoolean("auto_start", false);

            Prefs.clearActive(context);
            // A previous-session START must not stop a user-launched app after reboot.
            Prefs.clearBlitzerStartSent(context);
            ServiceState.stopped(context);
            Prefs.setRuntimeEnabled(
                    context,
                    runtimeAfterEvent(action, autoStart, false));

            Prefs.note(
                    context,
                    autoStart
                            ? "Neustart: Automatik automatisch aktiviert"
                            : "Neustart: Automatik wartet auf App-Start");

            BlitzerWatchService.sync(context);
            CallbackNotifications.scheduleRefresh(context);
            return;
        }

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            boolean previousRuntime = Prefs.runtimeEnabled(context);

            ServiceState.stopped(context);
            Prefs.setRuntimeEnabled(
                    context,
                    runtimeAfterEvent(
                            action,
                            Prefs.get(context).getBoolean("auto_start", false),
                            previousRuntime));

            Prefs.note(
                    context,
                    "App aktualisiert: Fahrmodus-Dienst wird neu geprüft");

            BlitzerWatchService.sync(context);
            CallbackNotifications.scheduleRefresh(context);
        }
    }

    static boolean runtimeAfterEvent(
            String action,
            boolean autoStart,
            boolean previousRuntime) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return autoStart;
        }

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return previousRuntime;
        }

        return previousRuntime;
    }
}
