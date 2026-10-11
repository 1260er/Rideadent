package de.pritcloud.rideadent;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class Prefs {
    private static final int MAX_HISTORY_ENTRIES = 30;
    private static final String KEY_SMS_TEXT = "sms_reply_text";
    private static final String DEFAULT_SMS_TEXT =
            "Ich fahre gerade Motorrad, ich rufe zurück.";
    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("test", Context.MODE_PRIVATE);
    }

    static Set<String> chosen(Context c, String type) {
        return new HashSet<>(get(c).getStringSet("choose_" + type, Set.of()));
    }

    static Set<String> active(Context c) {
        return new HashSet<>(get(c).getStringSet("connected", Set.of()));
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
            return false;
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
        Set<String> devices = active(c);
        if (on) devices.add(address); else devices.remove(address);
        get(c).edit().putStringSet("connected", devices).apply();
    }
}
