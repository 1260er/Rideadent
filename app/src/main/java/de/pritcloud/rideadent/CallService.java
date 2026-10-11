package de.pritcloud.rideadent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telephony.SmsManager;

public class CallService extends CallScreeningService {
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
        Prefs.note(this, "Anruf geprüft: " + reason);

        // Die Abweisung muss innerhalb von 5 Sekunden erfolgen. Insbesondere
        // darf sie NICHT vom SMS-Versand oder von dessen Wiederholsperre abhängen.
        CallResponse response = reject
                ? new CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build()
                : new CallResponse.Builder().build();
        respondToCall(call, response);
        Prefs.note(this, reject ? "Anrufabweisung angefordert" : "Anruf zugelassen");

        if (!reject) return;
        if (number == null || number.isBlank()) {
            Prefs.note(this, "Anruf abgewiesen; keine Rufnummer für SMS");
            return;
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Prefs.note(this, "Anruf abgewiesen; SEND_SMS-Berechtigung fehlt");
            return;
        }
        long now = System.currentTimeMillis();
        String key = "last_sms_" + number;
        long previous = Prefs.get(this).getLong(key, 0L);
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
            // Rückgabe bedeutet nur, dass der Versand angestoßen wurde.
            String message = Prefs.smsText(this);
            // Keep single-part SMS unchanged; use Android's segmentation for long messages.
            java.util.ArrayList<String> parts = sms.divideMessage(message);
            if (parts.size() > 1) {
                sms.sendMultipartTextMessage(number, null, parts, null, null);
            } else {
                sms.sendTextMessage(number, null, message, null, null);
            }
            Prefs.get(this).edit().putLong(key, now).apply();
            Prefs.note(this, "SMS angestoßen; Zustellung nicht bestätigt");
        } catch (Exception ex) {
            Prefs.note(this, "SMS-Fehler: " + ex.getClass().getSimpleName());
        }
    }
}
