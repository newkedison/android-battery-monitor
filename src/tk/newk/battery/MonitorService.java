package tk.newk.battery;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.util.Locale;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.media.AudioManager;

import android.os.IBinder;

import android.preference.PreferenceManager;

import android.speech.tts.TextToSpeech;

import android.support.v4.app.TaskStackBuilder;

import android.widget.Toast;

import static tk.newk.common.log.*;
import static tk.newk.common.utils.*;

import tk.newk.battery.Common;
import static tk.newk.battery.Common.*;

import android.support.v4.app.NotificationCompat;

public class MonitorService extends Service
  implements TextToSpeech.OnInitListener
{

  void load_recently_history()
  {
    InputStream is = null;
    byte[] buf = new byte[RECORD_LENGTH];
    buffer_data_used_size = 0;
    try
    {
      is = new BufferedInputStream(openFileInput(FILE_NAME_RECENT));
      int l = 0;
      do
      {
        l = is.read(buf, 0, RECORD_LENGTH);
        if (l > 0)
        {
          Common.buffer_data[buffer_data_used_size] = new BatteryInfo(null);
          Common.buffer_data[buffer_data_used_size].update_from_bytes(buf);
          ++buffer_data_used_size;
        }
      }while (l == RECORD_LENGTH);
      recent_file_record_count = buffer_data_used_size;
      if (is != null)
        is.close();
    }
    catch (Exception e)
    {
      logexception(this, e);
    }
  }

  @Override
  public void onCreate() 
  {
    log_set_tag("battery");
    load_recently_history();
    if (TTS == null)
      TTS = new TextToSpeech(this, this);
    audio_manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
    if (FIFO_history == null)
    {
      SharedPreferences shared_pref 
        = PreferenceManager.getDefaultSharedPreferences(this);
      int list_size = Integer.parseInt(shared_pref.getString(
            PREF_KEY_LIST_SIZE, "200"));
      FIFO_history = new FixFIFO<BatteryInfo>(list_size);
    }
    logv(this, "service is created");
  }

  int find_latest_history_index()
  {
    logd(this, str(latest_history_index));
    if (latest_history_index >= 0)
      return latest_history_index;
    String[] file_name_list = fileList();
    int index = 0;
    int i = 0;
    String file_name;
    boolean is_found;
    int ret = index;
    do
    {
      file_name = String.format(FILE_NAME_HISTORY, index);
      is_found = false;
      for (i = 0; i < file_name_list.length; ++i)
      {
        if (file_name.equals(file_name_list[i]))
        {
          ret = index;
          is_found = true;
          break;
        }
      }
      ++index;
    }while(is_found);
    return ret;
  }

  boolean copy_file(String from_file, String to_file)
  {
    InputStream is = null;
    FileOutputStream fos = null;
    byte[] buf = new byte[1024];
    int read_len;
    try
    {
      is = new BufferedInputStream(openFileInput(from_file));
      fos = openFileOutput(to_file, Context.MODE_PRIVATE);
      do
      {
        read_len = is.read(buf, 0, 1024);
        if (read_len > 0)
          fos.write(buf, 0, read_len);
      }while(read_len > 0);
      is.close();
      fos.close();
      return true;
    }
    catch (Exception e)
    {
      logexception(this, e);
      return false;
    }

  }

  void save_history(BatteryInfo bi)
  {
    logd(this, "saving batter info", str(buffer_data_used_size));
    buffer_data[buffer_data_used_size] = bi;
    ++buffer_data_used_size;
    //save buffer data to file when buffer SAVE_TO_FILE_STEP record
    //this prevent form writing to file too many times
    if (buffer_data_used_size > recent_file_record_count
        && buffer_data_used_size % SAVE_TO_FILE_STEP == 0)
    {
      append_records_to_file(FILE_NAME_RECENT, recent_file_record_count, -1);
      recent_file_record_count = buffer_data_used_size;
    }
    //when the next save action to recent file will reach the max size of history file, we must transfer the recent file to history file
    if (recent_file_record_count + SAVE_TO_FILE_STEP 
        > HISTORY_FILE_RECORD_COUNT)
    {
      logd(this, "start to copy recent file to history");
      latest_history_index = find_latest_history_index();
      String file_name = String.format(FILE_NAME_HISTORY, 
          latest_history_index + 1);
      logd(this, "target file name", file_name);
      if (copy_file(FILE_NAME_RECENT, file_name))
        ++latest_history_index;
      //delete recent file and clear buffer data
      deleteFile(FILE_NAME_RECENT);
      //don't need to actually delete the buffer data, only change index is OK
      //if want to reduce memory used, you can assign all elements to null,
      //but I think is not necessary because the buffer_data array is not so big
      buffer_data_used_size = recent_file_record_count = 0;
    }
  }

  void update_history_list(BatteryInfo bi)
  {
    if (bi != null && FIFO_history != null)
    {
      FIFO_history.add(bi);
      logv(this, "history FIFO", str(FIFO_history.used_size()), 
          str(FIFO_history.capacity()));
    }
    if (battery_info_to_battery_use_rate())
      adapter_battery_used_rate.notifyDataSetChanged();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int start_id)
  {
    logv(this, "service is start");
    if (intent != null && intent.getBooleanExtra(
          Common.START_SERVICE_FROM_RECEIVER, false))
    {
      BatteryInfo bi = new BatteryInfo(intent);
      logv(this, bi.toString());
      if (!is_notification_init)
      {
        update_notification(bi);
        if (!is_service_foreground && m_builder != null)
        {
          this.startForeground(ID_NOTIFICATION, m_builder.build());
          is_service_foreground = true;
        }
        is_notification_init = true;
      }
      if (FIFO_history != null && FIFO_history.used_size() > 0 
          && bi.level == FIFO_history.get(0).level)
      {
        logd(this, "battery level no change, ignored");
      }
      else
      {
        save_history(bi);
        update_history_list(bi);
        logv(this, "start to update notification");
        update_notification(bi);
        if (/*!is_service_foreground && */m_builder != null)
        {
          this.startForeground(ID_NOTIFICATION, m_builder.build());
          is_service_foreground = true;
        }
      }
    }
    if (!is_receiver_registed)
    {
      logd(this, "registing receiver");
      this.registerReceiver(battery_changed_receiver, 
          new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      is_receiver_registed = true;
    }
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent)
  {
    return null;
  }

  void append_records_to_file(String file_name, int start, int len)
  {
    if (start >= buffer_data_used_size || len == 0)
      return;
    if (len < 0 || start + len > buffer_data_used_size)
      len = buffer_data_used_size - start;

    logd(this, String.format("appending %d + %d to %s", start, len, file_name));
    FileOutputStream fos = null;
    try
    {
      fos = openFileOutput(file_name, Context.MODE_APPEND);
      //make all write data to the buf[] array before write to file
      byte[] buf = new byte[RECORD_LENGTH * len];
      byte[] record = new byte[RECORD_LENGTH];
      for (int i = 0; i < len; ++i)
      {
        record = buffer_data[start + i].to_bytes();
        for (int j = 0; j < record.length; ++j)
          buf[i * RECORD_LENGTH + j] = record[j];
      }
      fos.write(buf);
      fos.close();
    }
    catch(Exception e)
    {
      logexception(this, e);
    }

  }

  @Override
  public void onDestroy()
  {
    if (is_receiver_registed)
    {
      unregisterReceiver(battery_changed_receiver);
      is_receiver_registed = false;
    }
    if (buffer_data_used_size > recent_file_record_count)
    {
      logd("saving unsaved record before service stopped");
      append_records_to_file(FILE_NAME_RECENT, recent_file_record_count, -1);
    }
    TTS = null;
    stopForeground(false);
    is_service_foreground = false;
    NotificationManager nm = (NotificationManager)getSystemService(
        Context.NOTIFICATION_SERVICE);
    nm.cancelAll();
    is_notification_init = false;
    logw(this, "service is Destroy");
  }

  int get_icon_id(int level)
  {
    if (level < 0 || level > 100)
      return R.drawable.red_000;
    if (level <= 20)
      return R.drawable.red_000 + level;
    if (level <= 40)
      return R.drawable.yellow_020 + level - 20;
    return R.drawable.blue_040 + level - 40;
  }

  private NotificationCompat.Builder m_builder;

  void update_notification(BatteryInfo bi)
  {
    if (m_builder == null)
    {
      m_builder = new NotificationCompat.Builder(this);
      Intent open_intent = new Intent(this, mainActivity.class);
      TaskStackBuilder stack_builder = TaskStackBuilder.create(this);
      stack_builder.addParentStack(mainActivity.class);
      stack_builder.addNextIntent(open_intent);
      PendingIntent pending_intent = stack_builder.getPendingIntent(
          0,
          PendingIntent.FLAG_UPDATE_CURRENT);
      m_builder.setContentIntent(pending_intent);
    }
    if (bi != null)
    {
      int icon_id = get_icon_id(bi.level);
      m_builder.setSmallIcon(icon_id)
          .setContentTitle(String.format("Level: %02d%%", bi.level))
          .setContentText("click to see more detail")
          .setOngoing(true);
      NotificationManager nm = (NotificationManager)getSystemService(
          Context.NOTIFICATION_SERVICE);
      nm.notify(ID_NOTIFICATION, m_builder.build());
      if (bi.level < 50 && bi.level % 10 == 0)
      {
        say(String.format("battery level is %d percent", bi.level));
      }
      if (bi.level == 25 
          && power_supply_state == POWER_SUPPLY_STATE_DISCONNECTED)
      {
        say("battery level is low, please charge");
      }
      if (bi.level == 15 
          && power_supply_state == POWER_SUPPLY_STATE_DISCONNECTED)
      {
        say("battery level is very low, please charge immediately");
      }
      if (bi.level == 100 && power_supply_state == POWER_SUPPLY_STATE_CONNECTED)
      {
        say("I am full");
      }
    }
  }

  // interface TextToSpeech.OnInitListener
  public void onInit(int initStatus) 
  {
    //check for successful instantiation
    if (initStatus == TextToSpeech.SUCCESS) 
    {
      if(TTS != null 
          && TTS.isLanguageAvailable(Locale.US) == TextToSpeech.LANG_AVAILABLE)
        TTS.setLanguage(Locale.US);
    }
    else if (initStatus == TextToSpeech.ERROR) 
    {
      Toast.makeText(this, "Sorry! Text To Speech failed...", 
          Toast.LENGTH_LONG).show();
    }
  }
}

// vim: fdm=syntax fdl=1 fdn=2

