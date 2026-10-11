package de.pritcloud.rideadent;
import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
public class BtReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i) {
  if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;
  BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
  if(d==null || d.getAddress()==null)return;
  boolean previous=Prefs.connected(c,"blitzer");
  boolean connected=BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction());
  Prefs.state(c,d.getAddress(),connected);
  boolean now=Prefs.connected(c,"blitzer");
  Prefs.note(c,(connected?"Verbunden: ":"Getrennt: ")+d.getName());
  if(Prefs.runtimeEnabled(c) && Prefs.get(c).getBoolean("blitzer_enabled",false) && previous!=now) Blitzer.command(c,now);
  // Keep the Fahrmodus service aligned with all selected Bluetooth states.
  BlitzerWatchService.sync(c);
 }
}
