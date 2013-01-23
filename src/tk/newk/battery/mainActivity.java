package tk.newk.battery;

import java.util.Date;

import org.achartengine.model.XYSeries;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;

import android.content.pm.PackageManager.NameNotFoundException;

import android.graphics.Paint.Align;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.widget.TextView;

import org.achartengine.ChartFactory;
import org.achartengine.chart.PointStyle;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYSeriesRenderer;

import android.content.Context;
import android.graphics.Color;

import android.widget.Toast;

import static tk.newk.common.log.*;

import static tk.newk.battery.Common.*;

public class mainActivity extends ListActivity 
{
  private TextView m_lbl_battery_level;
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
    logv(this, "ativity is created");
    setContentView(R.layout.main);
    m_lbl_battery_level = (TextView)findViewById(R.id.lbl_battery_level);
    if (adapter_battery_used_rate == null)
      adapter_battery_used_rate = new MyArrayAdapter(this, battery_used_rate);
    setListAdapter(adapter_battery_used_rate);
    log_set_tag("battery");
    if (global_setting == null)
      global_setting = PreferenceManager.getDefaultSharedPreferences(this);
    boolean service_enable 
      = read_setting_boolean(PREF_KEY_SERVICE_ENABLE, true);
    if (service_enable)
    {
      Intent service_intent = new Intent(this, MonitorService.class);
      startService(service_intent);
    }
  }

  @Override
  public void onResume()
  {
    super.onResume();
    logv(this, "ativity is resume");
    need_update_list_view = true;
    if (update_battery_used_rate_list())
      adapter_battery_used_rate.notifyDataSetChanged();
    lbl_current_battery_state = m_lbl_battery_level;
    lbl_current_battery_state.setText(str_current_battery_state);
  }

  @Override
  public void onPause()
  {
    super.onPause();
    logv(this, "ativity is pause");
    need_update_list_view = false;
    lbl_current_battery_state = null;
  }

  @Override
  public void onStop()
  {
    super.onStop();
    logv(this, "activity is stop");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId())
    {
      case R.id.menu_curve:
        if (battery_used_rate.size() < 1)
        {
          Toast.makeText(this, R.string.str_no_enough_data_for_curve, 5).show();
        }
        else
        {
          startActivity(make_chart_activity_intent(this));
        }
        return true;
//      case R.id.menu_log:
//        Intent open_log = new Intent(this, LogActivity.class);
//        startActivity(open_log);
//        return true;
      case R.id.menu_setting:
        logv(this, "menu_setting is click");
        Intent open_setting = new Intent(this, SettingsActivity.class);
        startActivity(open_setting);
        return true;
      case R.id.menu_about:
        logv(this, "menu_about is click");
        String version_name = "";
        try {
          version_name = getPackageManager().getPackageInfo(
              getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
          version_name = "Unknown";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.app_name) + "("
            + version_name + ")\n"
            + getString(R.string.str_about_author) + "newkedison\n\n"
            + getString(R.string.str_about_open_soure)
            + "\nhttps://github.com/newkedison/android-battery-monitor")
          .setCancelable(false)
          .setNegativeButton(getString(R.string.btn_ok),
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
              }
            });
        AlertDialog alert = builder.create();
        alert.show();
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private Intent make_chart_activity_intent(Context context)
  {
    String title = getString(R.string.str_curve_title);
    int color_level = Color.CYAN;
    int color_rate = Color.GREEN;
    String x_title = getString(R.string.axis_x_time);
    String y_title_level = getString(R.string.axis_y_left_level);
    String y_title_rate = getString(R.string.axis_y_right_rate);
    double ymin = read_setting_int(PREF_KEY_AXIS_LEFT_MIN, 0);
    double ymax = read_setting_int(PREF_KEY_AXIS_LEFT_MAX, 100);
    if (ymin >= ymax)
    {
      loge(this, "ymin >= ymax");
      ymin = 0;
      ymax = 100;
    }
    int list_count = battery_used_rate.size();
    double xmin = battery_used_rate.get(list_count - 1).time;
    double xmax = battery_used_rate.get(0).time;
    double offset = (xmax - xmin) / 10;
    
    PointStyle point_style = PointStyle.POINT;
    XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer(2);
    renderer.setAxisTitleTextSize(25);
    renderer.setChartTitleTextSize(30);
    renderer.setLabelsTextSize(25);
    renderer.setLegendTextSize(25);
    renderer.setPointSize(5f);
    renderer.setMargins(new int[] { 60, 40, 60, 40 });
    XYSeriesRenderer r = new XYSeriesRenderer();
    r.setColor(color_level);
    r.setPointStyle(point_style);
    r.setLineWidth(3f);
    renderer.addSeriesRenderer(r);
    r = new XYSeriesRenderer();
    r.setColor(color_rate);
    r.setPointStyle(point_style);
    r.setLineWidth(3f);
    renderer.addSeriesRenderer(r);
    //set the y grid to 10, if possible
    renderer.setYLabels(10);
    renderer.setShowGrid(true);
    renderer.setPanEnabled(true, false);
    renderer.setPanLimits(
        new double[] {xmin - offset, xmax + offset, ymin, ymax});
    renderer.setZoomEnabled(true, false);
    renderer.setZoomLimits(
        new double[] {xmin - offset, xmax + offset, ymin, ymax});

    renderer.setChartTitle(title);
    renderer.setXTitle(x_title);
    renderer.setYTitle(y_title_level);
    renderer.setYTitle(y_title_rate, 1);

    renderer.setXAxisMin(xmin);
    renderer.setXAxisMax(xmax);
    renderer.setYAxisMin(ymin, 0);
    renderer.setYAxisMax(ymax, 0);

    renderer.setXLabelsAlign(Align.RIGHT);
    renderer.setYLabelsAlign(Align.LEFT, 0);
    renderer.setYLabelsAlign(Align.RIGHT, 1);
    renderer.setYAxisAlign(Align.LEFT, 0);
    renderer.setYAxisAlign(Align.RIGHT, 1);

    renderer.setAxesColor(Color.GRAY);
    renderer.setLabelsColor(Color.LTGRAY);
    renderer.setYLabelsColor(0, color_level);
    renderer.setYLabelsColor(1, color_rate);

    TimeSeries series_level = new TimeSeries(y_title_level);
    XYSeries series_rate = new XYSeries(y_title_rate, 1);
    for (int i = 0; i < list_count; ++i)
    {
      BatteryUsedRate bur = battery_used_rate.get(i);
      series_level.add(new Date(bur.time), bur.level);
      series_rate.add(bur.time, bur.rate);
    }
    double ymin2 = read_setting_int(PREF_KEY_AXIS_RIGHT_MIN, -10);
    double ymax2 = read_setting_int(PREF_KEY_AXIS_RIGHT_MAX, 10);
    if (ymin2 >= ymax2)
    {
      loge(this, "ymin2 >= ymax2");
      ymin2 = -10;
      ymax2 = 10;
    }
    renderer.setYAxisMin(ymin2, 1);
    renderer.setYAxisMax(ymax2, 1);

    XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
    dataset.addSeries(series_level);
    dataset.addSeries(series_rate);
    return ChartFactory.getTimeChartIntent(context, dataset, renderer, "HH:mm");
  }
}

// vim: fdm=syntax fdl=1 fdn=2

