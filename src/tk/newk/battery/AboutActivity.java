package tk.newk.battery;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends Activity
{
  @Override
  public void onCreate(Bundle saveInstanceState)
  {
    super.onCreate(saveInstanceState);
    setContentView(R.layout.about);
    TextView lbl_about = (TextView)findViewById(R.id.lbl_about);
    lbl_about.setText(getString(R.string.str_about_name)
      + getString(R.string.app_name) + "\n"
      + getString(R.string.str_about_author) + "newkedison\n"
      + getString(R.string.str_about_version) + "V0.3.5\n"
      + getString(R.string.str_about_open_soure)
      + "\nhttps://github.com/newkedison/android-battery-monitor");
  }

  @Override
  protected void onResume() 
  {
    super.onResume();
  }

  @Override
  protected void onDestroy() 
  {
    super.onDestroy();
  } 

  @Override
  protected void onPause()
  {
    super.onPause();
  }
}


