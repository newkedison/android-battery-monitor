package tk.newk.battery;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import android.app.Activity;

import android.os.Bundle;

import android.view.View;

import android.widget.TextView;

import tk.newk.battery.LogActivity;

public class LogActivity extends Activity
{
  TextView lbl_log;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.log);
    lbl_log = (TextView)findViewById(R.id.lbl_log);
  }

  public void update_log(String filter)
  {
    try
    {
      Process process = Runtime.getRuntime().exec("logcat -d " + filter);
      BufferedReader bufferedReader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));
      StringBuilder log = new StringBuilder();
      String line;
      while ((line = bufferedReader.readLine()) != null)
      {
        log.append(line);
      }
      lbl_log.setText(log.toString());
    }
    catch (IOException e)
    { }
  }

  public void update_log_verbose(View v)
  {
    update_log("battery:V *:S");
  }
  public void update_log_debug(View v)
  {
    update_log("battery:D *:S");
  }
  public void update_log_info(View v)
  {
    update_log("battery:I *:S");
  }
  public void update_log_warning(View v)
  {
    update_log("battery:W *:S");
  }
}
