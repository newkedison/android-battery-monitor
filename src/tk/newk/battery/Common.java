package tk.newk.battery;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.media.AudioManager;
import android.os.BatteryManager;

import android.speech.tts.TextToSpeech;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;
import android.widget.TextView;

import static tk.newk.common.log.*;
import static tk.newk.common.utils.*;

public class Common
{
  final static String START_SERVICE_FROM_RECEIVER 
    = "Start service from BatteryChangedReceiver";
  final static String FILE_NAME_HISTORY = "history%04d.dat";
  final static String FILE_NAME_RECENT = "recent.dat";
  //how many record store in a history file
  final static int HISTORY_FILE_RECORD_COUNT = 1000;
  final static int RECORD_LENGTH = 16;
  //uniqe ID for check TTS enabled
  final static int ID_TTS_CHECK_ENABLED = 0;
  //power supply state
  final static int POWER_SUPPLY_STATE_UNKNOWN = 0;
  final static int POWER_SUPPLY_STATE_DISCONNECTED = 1;
  final static int POWER_SUPPLY_STATE_CONNECTED = 2;
  final static int ID_NOTIFICATION = 0;
  //preference key
  final static String PREF_KEY_SERVICE_ENABLE = "pref_service_enable";
  final static String PREF_KEY_LIST_SIZE = "pref_list_size";
  final static String PREF_KEY_INTERVAL = "pref_interval";
  final static String PREF_KEY_TTS_ENABLE = "pref_tts_enable";

  static boolean is_receiver_registed = false;
  static BatteryInfo[] buffer_data = new BatteryInfo[HISTORY_FILE_RECORD_COUNT];
  static int buffer_data_used_size = 0;
  static int latest_history_index = -1;
  static MyArrayAdapter adapter_battery_used_rate = null;
  static ArrayList<BatteryUsedRate> battery_used_rate
    = new ArrayList<BatteryUsedRate>();
  static boolean need_update_list_view = false;
  static BroadcastReceiver battery_changed_receiver 
    = new BatteryChangedReceiver();
  static boolean is_service_foreground = false;
  static boolean is_TTS_checked = false;
  static boolean is_notification_init = false;
  static int power_supply_state = POWER_SUPPLY_STATE_UNKNOWN;

  static SharedPreferences global_setting = null;
  static Context service_context = null;

  static TextView lbl_current_battery_state = null;
  static String str_current_battery_state = "";

  static TTS_helper TTS_helper_class = null;

  private static class _read_buffer
  {
    public _read_buffer()
    {
      cursor = buffer_data_used_size - 1;
      read_history_index = find_latest_history_index();
    }
    public BatteryInfo get() throws FileNotFoundException
    {
      if (cursor >= 0)
      {
        --cursor;
        if (read_from_file == null)
          return buffer_data[cursor + 1];
        else
          return read_from_file[cursor + 1];
      }
      else
      {
        if (read_history_index >= 0)
        {
          read_from_file = new BatteryInfo[HISTORY_FILE_RECORD_COUNT];
          InputStream is = null;
          int read_len;
          int i = 0;
          byte[] buf = new byte[RECORD_LENGTH];
          try
          {
            is = new BufferedInputStream(service_context.openFileInput(
                  String.format(FILE_NAME_HISTORY, read_history_index)));
            do
            {
              read_len = is.read(buf, 0, RECORD_LENGTH);
              if (read_len == RECORD_LENGTH)
              {
                read_from_file[i] = new BatteryInfo(buf);
                ++i;
              }
            }while(read_len > 0);
            is.close();
          }
          catch (Exception e)
          {
            logexception(this, e);
            throw new FileNotFoundException();
          }
          if (i != HISTORY_FILE_RECORD_COUNT)
            throw new FileNotFoundException();
          cursor = i - 2;
          --read_history_index;
          return read_from_file[i - 1];
        }
      }
      throw new FileNotFoundException();
    }
    private int cursor;
    private BatteryInfo[] read_from_file;
    private int read_history_index;
  }

  private static void parse_buffer_data_to_used_rate(
      ArrayList<BatteryUsedRate> dst, long end_time, int interval, int max_size)
  {
    dst.clear();
    Calendar calendar = Calendar.getInstance();
    int minute = calendar.get(Calendar.MINUTE);
    calendar.set(Calendar.MINUTE, minute - (minute % interval));
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    if (end_time != 0)
      max_size = (int)
        ((calendar.getTime().getTime() - end_time) / 60000 / interval) + 1;
    
    dst.ensureCapacity(max_size + 1);
    BatteryUsedRate bur = null;
    BatteryUsedRate bur_prev = null;
    int list_index = 0;
    BatteryInfo bi = null;
    BatteryInfo bi_next = null;
    _read_buffer read_buffer = new _read_buffer();
    try
    {
      bi = read_buffer.get();
      bi_next = read_buffer.get();
      //read one more point from the history
      while (list_index < max_size + 1)
      {
        bur = new BatteryUsedRate();
        bur.time = calendar.getTime().getTime();
        if (list_index == 0)
        {
          while (bur.time > bi.time)
          {
            calendar.add(Calendar.MINUTE, -interval);
            bur.time = calendar.getTime().getTime();
          }
        }
        while (bi_next.time >= bur.time)
        {
          bi = bi_next;
          bi_next = read_buffer.get();
        }
        bur.is_charging = bi.is_charging;
        bur.level = 
          (float)(bi.level - bi_next.level) * (bur.time - bi_next.time)
          / (bi.time - bi_next.time) 
          + bi_next.level;
        dst.add(bur);
        if (list_index > 0)
        {
          bur_prev = dst.get(list_index - 1);
          bur_prev.rate = (bur_prev.level - bur.level);
        }
        if (bur.time <= end_time)
          break;
        calendar.add(Calendar.MINUTE, -interval);
        ++list_index;
      }
    }
    catch (FileNotFoundException e)
    {}
    //delete the last point
    int last = dst.size() - 1;
    if (last >= 0 && dst.get(last).rate == 0)
      dst.remove(last);
  }
  private static boolean recreate_battery_used_rate()
  {
    if (need_update_list_view && buffer_data_used_size > 2
        && adapter_battery_used_rate != null)
    {
      battery_used_rate.clear();
      int interval = read_setting_int(PREF_KEY_INTERVAL, 5);
      int list_count = read_setting_int(PREF_KEY_LIST_SIZE, 200);
      battery_used_rate.clear();
      parse_buffer_data_to_used_rate(battery_used_rate, 0, 
          interval, list_count);
      return true;
    }
    return false;
  }

  public static boolean update_battery_used_rate_list()
  {
    if (need_update_list_view && buffer_data_used_size > 2
        && adapter_battery_used_rate != null)
    {
      if (battery_used_rate.size() < 2)
        return recreate_battery_used_rate();
      int interval = read_setting_int(PREF_KEY_INTERVAL, 5);
      long old_interval = 
        (battery_used_rate.get(0).time - battery_used_rate.get(1).time) 
        / 60000L;
      if (interval != old_interval)
        return recreate_battery_used_rate();
      int list_count = read_setting_int(PREF_KEY_LIST_SIZE, 200);
      ArrayList<BatteryUsedRate> addition = new ArrayList<BatteryUsedRate>();
      parse_buffer_data_to_used_rate(addition, battery_used_rate.get(0).time, 
            interval, 0);
      if (addition.size() > 0)
      {
        battery_used_rate.addAll(0, addition);
        if (battery_used_rate.size() > list_count)
        {
          for (int i = battery_used_rate.size() - 1; i > list_count; --i)
          {
            battery_used_rate.remove(i);
          }
        }
        return true;
      }
    }
    return false;
  }

  public static int find_latest_history_index()
  {
    logd("find_latest_history_index", str(latest_history_index));
    if (latest_history_index >= 0)
      return latest_history_index;
    String[] file_name_list = service_context.fileList();
    int index = -1;
    String file_name;
    boolean is_found;
    int ret = index;
    do
    {
      ++index;
      file_name = String.format(FILE_NAME_HISTORY, index);
      is_found = false;
      for (int i = 0; i < file_name_list.length; ++i)
      {
        if (file_name.equals(file_name_list[i]))
        {
          ret = index;
          is_found = true;
          break;
        }
      }
    }while(is_found);
    return ret;
  }

  public static int read_setting_int(String key, int default_value)
  {
    try
    {
      return global_setting.getInt(key, default_value);
    }
    catch (ClassCastException e)
    {
      return Integer.parseInt(global_setting.getString(
            key, Integer.toString(default_value)));
    }
  }

  public static boolean read_setting_boolean(String key, boolean default_value)
  {
    try
    {
      return global_setting.getBoolean(key, default_value);
    }
    catch (ClassCastException e)
    {
      return Boolean.parseBoolean(global_setting.getString(
            key, Boolean.toString(default_value)));
    }
  }
}

class MyArrayAdapter extends ArrayAdapter<BatteryUsedRate>
{
  private Context m_context;
  private List<BatteryUsedRate> m_data;
  static class ViewHolder
  {
    public TextView lbl_left;
    public TextView lbl_right;
  }
  public MyArrayAdapter(Context context, List<BatteryUsedRate> data)
  {
    super(context, R.layout.list_item_small, data);
    m_context = context;
    m_data = data;
  }
  public String toString()
  {
    return str(m_data.size());
  }
  @Override
  public View getView(int position, View convertView, ViewGroup parent)
  {
    View row = convertView;
    if (row == null)
    {
      LayoutInflater inflater = (LayoutInflater) m_context
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      row = inflater.inflate(R.layout.list_item_small, parent, false);
      ViewHolder vh = new ViewHolder();
      vh.lbl_left = (TextView)row.findViewById(R.id.lbl_left);
      vh.lbl_right = (TextView)row.findViewById(R.id.lbl_right);
      row.setTag(vh);
    }
    BatteryUsedRate bur = m_data.get(position);
    ViewHolder vh = (ViewHolder) row.getTag();
    String charge_state;
    if (bur.is_charging)
      charge_state = "+";
    else
      charge_state = "";
    vh.lbl_left.setText(String.format("%3d:%s %4.2f%% %s", position + 1, 
        format_ticket(bur.time, "yy-MM-dd HH:mm:ss"), bur.level, charge_state));
    vh.lbl_right.setText(str(bur.rate, "0.00") + "%");
    return row;
  }
}

class BatteryUsedRate
{
  public long time;
  public float level;
  public boolean is_charging;
  public float rate;
}

class FixFIFO<T>
{
  private List<T> m_queue;
  private int m_size;
  
  public FixFIFO(int size)
  {
    if (size < 1)
      size = 10;
    m_size = size;
    m_queue = new LinkedList<T>();
  }
  public boolean resize(int new_size)
  {
    if (new_size < 1)
      return false;
    if (new_size < m_size)
    {
      if (new_size < m_queue.size())
        m_queue = m_queue.subList(0, new_size);
    }
    m_size = new_size;
    return true;
  }
  public void add(T o)
  {
    m_queue.add(0, o);
    if (m_queue.size() > m_size)
    {
      //remove nearly 1/5 of all elements from the list, FIFO.
      //because insert to the first, wo must delete from the end of list
      int delete_count = (m_size + 4) / 5;
      logd(this, "begin to delete items form FIFO", str(delete_count));
      for (int i = 0; i < delete_count; ++i)
      {
        m_queue.remove(m_queue.size() - 1);
      }
    }
  }
  public int used_size()
  {
    return m_queue.size();
  }
  public int capacity()
  {
    return m_size;
  }
  public T get(int i)
  {
    if (i >= 0 && i < m_queue.size())
    {
      return m_queue.get(i);
    }
    else
    {
      return null;
    }
  }
  public ListIterator<T> Iterator()
  {
    return m_queue.listIterator();
  }
}

class BatteryInfo
{
  public int state;
  public boolean is_charging;
  public int level;
  public int power_state;
  public long time;
  private boolean judge_charging(int _state, int _power)
  {
    if (_power != Common.POWER_SUPPLY_STATE_UNKNOWN)
    {
      is_charging = (_power == Common.POWER_SUPPLY_STATE_CONNECTED);
    }
    else
    {
      is_charging = (_state == BatteryManager.BATTERY_STATUS_CHARGING 
          || _state == BatteryManager.BATTERY_STATUS_FULL);
    }
    return is_charging;
  }
  public BatteryInfo(Intent intent)
  {
    if (intent != null)
    {
      state = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
      power_state = Common.power_supply_state;
      judge_charging(state, power_state);
      level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
//      int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    }
    time = ticket();
  }
  public BatteryInfo(byte[] data)
  {
    update_from_bytes(data);
  }
  public String toString()
  {
    String ret = String.format("[%s]State=%d, Level=%d, Power=%d", 
        format_ticket(time, "yy-MM-dd HH:mm:ss"), state, level, power_state);
    if (is_charging)
      ret += "Charging";
    return ret;
  }
  public byte[] to_bytes()
  {
    byte[] ret = new byte[Common.RECORD_LENGTH];
    for (int i = 0; i < 8; ++i) {
      ret[i] = (byte) (time >> ((8 - i - 1) << 3));
    }
    ret[9] = (byte)(state & 0xFF);
    ret[10] = (byte)(level & 0xFF);
    ret[11] = (byte)(power_state & 0xFF);
    add_crc(ret);
    return ret;
  }
  public void update_from_bytes(byte[] data)
  {
    if (data.length == Common.RECORD_LENGTH && crc16(data) == 0)
    {
      time = 0;
      for (int i = 0; i < 8; i++)
      {
        time = (time << 8) + (data[i] & 0xff);
      }
      state = data[9];
      level = data[10];
      power_state = data[11];
      judge_charging(state, power_state);
    }
  }
}

class TTS_helper implements TextToSpeech.OnInitListener, 
               TextToSpeech.OnUtteranceCompletedListener
{
  private TextToSpeech m_TTS = null;
  private String m_what;
  private float m_volume;
  private float m_pre_volume;
  private AudioManager m_audio_manager;
  private Context m_context;

  public TTS_helper(Context context)
  {
    m_context = context;
    m_audio_manager = (AudioManager)(context.getSystemService(
        Context.AUDIO_SERVICE));
  }

  int change_volume(int stream, int volume)
  {
    int prev_volume = m_audio_manager.getStreamVolume(stream);
    int max_volume = m_audio_manager.getStreamMaxVolume(stream);
    if (volume > max_volume)
      volume = max_volume;
    m_audio_manager.setStreamVolume(stream, volume, 0);
    logd("change_volume", "volume is change to ", str(volume));
    return prev_volume;
  }

  int change_volume(int stream, float scale)
  {
    int max_volume = m_audio_manager.getStreamMaxVolume(stream);
    return change_volume(stream, (int)(max_volume * scale));
  }

  public boolean say(String what, float volume_scale)
  {
    logv(this, "say", what, str(volume_scale));
    if (m_TTS == null)
    {
      logv(this, "creating TTS");
      m_what = what;
      m_volume = volume_scale;
      m_TTS = new TextToSpeech(m_context, this);
      return true;
    }
    return false;
  }

  public boolean say(String what)
  {
    return say(what, -1f);
  }

  // interface TextToSpeech.OnInitListener
  @SuppressWarnings("deprecation")
  public void onInit(int initStatus) 
  {
    //check for successful instantiation
    if (initStatus == TextToSpeech.SUCCESS) 
    {
      int result = m_TTS.isLanguageAvailable(Locale.US);
      if (result != TextToSpeech.LANG_MISSING_DATA 
          && result != TextToSpeech.LANG_NOT_SUPPORTED)
      {
        if (m_volume > 0 && m_volume <= 1)
        {
          m_pre_volume = change_volume(AudioManager.STREAM_MUSIC, m_volume);
        }
        else
        {
          m_pre_volume = -1;
        }
        m_TTS.setLanguage(Locale.US);
        m_TTS.setOnUtteranceCompletedListener(this);
        HashMap<String, String> extra = new HashMap<String, String>();
        extra.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "battery");
        m_TTS.speak(m_what, TextToSpeech.QUEUE_FLUSH, extra);
      }
      else
      {
        logw(this, "Chech language fail, TTS will be shutdown");
        m_TTS.shutdown();
        m_TTS = null;
      }
    }
    else
    {
      logw(this, "Init TTS fail, TTS will be shutdown");
      m_TTS.shutdown();
      m_TTS = null;
    }
  }

  // interface TextToSpeech.OnUtteranceCompletedListener
  public void onUtteranceCompleted(String id)
  {
    logv(this, "TTS is complete");
    if (m_pre_volume > 0)
    {
      change_volume(AudioManager.STREAM_MUSIC, m_pre_volume);
      m_pre_volume = -1;
    }
    logv(this, "TTS will be shutdown");
    m_TTS.shutdown();
    m_TTS = null;
  }
}

// vim: fdm=syntax fdl=1 fdn=2

