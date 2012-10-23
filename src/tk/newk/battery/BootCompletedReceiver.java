package tk.newk.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.widget.Toast;
import static tk.newk.common.log.*;

public class BootCompletedReceiver extends BroadcastReceiver
{
  @Override
  public void onReceive(Context context, Intent intent)
  {
    logv(this, "BootCompletedReceiver receive broadcast");
    Toast.makeText(context, "Boot Completed", 10);
    Intent service_intent = new Intent(context, MonitorService.class);
    context.startService(service_intent);
  }
}
