package tk.newk.battery;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

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
  final static String PREF_KEY_INTERVAL = "pref_interval";

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

  static TextToSpeech TTS = null;
  static AudioManager audio_manager = null;

  static SharedPreferences global_setting = null;
  static Context service_context = null;

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

  public static boolean parse_buffer_data_to_used_rate()
  {
    if (need_update_list_view && buffer_data_used_size > 2
        && adapter_battery_used_rate != null)
    {
      battery_used_rate.clear();
      int interval = Integer.parseInt(
          global_setting.getString(PREF_KEY_INTERVAL, "5"));
      int list_count = Integer.parseInt(
          global_setting.getString(PREF_KEY_LIST_SIZE, "200"));
      Calendar calendar = Calendar.getInstance();
      int minute = calendar.get(Calendar.MINUTE);
      calendar.set(Calendar.MINUTE, minute - (minute % interval));
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
      //for test
//      int second = calendar.get(Calendar.SECOND);
//      calendar.set(Calendar.SECOND, second - (second % interval));

      //need to read one more data, because we need it to calculate the rate
      //the last element will be delete after all work done
      battery_used_rate.ensureCapacity(list_count + 1);
      BatteryUsedRate bur = null;
      BatteryUsedRate bur_prev = null;
      int list_index = 0;
      BatteryInfo bi = null;
      BatteryInfo bi_next = null;
      float[] levels = new float[list_count + 1];
      _read_buffer read_buffer = new _read_buffer();
      try
      {
        //read one more point from the history
        while (list_index < list_count + 1)
        {
          bur = new BatteryUsedRate();
          bur.time = calendar.getTime().getTime();
          if (bi_next != null)
            bi = bi_next;
          else
            bi = read_buffer.get();
//          logv("bi", bi.toString());
          while (bur.time > bi.time)
          {
            calendar.add(Calendar.MINUTE, -interval);
            //for test
//            calendar.add(Calendar.SECOND, -interval);
            bur.time = calendar.getTime().getTime();
          }
//          logv("bur.time", format_ticket(bur.time, "yy-MM-dd HH:mm:ss"));
          bi_next = read_buffer.get();
//          logv("bi_next", bi_next.toString());
          while (bi_next.time >= bur.time)
          {
            bi = bi_next;
            bi_next = read_buffer.get();
            logv("bi_next", bi_next.toString());
          }
          bur.is_charging = bi.is_charging;
          bur.level = bi.level;
          battery_used_rate.add(bur);
          //calculate the exact level in that point
          levels[list_index] = 
            (float)(bi.level - bi_next.level) * (bur.time - bi_next.time)
            / (bi.time - bi_next.time) 
            + bi_next.level;
//          logv("parse", str(list_index), format_ticket(bur.time, "yy-MM-dd HH:mm:ss"), str(levels[list_index]), str(bi.level), str(bi_next.level), str(bur.time), str(bi_next.time), str(bi.time), str(bi_next.time), str(bi_next.level));
          if (list_index > 0)
          {
            bur_prev = battery_used_rate.get(list_index - 1);
            bur_prev.rate = (levels[list_index - 1] - levels[list_index]) 
              / (bur_prev.time - bur.time) * 3600L * 1000L;
//            logv("parse", str(bur_prev.rate));
          }
          ++list_index;
        }
      }
      catch (FileNotFoundException e)
      {}
      //delete the last point
      int last = battery_used_rate.size() - 1;
      if (last >= 0 && battery_used_rate.get(last).rate == 0)
        battery_used_rate.remove(last);
      return true;
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
    vh.lbl_left.setText(String.format("%3d %s %4.1f%% %10s", position + 1, 
        format_ticket(bur.time, "yy-MM-dd HH:mm:ss"), bur.level, charge_state));
    vh.lbl_right.setText(str(bur.rate, "0.00") + "%/h");
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

// vim: fdm=syntax fdl=1 fdn=2
