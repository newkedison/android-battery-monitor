package tk.newk.battery;

import android.content.Intent;
import android.content.SharedPreferences;

import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import android.os.Bundle;

import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import android.text.InputFilter;

import static tk.newk.battery.Common.*;
import static tk.newk.common.utils.*;
import static tk.newk.common.log.*;

class IntInputFilter implements InputFilter 
{
  //cannot set the min value filter, because we can not guarantee that by a filter
  private int m_max;
  public IntInputFilter(int max)
  {
    m_max = max;
  }
  public CharSequence filter(CharSequence source, int start, int end,
      android.text.Spanned dest, int dstart, int dend) 
  {
    if (end > start) 
    {
      String destTxt = dest.toString();
      String resultingTxt = destTxt.substring(0, dstart) 
        + source.subSequence(start, end) 
        + destTxt.substring(dend);
      try
      {
        int value = Integer.parseInt(resultingTxt);
        if (value <= m_max)
        {
          return null;
        }
      }
      catch(NumberFormatException e)
      { }
    }
    return "";
  }
};

public class SettingsActivity extends PreferenceActivity
    implements OnSharedPreferenceChangeListener
{
  @SuppressWarnings("deprecation")
  private Preference set_integer_title(String key, String format, 
      int default_value)
  {
    int value = read_setting_int(key, default_value);
    Preference pref = findPreference(key);
    pref.setTitle(String.format(format, value));
    return pref;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onCreate(Bundle saveInstanceState)
  {
    super.onCreate(saveInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    getPreferenceScreen().getSharedPreferences()
      .registerOnSharedPreferenceChangeListener(this);
    InputFilter[] filters = new InputFilter[1];
    filters[0] = new IntInputFilter(500);
    ((EditTextPreference)findPreference(PREF_KEY_LIST_SIZE))
      .getEditText().setFilters(filters);
  }

  @SuppressWarnings("deprecation")
  private Preference init_checkbox(String key, boolean default_value)
  {
    Preference pref = findPreference(key);
    ((CheckBoxPreference)pref).setChecked(
        read_setting_boolean(key, default_value));
    return pref;
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void onResume() 
  {
    super.onResume();
    init_checkbox(PREF_KEY_SERVICE_ENABLE, true);
    init_checkbox(PREF_KEY_TTS_ENABLE, false);
    init_checkbox(PREF_KEY_TTS_LOW_ALARM, true);
    init_checkbox(PREF_KEY_TTS_VERY_LOW_ALARM, true);
    init_checkbox(PREF_KEY_TTS_CHARGE_PROGRESS, true);
    init_checkbox(PREF_KEY_TTS_USED_PROGRESS, true);
    init_checkbox(PREF_KEY_TTS_POWER_STATE, true);
    init_checkbox(PREF_KEY_TTS_SILENT_IN_NIGHT, true);
    findPreference(PREF_KEY_TTS_DETAIL).setEnabled(
        read_setting_boolean(PREF_KEY_TTS_ENABLE, false));
    set_integer_title(PREF_KEY_LIST_SIZE, 
        getString(R.string.pref_list_size) + ": %d", 200);
    set_integer_title(PREF_KEY_INTERVAL,
        getString(R.string.pref_interval) + ": %d minute(s)", 5);
    set_integer_title(PREF_KEY_AXIS_LEFT_MAX, 
        getString(R.string.pref_axis_left_max) + ": %d", 100);
    set_integer_title(PREF_KEY_AXIS_LEFT_MIN, 
        getString(R.string.pref_axis_left_min) + ": %d", 0);
    set_integer_title(PREF_KEY_AXIS_RIGHT_MAX, 
        getString(R.string.pref_axis_right_max) + ": %d", 10);
    set_integer_title(PREF_KEY_AXIS_RIGHT_MIN, 
        getString(R.string.pref_axis_right_min) + ": %d", -10);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void onDestroy() 
  {
    super.onDestroy();
    getPreferenceScreen().getSharedPreferences()
      .unregisterOnSharedPreferenceChangeListener(this);
  } 

  @Override
  protected void onPause()
  {
    super.onPause();
    int new_list_size = read_setting_int(PREF_KEY_LIST_SIZE, 200);
    int old_list_size = battery_used_rate.size();
    if (new_list_size < old_list_size)
    {
      for (int i = old_list_size - 1; i >= new_list_size; --i)
      {
        battery_used_rate.remove(i);
      }
      if(adapter_battery_used_rate != null)
        adapter_battery_used_rate.notifyDataSetChanged();
    }
    else
    {
      battery_used_rate.clear();
    }
  }

  private int check_range(SharedPreferences sharePreferences, 
      String key, int default_value, int min, int max)
  {
    int value = read_setting_int(key, default_value);
    int new_value = value;
    if (value < min)
      new_value = min;
    if (value > max)
      new_value = max;
    if (value != new_value)
    {
      SharedPreferences.Editor editor = sharePreferences.edit();
      editor.putString(key, str(new_value));
      editor.commit();
      value = new_value;
    }
    return value;
  }

  @SuppressWarnings("deprecation")
  public void onSharedPreferenceChanged(SharedPreferences sharePreferences, 
      String key)
  {
    logv(this, "key", key);
    if (key.equals(PREF_KEY_SERVICE_ENABLE))
    {
      boolean service_enable = sharePreferences.getBoolean(key, true);
      if (service_enable)
      {
        Intent intent = new Intent(this, MonitorService.class);
        startService(intent);
      }
      else
      {
        Intent intent = new Intent(this, MonitorService.class);
        stopService(intent);
      }
    }
    else if (key.equals(PREF_KEY_LIST_SIZE))
    {
      int list_size = check_range(sharePreferences, key, 200, 50, 500);
      set_integer_title(PREF_KEY_LIST_SIZE, 
          getString(R.string.pref_list_size) + ": %d", 200);
      logv(this, "list_size change to", str(list_size));
    }
    else if (key.equals(PREF_KEY_AXIS_LEFT_MAX))
    {
      int value = check_range(sharePreferences, key, 100, 
          read_setting_int(PREF_KEY_AXIS_LEFT_MIN, 0) + 1, 200);
      set_integer_title(PREF_KEY_AXIS_LEFT_MAX,
          getString(R.string.pref_axis_left_max) + ": %d", 100);
      logv(this, "left axis max change to", str(value));
    }
    else if (key.equals(PREF_KEY_AXIS_LEFT_MIN))
    {
      int value = check_range(sharePreferences, key, 0, -100,
          read_setting_int(PREF_KEY_AXIS_LEFT_MAX, 100) - 1);
      set_integer_title(PREF_KEY_AXIS_LEFT_MIN,
          getString(R.string.pref_axis_left_min) + ": %d", 100);
      logv(this, "left axis min change to", str(value));
    }
    else if (key.equals(PREF_KEY_AXIS_RIGHT_MAX))
    {
      int value = check_range(sharePreferences, key, 10, 
          read_setting_int(PREF_KEY_AXIS_RIGHT_MIN, -10) + 1, 100);
      set_integer_title(PREF_KEY_AXIS_RIGHT_MAX,
          getString(R.string.pref_axis_right_max) + ": %d", 10);
      logv(this, "right axis max change to", str(value));
    }
    else if (key.equals(PREF_KEY_AXIS_RIGHT_MIN))
    {
      int value = check_range(sharePreferences, key, -10, -100,
          read_setting_int(PREF_KEY_AXIS_RIGHT_MAX, 10) - 1);
      set_integer_title(PREF_KEY_AXIS_RIGHT_MIN,
          getString(R.string.pref_axis_right_min) + ": %d", -10);
      logv(this, "right axis min change to", str(value));
    }
    else if (key.equals(PREF_KEY_INTERVAL))
    {
      int interval = Integer.parseInt(sharePreferences.getString(key, "5"));
      set_integer_title(PREF_KEY_INTERVAL,
          getString(R.string.pref_interval) + ": %d minute(s)", 5);
      logv(this, "interval change to", str(interval));
    }
    else if (key.equals(PREF_KEY_TTS_ENABLE))
    {
      Preference pref = findPreference(PREF_KEY_TTS_ENABLE);
      findPreference(PREF_KEY_TTS_DETAIL).setEnabled(
          ((CheckBoxPreference)pref).isChecked());
    }
  }

}

// vim: fdm=syntax fdl=1 fdn=2

