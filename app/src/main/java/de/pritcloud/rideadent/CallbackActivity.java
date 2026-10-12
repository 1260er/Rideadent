package de.pritcloud.rideadent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Activity PendingIntent: Android forbids starting a call Activity via notification trampoline services. */
public final class CallbackActivity extends Activity {
    private static final int CALL_REQUEST = 31;
    private int entryId = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        entryId = getIntent().getIntExtra(CallbackNotifications.EXTRA_ID, -1);
        if (entryId <= 0 || !CallbackNotifications.enabled(this)
                || CallbackNotifications.find(this, entryId) == null) {
            finish();
            return;
        }
        // Avoid placing a second call or leaving a transparent window open
        // if Android recreates the Activity. The notification remains available.
        if (state != null) {
            finish();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.CALL_PHONE }, CALL_REQUEST);
            return;
        }
        callAndRemove();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CALL_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            callAndRemove();
        } else {
            Toast.makeText(this, R.string.callback_permission_needed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void callAndRemove() {
        CallbackNotifications.Entry entry = CallbackNotifications.find(this, entryId);
        if (!CallbackNotifications.enabled(this) || entry == null
                || checkSelfPermission(Manifest.permission.CALL_PHONE)
                        != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        String phone = CallbackRules.dialNumber(entry.phone);
        if (phone == null) {
            finish();
            return;
        }
        Intent call = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone, null));
        try {
            startActivity(call);
            // Remove only the clicked entry once Android accepted the call Intent.
            CallbackNotifications.remove(this, entryId);
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.callback_call_failed, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
