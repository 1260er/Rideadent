package de.pritcloud.rideadent;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
public class BtReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i) {
  if (i == null) return;
  String action = i.getAction();
   // A powered-off radio cannot have usable ACL connections. This broadcast
   // is handled independently of per-device ACL events (which can be lost).
   if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
    int state = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
    if (state != BluetoothAdapter.STATE_TURNING_OFF
            && state != BluetoothAdapter.STATE_OFF) return;
    boolean hadBlitzer = Logic.shouldRun(
            Prefs.recordedActive(c), Prefs.chosen(c, "blitzer"));
    Prefs.clearActive(c);
    if (hadBlitzer && Prefs.runtimeEnabled(c)
            && Prefs.get(c).getBoolean("blitzer_enabled", false)) {
     Blitzer.command(c, false);
    }
    Prefs.note(c, "Bluetooth ausgeschaltet: Verbindungsstatus gelöscht");
    BlitzerWatchService.sync(c);
    return;
   }
   final boolean connected;
  if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
   connected = true;
  } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
   connected = false;
  } else {
   // Unknown actions must never be interpreted as a disconnect.
   return;
  }
  if (c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
          != PackageManager.PERMISSION_GRANTED) return;
  // Ignore malformed external broadcasts and permission revocation races.
  final String address;
  final String name;
  try {
   BluetoothDevice device = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
   if (device == null) return;
   address = device.getAddress();
   if (address == null) return;
   name = device.getName();
  } catch (RuntimeException ignored) {
   return;
  }
  boolean previous = Prefs.connected(c, "blitzer");
  Prefs.state(c, address, connected);
  boolean now = Prefs.connected(c, "blitzer");
  // Android may omit a Bluetooth name even for a valid connection.
  // Preserve the known address instead of recording the string "null".
  String displayName = name == null ? "" :
          name.replace((char) 10, (char) 32)
                  .replace((char) 13, (char) 32).trim();
  if (displayName.isEmpty()) {
   displayName = "Bluetooth-Gerät " + address;
  }

  // Selection is independent of whether an automation is enabled.
  boolean selectedBlitzer = Prefs.chosen(c, "blitzer").contains(address);
  boolean selectedCalls = Prefs.chosen(c, "sms").contains(address);
  String selection = selectedBlitzer && selectedCalls
          ? " (ausgewählt: beide)"
          : selectedBlitzer ? " (ausgewählt: Blitzer.de)"
          : selectedCalls ? " (ausgewählt: Anrufautomatik)"
          : " (nicht ausgewählt)";

  Prefs.note(c, (connected ? "Verbunden: " : "Getrennt: ")
          + displayName + selection);
  if (Prefs.runtimeEnabled(c)
          && Prefs.get(c).getBoolean("blitzer_enabled", false)
          && previous != now) {
   Blitzer.command(c, now);
  }
  BlitzerWatchService.sync(c);
 }
}
