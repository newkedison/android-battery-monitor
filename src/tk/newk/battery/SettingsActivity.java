package tk.newk.battery;

import android.content.Intent;
import android.content.SharedPreferences;

import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import android.os.Bundle;

import android.preference.Preference;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity
    implements OnSharedPreferenceChangeListener
{
  @Override
  public void onCreate(Bundle saveInstanceState)
  {
    super.onCreate(saveInstanceState);
    addPreferencesFromResource(R.xml.preferences);
  }

  @Override
  protected void onResume() {
    super.onResume();
    getPreferenceScreen().getSharedPreferences()
      .registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();
    getPreferenceScreen().getSharedPreferences()
      .unregisterOnSharedPreferenceChangeListener(this);
  } 

  public void onSharedPreferenceChanged(SharedPreferences sharePreferences, 
      String key)
  {
    if (key.equals("pref_service_enable"))
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
  }
}
