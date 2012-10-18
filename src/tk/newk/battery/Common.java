package tk.newk.battery;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import android.content.Context;
import android.content.Intent;

import android.os.BatteryManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;
import android.widget.TextView;

//import static tk.newk.common.log.*;
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
  //the size of FIFO array, recent data is save in this array
  final static int FIFO_SIZE = 200;
  //buffer how many recored before save the buffer data to real file
  final static int SAVE_TO_FILE_STEP = 20;

  static BatteryInfo[] buffer_data = new BatteryInfo[HISTORY_FILE_RECORD_COUNT];
  static int buffer_data_used_size = 0;
  static int recent_file_record_count = 0;
  static int latest_history_index = -1;
  static MyArrayAdapter adapter_battery_used_rate = null;
  static ArrayList<BatteryUsedRate> battery_used_rate
    = new ArrayList<BatteryUsedRate>();
  static boolean need_update_list_view = false;
}

class MyArrayAdapter extends ArrayAdapter<BatteryUsedRate>
{
  private Context m_context;
  private List<BatteryUsedRate> m_data;
  public MyArrayAdapter(Context context, List<BatteryUsedRate> data)
  {
    super(context, R.layout.list_item_small, data);
    m_context = context;
    m_data = data;
  }
  @Override
  public View getView(int position, View convertView, ViewGroup parent)
  {
    LayoutInflater inflater = (LayoutInflater) m_context
      .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View rowView = inflater.inflate(R.layout.list_item_small, parent, false);
    TextView lbl_time = (TextView) rowView.findViewById(R.id.lbl_time);
    TextView lbl_rate = (TextView) rowView.findViewById(R.id.lbl_rate);
    BatteryUsedRate bur = m_data.get(position);
    lbl_time.setText(format_ticket(bur.time, "yy-MM-dd HH:mm:ss"));
    lbl_rate.setText(str(bur.rate, "0.00") + "%/h");
    return rowView;
  }
}

class BatteryUsedRate
{
  public long time;
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
  public void add(T o)
  {
    m_queue.add(0, o);
    if (m_queue.size() > m_size)
    {
      //remove nearly 1/5 of all elements from the list, FIFO
      //because insert to the first, wo must delete from the end of list
      int delete_count = (m_size + 4) / 5;
      for (int i = 0; i < delete_count; ++i)
      {
        m_queue.remove(m_queue.size() - 1);
      }
    }
  }
  public int size()
  {
    return m_queue.size();
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
  public long time;
  public BatteryInfo(Intent intent)
  {
    if (intent != null)
    {
      state = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
      is_charging = state == BatteryManager.BATTERY_STATUS_CHARGING ||
                            state == BatteryManager.BATTERY_STATUS_FULL;
      level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
//      int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    }
    time = ticket();
  }
  public String toString()
  {
    String ret = String.format("[%s]State=%d, Level=%d, ", 
        format_ticket(time, "yy-MM-dd HH:mm:ss"), state, level);
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
      is_charging = state == BatteryManager.BATTERY_STATUS_CHARGING ||
                            state == BatteryManager.BATTERY_STATUS_FULL;
      level = data[10];
    }
  }
}
