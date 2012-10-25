package tk.newk.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.preference.PreferenceManager;

import static tk.newk.common.log.*;

public class BootCompletedReceiver extends BroadcastReceiver
{
  @Override
  public void onReceive(Context context, Intent intent)
  {
    logv(this, "BootCompletedReceiver receive broadcast");
    SharedPreferences shared_pref 
      = PreferenceManager.getDefaultSharedPreferences(context);
    boolean service_enable 
      = shared_pref.getBoolean("pref_service_enable", true);
    if (service_enable)
    {
      Intent service_intent = new Intent(context, MonitorService.class);
      context.startService(service_intent);
    }
  }
}
