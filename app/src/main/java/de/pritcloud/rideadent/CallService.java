package de.pritcloud.rideadent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telephony.SmsManager;

public class CallService extends CallScreeningService {
    private static final Object SMS_LOCK = new Object();
    @Override
    public void onScreenCall(Call.Details call) {
        if (call.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
            return;
        }

        String number = call.getHandle() == null
                ? null : call.getHandle().getSchemeSpecificPart();
        boolean enabled = Prefs.get(this).getBoolean("reply_enabled", false);
        boolean connected = Prefs.connected(this, "sms");
        boolean runtimeEnabled = Prefs.runtimeEnabled(this);
        boolean reject = Logic.shouldRejectCall(
                runtimeEnabled, enabled, connected);

        String reason;
        if (!runtimeEnabled) reason = "Automatik nach Neustart noch nicht aktiviert";
        else if (!enabled) reason = "Anrufautomatik deaktiviert";
        else if (!connected) reason = "kein ausgewähltes Anruf-Bluetooth erkannt";
        else reason = "Anrufautomatik aktiv";
        // Do not perform diagnostic file writes before Android receives its response.
        // Die Abweisung muss innerhalb von 5 Sekunden erfolgen. Insbesondere
        // darf sie NICHT vom SMS-Versand oder von dessen Wiederholsperre abhängen.
        CallResponse response = reject
                ? new CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build()
                : new CallResponse.Builder().build();
        respondToCall(call, response);
        Prefs.note(this, "Anruf geprüft: " + reason);
        Prefs.note(this, reject ? "Anrufabweisung angefordert" : "Anruf zugelassen");
        // Add-on only: an error in the callback list must never affect SMS handling.
        // The call-screening response has already been delivered to Android.
        if (reject) {
            try {
                CallbackNotifications.rejected(this, call.getHandle());
            } catch (RuntimeException ignored) {
                Prefs.note(this, "Rückruf-Benachrichtigung nicht verfügbar");
            }
        }

        if (!reject) return;
        // Only the optional SMS reply is restricted; call rejection and the
        // callback notification were already handled independently above.
        String target = SmsSafety.destination(number);
        if (target == null) {
            Prefs.note(this, "Anruf abgewiesen; SMS-Zielnummer nicht zulässig");
            return;
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Prefs.note(this, "Anruf abgewiesen; SEND_SMS-Berechtigung fehlt");
            return;
        }
        // Serialize only SMS attempts: two close incoming calls must not bypass
        // the ten-minute per-number cooldown. There is no global SMS limit.
        synchronized (SMS_LOCK) {
            android.content.SharedPreferences prefs = Prefs.get(this);
            long now = System.currentTimeMillis();
            String key = "last_sms_" + target;
            // Preserve the dev.41 cooldown even for raw stored caller formats.
            long previous = Math.max(prefs.getLong(key, 0L),
                    number == null ? 0L : prefs.getLong("last_sms_" + number, 0L));
            // Check the previous 00-format key when the new key uses +.
            if (target.startsWith("+")) {
                previous = Math.max(previous, prefs.getLong(
                        "last_sms_00" + target.substring(1), 0L));
            }
            if (!Logic.smsCooldownElapsed(previous, now)) {
                Prefs.note(this, "SMS gesperrt (10 Minuten); Anrufabweisung bleibt aktiv");
                return;
            }
            try {
                SmsManager sms = getSystemService(SmsManager.class);
                if (sms == null) {
                    Prefs.note(this, "SMS-Dienst nicht verfügbar");
                    return;
                }
                String message = Prefs.smsText(this);
                java.util.ArrayList<String> parts = sms.divideMessage(message);
                if (parts.size() > 1) {
                    sms.sendMultipartTextMessage(target, null, parts, null, null);
                } else {
                    sms.sendTextMessage(target, null, message, null, null);
                }
                // A successful API return means submitted, NOT delivered.
                // Persist before leaving the critical section to reduce
                // duplicate replies after a near-simultaneous second call.
                boolean saved = prefs.edit().putLong(key, now).commit();
                Prefs.note(this, saved
                        ? "SMS angestoßen; Zustellung nicht bestätigt"
                        : "SMS angestoßen; Wiederholsperre nicht speicherbar");
            } catch (Exception ex) {
                Prefs.note(this, "SMS-Fehler: " + ex.getClass().getSimpleName());
            }
        }
    }
}
