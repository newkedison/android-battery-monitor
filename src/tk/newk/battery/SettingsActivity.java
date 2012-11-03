package tk.newk.battery;

import android.content.Intent;
import android.content.SharedPreferences;

import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import android.os.Bundle;

import android.preference.Preference;
import android.preference.PreferenceActivity;

import static tk.newk.battery.Common.*;
import static tk.newk.common.utils.*;
import static tk.newk.common.log.*;

public class SettingsActivity extends PreferenceActivity
    implements OnSharedPreferenceChangeListener
{
  @SuppressWarnings("deprecation")
  @Override
  public void onCreate(Bundle saveInstanceState)
  {
    super.onCreate(saveInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    getPreferenceScreen().getSharedPreferences()
      .registerOnSharedPreferenceChangeListener(this);
    int list_size = Integer.parseInt(global_setting.getString(
          PREF_KEY_LIST_SIZE, "200"));
    Preference pref = findPreference(PREF_KEY_LIST_SIZE);
    pref.setTitle(getString(R.string.pref_list_size) 
        + String.format(": %d", list_size));
    int interval = Integer.parseInt(global_setting.getString(
          PREF_KEY_INTERVAL, "5"));
    pref = findPreference(PREF_KEY_INTERVAL);
    pref.setTitle(getString(R.string.pref_interval)
        + String.format(": %d", interval));
  }

  @Override
  protected void onResume() 
  {
    super.onResume();
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void onDestroy() 
  {
    super.onDestroy();
    getPreferenceScreen().getSharedPreferences()
      .unregisterOnSharedPreferenceChangeListener(this);
  } 

  @SuppressWarnings("deprecation")
  public void onSharedPreferenceChanged(SharedPreferences sharePreferences, 
      String key)
  {
    logv(this, "key", key);
    if (key.equals(PREF_KEY_SERVICE_ENABLE))
    {
      Preference pref = findPreference(key);
      boolean service_enable = sharePreferences.getBoolean(key, true);
      if (service_enable)
      {
        pref.setSummary(getString(R.string.pref_sum_service_enabled));
        Intent intent = new Intent(this, MonitorService.class);
        startService(intent);
      }
      else
      {
        pref.setSummary(getString(R.string.pref_sum_service_disabled));
        Intent intent = new Intent(this, MonitorService.class);
        stopService(intent);
      }
    }
    else if (key.equals(PREF_KEY_LIST_SIZE))
    {
      int list_size = Integer.parseInt(sharePreferences.getString(key, "200"));
      int new_size = list_size;
      if (list_size < 50)
        new_size = 50;
      if (list_size > 500)
        new_size = 500;
      if (list_size != new_size)
      {
        SharedPreferences.Editor editor = sharePreferences.edit();
        editor.putString(key, str(new_size));
        editor.commit();
        list_size = new_size;
      }
      Preference pref = findPreference(key);
      pref.setTitle(getString(R.string.pref_list_size) 
          + String.format(": %d", list_size));
      logv(this, "list_size change to", str(list_size));
    }
    else if (key.equals(PREF_KEY_INTERVAL))
    {
      int interval = Integer.parseInt(sharePreferences.getString(key, "5"));
      int new_interval = interval;
      if (interval < 1)
        new_interval = 1;
      if (interval > 60)
        new_interval = 60;
      if (interval != new_interval)
      {
        SharedPreferences.Editor editor = sharePreferences.edit();
        editor.putString(key, str(new_interval));
        editor.commit();
        interval = new_interval;
      }
      Preference pref = findPreference(key);
      pref.setTitle(getString(R.string.pref_interval) 
          + String.format(": %d", interval));
      logv(this, "interval change to", str(interval));
    }
  }
}
