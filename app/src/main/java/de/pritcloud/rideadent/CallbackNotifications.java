package de.pritcloud.rideadent;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.format.DateFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional, local callback queue. No interaction with call rejection or SMS logic. */
final class CallbackNotifications {
    static final String ENABLED_KEY = "callback_notifications_enabled";
    static final String EXTRA_ID = "callback_entry_id";
    private static final String PREF = "callback_notifications";
    private static final String DATA = "pending_calls";
    private static final String NEXT_ID = "next_id";
    private static final String CHANNEL = "rideadent_callbacks";
    private static final String GROUP = "rideadent_callbacks_group";
    private static final String TAG = "rideadent_callbacks";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    static final class Entry {
        final int id;
        final String phone;
        final String name;
        final long time;
        Entry(int id, String phone, String name, long time) {
            this.id = id;
            this.phone = phone;
            this.name = name;
            this.time = time;
        }
        String label() { return name == null || name.isBlank() ? phone : name; }
    }

    private CallbackNotifications() {}

    static boolean enabled(Context c) {
        // Existing installs without the new preference also start enabled.
        return Prefs.get(c).getBoolean(ENABLED_KEY, true);
    }

    private static synchronized List<Entry> entries(Context c) {
        List<Entry> found = new ArrayList<>();
        String value = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(DATA, "[]");
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                int id = item.optInt("id", 0);
                String phone = item.optString("phone", "");
                if (id <= 0 || CallbackRules.dialNumber(phone) == null) continue;
                String name = item.optString("name", "");
                found.add(new Entry(id, phone, name, item.optLong("time", 0L)));
            }
        } catch (JSONException ignored) {
            // A malformed local file cannot create an outgoing call.
        }
        return found;
    }

    private static boolean persist(Context c, List<Entry> list, int nextId) {
        JSONArray array = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject item = new JSONObject();
                item.put("id", e.id);
                item.put("phone", e.phone);
                item.put("name", e.name);
                item.put("time", e.time);
                array.put(item);
            }
        } catch (JSONException ignored) {
            return false;
        }
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(DATA, array.toString()).putInt(NEXT_ID, nextId).commit();
    }

    static synchronized Entry find(Context c, int id) {
        for (Entry e : entries(c)) if (e.id == id) return e;
        return null;
    }

    static void rejected(Context c, Uri handle) {
        if (!enabled(c) || handle == null || !"tel".equalsIgnoreCase(handle.getScheme())) return;
        String phone = CallbackRules.dialNumber(handle.getSchemeSpecificPart());
        if (phone == null) return; // No private/unknown/SIP/USSD callback targets.
        // Android's screening response has ALREADY been sent. Persist the
        // callback immediately, so a process kill cannot discard a queued,
        // never-started background task. Errors remain isolated from SMS.
        Context app = c.getApplicationContext();
        final int id;
        try {
            id = add(app, phone);
        } catch (RuntimeException ignored) {
            Prefs.note(app, "Rückruf konnte nicht gespeichert werden");
            return;
        }
        if (id <= 0) return;
        // Contact lookups and notification posting stay off the call thread.
        WORK.execute(() -> {
            try {
                refresh(app);
                String name = lookupName(app, phone);
                if (name != null && !name.isBlank()) {
                    updateName(app, id, name);
                    refresh(app);
                }
            } catch (RuntimeException ignored) {
                Prefs.note(app, "Rückruf-Benachrichtigung nicht verfügbar");
            }
        });
    }

    // UI/boot restores need not do JSON scans or post notifications on their
    // main / broadcast thread. The stored queue is already durable.
    static void scheduleRefresh(Context c) {
        Context app = c.getApplicationContext();
        WORK.execute(() -> {
            try {
                refresh(app);
            } catch (RuntimeException ignored) {
                Prefs.note(app, "Rückruf-Aktualisierung nicht verfügbar");
            }
        });
    }

    private static synchronized int add(Context c, String phone) {
        if (!enabled(c)) return -1;
        List<Entry> list = consolidatedEntries(c);
        int next = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(NEXT_ID, 1);
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            Entry previous = list.get(i);
            if (CallbackRules.sameNumber(previous.phone, phone)) {
                // Preserve the notification/PendingIntent ID for this number.
                // Move it to the newest position with the last call time.
                list.remove(i);
                list.add(new Entry(previous.id, phone, previous.name, now));
                return persist(c, list, next) ? previous.id : -1;
            }
        }
        if (next <= 0 || next == Integer.MAX_VALUE) return -1;
        list.add(new Entry(next, phone, "", now));
        return persist(c, list, next + 1) ? next : -1;
    }

    // Fold duplicate entries saved by dev.40 in linear rather than quadratic
    // time. Keep the first callback ID and latest timestamp/name, preserving
    // one independently clickable Android PendingIntent per unique number.
    private static synchronized List<Entry> consolidatedEntries(Context c) {
        List<Entry> original = entries(c);
        Map<String, Entry> merged = new LinkedHashMap<>();
        List<Integer> obsoleteIds = new ArrayList<>();
        boolean changed = false;
        for (Entry item : original) {
            String key = CallbackRules.identity(item.phone);
            if (key == null) continue;
            Entry existing = merged.remove(key);
            if (existing == null) {
                merged.put(key, item);
                continue;
            }
            changed = true;
            Entry latest = item.time >= existing.time ? item : existing;
            String name = !latest.name.isBlank() ? latest.name
                    : !existing.name.isBlank() ? existing.name : item.name;
            merged.put(key, new Entry(existing.id, latest.phone, name,
                    Math.max(existing.time, item.time)));
            if (item.id != existing.id) obsoleteIds.add(item.id);
        }
        List<Entry> list = new ArrayList<>(merged.values());
        if (changed) {
            int next = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getInt(NEXT_ID, 1);
            if (!persist(c, list, next)) return original;
            NotificationManager manager = c.getSystemService(NotificationManager.class);
            if (manager != null) {
                for (int id : obsoleteIds) manager.cancel(TAG, id);
            }
        }
        return list;
    }

    private static String lookupName(Context c, String phone) {
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return null;
        Uri filter = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
        try (Cursor cursor = c.getContentResolver().query(filter,
                new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME },
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // Contacts are optional. The telephone number remains readable.
        }
        return null;
    }

    private static synchronized void updateName(Context c, int id, String name) {
        if (!enabled(c)) return;
        List<Entry> list = entries(c);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (e.id == id) {
                list.set(i, new Entry(id, e.phone, name, e.time));
                found = true;
                break;
            }
        }
        if (found) persist(c, list,
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(NEXT_ID, 1));
    }

    static synchronized boolean remove(Context c, int id) {
        List<Entry> list = entries(c);
        boolean removed = list.removeIf(e -> e.id == id);
        if (!removed) return false;
        int next = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(NEXT_ID, 1);
        if (!persist(c, list, next)) return false;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(TAG, id);
        refresh(c);
        return true;
    }

    static synchronized void setEnabled(Context c, boolean on) {
        Prefs.get(c).edit().putBoolean(ENABLED_KEY, on).commit();
        if (!on) {
            List<Entry> list = entries(c);
            int next = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(NEXT_ID, 1);
            if (persist(c, new ArrayList<>(), next)) {
                NotificationManager manager = c.getSystemService(NotificationManager.class);
                if (manager != null) {
                    for (Entry e : list) manager.cancel(TAG, e.id);
                    manager.cancel(TAG, 0);
                }
            }
        } else refresh(c);
    }

    private static String lastCallTime(Context c, long timestamp) {
        Date date = new Date(timestamp);
        return DateFormat.getDateFormat(c).format(date) + " "
                + DateFormat.getTimeFormat(c).format(date);
    }

    static synchronized void refresh(Context c) {
        if (!enabled(c)) return;
        // Consolidate legacy entries even when posting notifications is denied.
        List<Entry> list = consolidatedEntries(c);
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                c.getString(R.string.callback_channel), NotificationManager.IMPORTANCE_DEFAULT));
        if (list.isEmpty()) {
            manager.cancel(TAG, 0);
            return;
        }
        boolean grouped = list.size() > 1;
        Notification publicNotice = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle(c.getString(R.string.callback_private_title))
                .setVisibility(Notification.VISIBILITY_PUBLIC).build();
        for (Entry e : list) {
            Intent call = new Intent(c, CallbackActivity.class);
            call.putExtra(EXTRA_ID, e.id);
            PendingIntent click = PendingIntent.getActivity(c, e.id, call,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder item = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.sym_call_missed)
                    .setContentTitle(e.label())
                    .setContentText(c.getString(R.string.callback_last_call,
                            lastCallTime(c, e.time)))
                    .setSubText(c.getString(R.string.callback_tap_to_call))
                    .setWhen(e.time)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotice)
                    .setContentIntent(click);
            if (grouped) item.setGroup(GROUP);
            manager.notify(TAG, e.id, item.build());
        }
        if (grouped) {
            Notification.InboxStyle inbox = new Notification.InboxStyle();
            for (int i = Math.max(0, list.size() - 5); i < list.size(); i++) {
                Entry entry = list.get(i);
                inbox.addLine(entry.label() + " · " + lastCallTime(c, entry.time));
            }
            Notification summary = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.sym_call_missed)
                    .setContentTitle(c.getString(R.string.callback_group_title, list.size()))
                    .setContentText(c.getString(R.string.callback_group_hint))
                    .setStyle(inbox).setGroup(GROUP).setGroupSummary(true)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                    .setOnlyAlertOnce(true)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotice)
                    .build();
            manager.notify(TAG, 0, summary);
        } else {
            manager.cancel(TAG, 0);
        }
    }
}
