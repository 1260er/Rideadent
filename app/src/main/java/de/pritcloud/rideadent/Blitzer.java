package de.pritcloud.rideadent;
import android.content.Context;
import android.content.Intent;
final class Blitzer {
    private Blitzer() {}

    /** All START and STOP operations serialize with the bounded stop monitor. */
    static void command(Context context, boolean start) {
        synchronized (BlitzerStopMonitor.class) {
            if (start) BlitzerStopMonitor.cancel();
            if (!send(context, start)) return;
            boolean stored = Prefs.get(context).edit()
                    .putBoolean("blitzer_start_sent", start).commit();
            if (!stored) Prefs.note(context, "Blitzer-Kommandostatus nicht gespeichert");
            Prefs.note(context, "Blitzer-Befehl gesendet: " + (start ? "Start" : "Stop"));
            if (!start) BlitzerStopMonitor.arm(context);
        }
    }

    /** Retry does not re-arm the verification window. */
    static boolean sendStopRetry(Context context) {
        return send(context, false);
    }

    private static boolean send(Context context, boolean start) {
        Intent intent = new Intent(start ? "de.blitzer.APP_MODE" : "de.blitzer.KILL_APP");
        intent.setClassName("de.blitzer.plus", "de.blitzer.service.BlitzerBroadcastReceiver");
        if (start) intent.putExtra("mode", "START");
        try {
            context.sendBroadcast(intent);
            return true;
        } catch (Exception ex) {
            Prefs.note(context, "Blitzer-Befehl fehlgeschlagen: "
                    + ex.getClass().getSimpleName());
            return false;
        }
    }
}
