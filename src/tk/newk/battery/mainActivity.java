package tk.newk.battery;

import java.io.FileInputStream;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.widget.TextView;

import static tk.newk.common.log.*;
import static tk.newk.common.utils.*;

import static tk.newk.battery.Common.*;

public class mainActivity extends ListActivity 
{
  private TextView m_lbl_battery_level;
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
    logv(this, "ativity is created");
    setContentView(R.layout.main);
    m_lbl_battery_level = (TextView)findViewById(R.id.lbl_battery_level);
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
    if (!is_TTS_checked)
    {
      is_TTS_checked = true;
      check_TTS_enable();
    }
    if (global_setting == null)
      global_setting = PreferenceManager.getDefaultSharedPreferences(this);
    boolean service_enable 
      = global_setting.getBoolean(PREF_KEY_SERVICE_ENABLE, true);
    if (service_enable)
    {
      Intent service_intent = new Intent(this, MonitorService.class);
      startService(service_intent);
    }
  }

  @Override
  public void onResume()
  {
    super.onResume();
    logv(this, "ativity is resume");
    need_update_list_view = true;
    if (update_battery_used_rate_list())
      adapter_battery_used_rate.notifyDataSetChanged();
    lbl_current_battery_state = m_lbl_battery_level;
    lbl_current_battery_state.setText(str_current_battery_state);
  }

  @Override
  public void onPause()
  {
    super.onPause();
    logv(this, "ativity is pause");
    need_update_list_view = false;
    lbl_current_battery_state = null;
  }

  @Override
  public void onStop()
  {
    super.onStop();
    logv(this, "activity is stop");
  }

  void check_TTS_enable()
  {
    Intent checkTTS = new Intent();
    checkTTS.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
    startActivityForResult(checkTTS, TTS_CHECK_ENABLED);
  }

  protected void onActivityResult(int requestCode, int resultCode, 
      Intent data) 
  {
    if (requestCode == TTS_CHECK_ENABLED) 
    {
      if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) 
      {
        //the user has the necessary data - create the TTS
//        myTTS = new TextToSpeech(this, this);
        //do nothing here, because I will create the TTS in the service
      }
      else 
      {
        //no data - install it now
        Intent installTTSIntent = new Intent();
        installTTSIntent.setAction(
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        startActivity(installTTSIntent);
      }
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId())
    {
//      case R.id.menu_curve:
//        return true;
      case R.id.menu_log:
        Intent open_log = new Intent(this, LogActivity.class);
        startActivity(open_log);
        return true;
      case R.id.menu_setting:
        logv(this, "menu_setting is click");
        Intent open_setting = new Intent(this, SettingsActivity.class);
        startActivity(open_setting);
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }
}

// vim: fdm=syntax fdl=1 fdn=2
