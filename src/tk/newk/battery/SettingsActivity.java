package tk.newk.battery;

import android.content.Intent;
import android.content.SharedPreferences;

import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import android.os.Bundle;

import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

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
    SharedPreferences shared_pref 
      = PreferenceManager.getDefaultSharedPreferences(this);
    int list_size = Integer.parseInt(shared_pref.getString(
          PREF_KEY_LIST_SIZE, "200"));
    Preference pref = findPreference(PREF_KEY_LIST_SIZE);
    pref.setTitle(String.format(
          getString(R.string.pref_list_size), list_size));
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
    logw(this, "key", key);
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
      FIFO_history.resize(list_size);
      Preference pref = findPreference(key);
      pref.setTitle(String.format(
            getString(R.string.pref_list_size), list_size));
      logw(this, "list_size change to", str(list_size));
    }
  }
}
