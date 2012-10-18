package tk.newk.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static tk.newk.common.log.*;

public class BatteryChangedReceiver extends BroadcastReceiver
{
  @Override
  public void onReceive(Context context, Intent intent)
  {
    logv(this, "receive broadcast");
    //change the intent to explicitly start the MonitorService
    intent.setClass(context, MonitorService.class);
    intent.putExtra(Common.START_SERVICE_FROM_RECEIVER, true);
    context.startService(intent);
  }
}
