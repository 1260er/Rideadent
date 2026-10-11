package de.pritcloud.rideadent;
import android.content.Context;
import android.content.Intent;
final class Blitzer {
 static void command(Context context, boolean start) {
  Intent intent=new Intent(start?"de.blitzer.APP_MODE":"de.blitzer.KILL_APP");
  intent.setClassName("de.blitzer.plus", "de.blitzer.service.BlitzerBroadcastReceiver");
  if(start) intent.putExtra("mode", "START");
  try {context.sendBroadcast(intent); Prefs.note(context,"Blitzer-Befehl gesendet: "+(start?"Start":"Stop"));}
  catch(Exception ex) {Prefs.note(context,"Blitzer-Befehl fehlgeschlagen: "+ex.getClass().getSimpleName());}
 }
}
