package tk.newk.battery;

import java.io.FileInputStream;

import android.app.ListActivity;

import android.content.Intent;
import android.os.Bundle;

import android.view.View;

import static tk.newk.common.log.*;
import static tk.newk.common.utils.*;

import static tk.newk.battery.Common.*;

public class mainActivity extends ListActivity
{
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    logv(this, "ativity is created");
    setContentView(R.layout.main);
    if (adapter_battery_used_rate == null)
      adapter_battery_used_rate = new MyArrayAdapter(this, battery_used_rate);
    setListAdapter(adapter_battery_used_rate);
    log_set_tag("battery");
    String[] file_list = fileList();
    for (String file_name: file_list)
    {
      FileInputStream fis = null;
      int size = -1;
      try
      {
        fis = openFileInput(file_name);
        size = fis.available();
        fis.close();
      }
      catch (Exception e)
      {
        logexception(this, e);
      }
      logv(this, file_name, str(size));
    }
  }

  @Override
  public void onResume()
  {
    super.onResume();
    logv(this, "ativity is resume");
    need_update_list_view = true;
    if (battery_info_to_battery_use_rate())
      adapter_battery_used_rate.notifyDataSetChanged();
  }

  @Override
  public void onPause()
  {
    super.onPause();
    logv(this, "ativity is pause");
    need_update_list_view = false;
  }

  @Override
  public void onStop()
  {
    super.onStop();
    logv(this, "activity is stop");
  }

  public void start_service(View v)
  {
    Intent intent = new Intent(this, MonitorService.class);
    startService(intent);
  }

  public void stop_service(View v)
  {
    Intent intent = new Intent(this, MonitorService.class);
    stopService(intent);
  }
}
