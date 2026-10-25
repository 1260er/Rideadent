package de.pritcloud.rideadent;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Fahrmodus-Foreground-Service.
 *
 * Solange mindestens eine aktive Fahrfunktion ein ausgewähltes Bluetooth-Gerät
 * nutzt, aktualisiert der Dienst alle 15 Sekunden seinen Heartbeat. Wenn die
 * Blitzer-Automatik aktiv ist, wird im selben Takt deren Benachrichtigung geprüft.
 */
public final class BlitzerWatchService extends Service {
    private static final String CHANNEL = "rideadent_drive";
    private static final int NOTIFICATION_ID = 1203;

    static final long CHECK_INTERVAL_MS = 15_000L;
    static final long RETRY_INTERVAL_MS = 45_000L;

    private static volatile boolean active;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable checkTask =
            this::checkAndReschedule;

    private int previousStatus = 2;
    private long nextRetryElapsed;

    static void sync(Context context) {
        // Reconcile START/STOP even if another automation keeps this service
        // alive, a selected device was changed, or a disconnect was missed.
        boolean blitzerRequired = blitzerWanted(context);
        if (BlitzerStopRules.reconcile(blitzerRequired,
                Prefs.blitzerStartSent(context))) {
            Blitzer.command(context, blitzerRequired);
        }
        boolean shouldRun = driveWanted(context);
        Intent service =
                new Intent(context, BlitzerWatchService.class);

        if (!shouldRun) {
            ServiceState.stopped(context);
            context.stopService(service);
            return;
        }

        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission may have been revoked while the service was active.
            ServiceState.stopped(context);
            context.stopService(service);
            Prefs.note(
                    context,
                    "Fahrmodus beendet: Bluetooth-Berechtigung fehlt");
            return;
        }

        if (active && ServiceState.isFresh(context)) {
            return;
        }

        try {
            context.startForegroundService(service);
        } catch (RuntimeException exception) {
            Prefs.note(
                    context,
                    "Fahrmodusstart von Android blockiert: "
                            + exception.getClass().getSimpleName());
            // Do not leave a Rideadent-started Blitzer session behind if
            // Android refuses to start the driving foreground service.
            if (Prefs.blitzerStartSent(context)) Blitzer.command(context, false);
        }
    }

    private static boolean driveWanted(Context context) {
        return Logic.shouldRunDriveService(
                Prefs.runtimeEnabled(context),
                Prefs.get(context).getBoolean("blitzer_enabled", false),
                Prefs.connected(context, "blitzer"),
                Prefs.get(context).getBoolean("reply_enabled", false),
                Prefs.connected(context, "sms"));
    }

    private static boolean blitzerWanted(Context context) {
        return Logic.shouldWatchBlitzer(
                Prefs.runtimeEnabled(context),
                Prefs.get(context).getBoolean("blitzer_enabled", false),
                Prefs.connected(context, "blitzer"));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager nm =
                getSystemService(NotificationManager.class);

        if (nm != null) {
            nm.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL,
                            getString(R.string.notification_channel_drive),
                            NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!driveWanted(this)) {
            // This may be a START_STICKY restart without a BT callback.
            sync(this);
            ServiceState.stopped(this);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Revalidate access on Android service restarts, not only in sync().
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            sync(this);
            ServiceState.stopped(this);
            Prefs.note(this, "Fahrmodus beendet: Bluetooth-Berechtigung fehlt");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            Intent open =
                    new Intent(this, MainActivity.class);

            PendingIntent tap =
                    PendingIntent.getActivity(
                            this,
                            0,
                            open,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE);

            Notification note =
                    new Notification.Builder(this, CHANNEL)
                            .setSmallIcon(
                                    android.R.drawable.ic_menu_compass)
                            .setContentTitle(getString(R.string.notification_drive_active))
                            .setOngoing(true)
                            .setContentIntent(tap)
                            .build();

            startForeground(
                    NOTIFICATION_ID,
                    note,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } catch (RuntimeException exception) {
            ServiceState.stopped(this);

            Prefs.note(
                    this,
                    "Fahrmodus nicht möglich: "
                            + exception.getClass().getSimpleName());
            if (Prefs.blitzerStartSent(this)) Blitzer.command(this, false);

            stopSelf();
            return START_NOT_STICKY;
        }

        ServiceState.heartbeat(this);

        if (!active) {
            active = true;

            Prefs.note(
                    this,
                    "Fahrmodus gestartet: Dienst-Heartbeat alle 15 Sekunden");

            handler.postDelayed(
                    checkTask,
                    CHECK_INTERVAL_MS);
        }

        return START_STICKY;
    }

    private void checkAndReschedule() {
        // A running service must not keep a fresh heartbeat after revocation.
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            sync(this);
            ServiceState.stopped(this);
            Prefs.note(this, "Fahrmodus beendet: Bluetooth-Berechtigung fehlt");
            stopSelf();
            return;
        }
        if (!driveWanted(this)) {
            sync(this);
            ServiceState.stopped(this);

            Prefs.note(
                    this,
                    "Fahrmodus beendet: kein ausgewähltes Bluetooth mehr aktiv");

            stopSelf();
            return;
        }

        ServiceState.heartbeat(this);

        if (!blitzerWanted(this)) {
            // Blitzer may have become unwanted while the call service continues.
            sync(this);
            previousStatus = 2;
            nextRetryElapsed = 0L;

            handler.postDelayed(
                    checkTask,
                    CHECK_INTERVAL_MS);
            return;
        }

        Boolean present =
                BlitzerListener.blitzerNotificationPresent();

        long now =
                SystemClock.elapsedRealtime();

        int status =
                present == null
                        ? -1
                        : (present ? 1 : 0);

        if (status != previousStatus) {
            if (status == -1) {
                Prefs.note(
                        this,
                        "Blitzer-Status unbekannt: Benachrichtigungszugriff prüfen");
            } else if (status == 1) {
                Prefs.note(
                        this,
                        "Blitzer-Benachrichtigung vorhanden");
            } else {
                Prefs.note(
                        this,
                        "Blitzer-Benachrichtigung fehlt");
            }

            previousStatus = status;
        }

        if (status == 1) {
            nextRetryElapsed = 0L;
        } else if (status == 0) {
            if (Logic.shouldRestartMissingNotification(
                    Prefs.runtimeEnabled(this),
                    Prefs.get(this).getBoolean("blitzer_enabled", false),
                    Prefs.connected(this, "blitzer"),
                    false,
                    now,
                    nextRetryElapsed)) {

                nextRetryElapsed =
                        now + RETRY_INTERVAL_MS;

                Prefs.note(
                        this,
                        "Blitzer-Wache: Start-Intent wegen fehlender Benachrichtigung");

                Blitzer.command(this, true);
            }
        }

        handler.postDelayed(
                checkTask,
                CHECK_INTERVAL_MS);
    }

    @Override
    public void onDestroy() {
        active = false;

        handler.removeCallbacks(checkTask);
        ServiceState.stopped(this);

        Prefs.note(
                this,
                "Fahrmodus angehalten");

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
