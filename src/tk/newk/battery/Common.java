package tk.newk.battery;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.media.AudioManager;

import android.os.AsyncTask;
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
  //buffer how many recored before save the buffer data to real file
  final static int SAVE_TO_FILE_STEP = 20;
  //uniqe ID for check TTS enabled
  final static int TTS_CHECK_ENABLED = 0;
  //power supply state
  final static int POWER_SUPPLY_STATE_UNKNOWN = 0;
  final static int POWER_SUPPLY_STATE_DISCONNECTED = 1;
  final static int POWER_SUPPLY_STATE_CONNECTED = 2;
  final static int ID_NOTIFICATION = 0;
  //preference key
  final static String PREF_KEY_SERVICE_ENABLE = "pref_service_enable";
  final static String PREF_KEY_LIST_SIZE = "pref_list_size";

  static boolean is_receiver_registed = false;
  static BatteryInfo[] buffer_data = new BatteryInfo[HISTORY_FILE_RECORD_COUNT];
  static int buffer_data_used_size = 0;
  static int recent_file_record_count = 0;
  static int latest_history_index = -1;
  static MyArrayAdapter adapter_battery_used_rate = null;
  static ArrayList<BatteryUsedRate> battery_used_rate
    = new ArrayList<BatteryUsedRate>();
  static boolean need_update_list_view = false;
  static BroadcastReceiver battery_changed_receiver 
    = new BatteryChangedReceiver();
  static FixFIFO<BatteryInfo> FIFO_history = null;
  static boolean is_service_foreground = false;
  static boolean is_TTS_checked = false;
  static boolean is_notification_init = false;
  static int power_supply_state = POWER_SUPPLY_STATE_UNKNOWN;

  static TextToSpeech TTS = null;
  static AudioManager audio_manager = null;

  static boolean battery_info_to_battery_use_rate()
  {
    if (need_update_list_view && FIFO_history != null
        && adapter_battery_used_rate != null)
    {
      battery_used_rate.clear();
      if (FIFO_history.used_size() > 1)
      {
        ListIterator<BatteryInfo> it = FIFO_history.Iterator();
        BatteryInfo prev = it.next();
        BatteryInfo now;
        while(it.hasNext())
        {
          now = it.next();
          BatteryUsedRate used_rate = new BatteryUsedRate();
          used_rate.time = now.time;
          used_rate.level = prev.level;
          used_rate.is_charging = now.is_charging;
          used_rate.rate = (float)(now.level - prev.level) 
            / (now.time - prev.time) * 1000 * 3600;
          battery_used_rate.add(used_rate);
          prev = now;
        }
        return true;
      }
    }
    return false;
  }

  public static int change_volume(int stream, int volume)
  {
    if (audio_manager != null)
    {
      int prev_volume = audio_manager.getStreamVolume(stream);
      int max_volume = audio_manager.getStreamMaxVolume(stream);
      if (volume > max_volume)
        volume = max_volume;
      audio_manager.setStreamVolume(stream, volume, 0);
      logd("change_volume", "volume is change to ", str(volume));
      return prev_volume;
    }
    return 0;
  }

  public static int change_volume(int stream, float scale)
  {
    if (audio_manager != null)
    {
      int max_volume = audio_manager.getStreamMaxVolume(stream);
      return change_volume(stream, (int)(max_volume * scale));
    }
    return 0;
  }


  public static void say(String what)
  {
    if (TTS != null)
    {
      new SpeakThread().execute(what);
    }
  }
}

class SpeakThread extends AsyncTask<String, Void, Boolean>
{
  @Override
  protected Boolean doInBackground(String ... params)
  {
    if (Common.TTS == null)
      return false;
    int prev_volume = Common.change_volume(AudioManager.STREAM_MUSIC, 0.7f);
    Common.TTS.speak(params[0], TextToSpeech.QUEUE_FLUSH, null);
    try
    {
      Thread.sleep(5000, 0);
    }
    catch (Exception e)
    {
      logexception(this, e);
    }
    Common.change_volume(AudioManager.STREAM_MUSIC, prev_volume);
    return true;
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
      charge_state = "charging";
    else
      charge_state = "discharge";
    vh.lbl_left.setText(String.format("%s %3d%% %10s", 
        format_ticket(bur.time, "yy-MM-dd HH:mm:ss"), bur.level, charge_state));
    vh.lbl_right.setText(str(bur.rate, "0.00") + "%/h");
    return row;
  }
}

class BatteryUsedRate
{
  public long time;
  public int level;
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
