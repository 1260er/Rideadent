package de.pritcloud.rideadent;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class Prefs {
    private static final int MAX_HISTORY_ENTRIES = 30;
    private static final String KEY_SMS_TEXT = "sms_reply_text";
    private static final String DEFAULT_SMS_TEXT =
            "Ich kann gerade nicht telefonieren. Ich melde mich später.";
    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("test", Context.MODE_PRIVATE);
    }

    static Set<String> chosen(Context c, String type) {
        return new HashSet<>(get(c).getStringSet("choose_" + type, Set.of()));
    }

    // Raw cache is used ONLY to detect transitions while Bluetooth powers off.
    static Set<String> recordedActive(Context c) {
        return new HashSet<>(get(c).getStringSet("connected", Set.of()));
    }

    // -1=unavailable, otherwise an Android BluetoothAdapter STATE_* value.
    private static int bluetoothRadioState(Context c) {
        try {
            if (c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) return -1;
            BluetoothManager manager = c.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            return adapter == null ? -1 : adapter.getState();
        } catch (RuntimeException unavailable) {
            return -1;
        }
    }

    static Set<String> active(Context c) {
        Set<String> recorded = recordedActive(c);
        if (recorded.isEmpty()) return recorded;
        int radio = bluetoothRadioState(c);
        // A definite power-off invalidates saved ACL events. This is also a
        // fallback when Android did not deliver the OFF broadcast to us.
        if (radio == BluetoothAdapter.STATE_OFF
                || radio == BluetoothAdapter.STATE_TURNING_OFF) {
            get(c).edit().remove("connected").apply();
        }
        // Unknown radio state is fail-closed, but keeps the cache so a brief
        // permission/service outage cannot permanently erase a real connection.
        return Logic.effectiveActive(recorded, radio == BluetoothAdapter.STATE_ON);
    }

    static boolean connected(Context c, String type) {
        return Logic.shouldRun(active(c), chosen(c, type));
    }

    static boolean runtimeEnabled(Context c) {
        return get(c).getBoolean("runtime_enabled", true);
    }

    static void setRuntimeEnabled(Context c, boolean enabled) {
        get(c).edit().putBoolean("runtime_enabled", enabled).commit();
    }

    static String smsText(Context c) {
        return get(c).getString(KEY_SMS_TEXT, DEFAULT_SMS_TEXT);
    }

    static boolean setSmsText(Context c, String text) {
        String value = text == null ? "" : text.trim();

        if (value.isEmpty()) {
            // No custom SMS: use DEFAULT_SMS_TEXT via smsText()'s fallback.
            get(c).edit().remove(KEY_SMS_TEXT).apply();
            return true;
        }

        get(c)
                .edit()
                .putString(KEY_SMS_TEXT, value)
                .apply();

        return true;
    }

    static void clearActive(Context c) {
        get(c).edit().remove("connected").commit();
    }

    // Synchronisiert: Bluetooth-, Telefon- und Benachrichtigungsereignisse koennen
    // kurz hintereinander eintreffen. Es werden maximal 30 Statuszeilen gehalten.
    static synchronized void note(Context c, String message) {
        SharedPreferences prefs = get(c);
        String when = new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY)
                .format(new Date());
        String old = prefs.getString("event_history", "");
        String[] entries = (when + " – " + message
                + (old.isEmpty() ? "" : "\n" + old)).split("\n");
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < Math.min(entries.length, MAX_HISTORY_ENTRIES); index++) {
            if (index > 0) result.append('\n');
            result.append(entries[index]);
        }
        prefs.edit().putString("last", message)
                .putString("event_history", result.toString()).apply();
    }

    static void clearHistory(Context c) {
        get(c).edit().remove("event_history").apply();
    }

    static void state(Context c, String address, boolean on) {
        Set<String> devices = recordedActive(c);
        if (on) devices.add(address); else devices.remove(address);
        get(c).edit().putStringSet("connected", devices).apply();
    }
}
