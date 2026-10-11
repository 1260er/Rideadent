package de.pritcloud.rideadent;
import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
public class BtReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i) {
  if (i == null) return;
  String action = i.getAction();
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
  Prefs.note(c, (connected ? "Verbunden: " : "Getrennt: ") + name);
  if (Prefs.runtimeEnabled(c)
          && Prefs.get(c).getBoolean("blitzer_enabled", false)
          && previous != now) {
   Blitzer.command(c, now);
  }
  BlitzerWatchService.sync(c);
 }
}
