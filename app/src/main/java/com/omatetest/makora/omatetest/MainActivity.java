package com.omatetest.makora.omatetest;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.omatetest.makora.omatetest.utils.DBHelper;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    private final String TAG = "MainActivity";

    private Button mAccel, mWifi, mDump;
    private Boolean mAccelOn = false, mWifiOn = false;
    private Context mContext;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private WifiManager wifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAccel = (Button) findViewById(R.id.accel_button);
        mAccel.setOnClickListener(mOnAccelClickListener);
        mWifi = (Button) findViewById(R.id.wifi_button);
        mWifi.setOnClickListener(mOnWifiClickListener);
        mDump = (Button) findViewById(R.id.dump_button);
        mDump.setOnClickListener(mOnDumpClickListener);
        mContext = getApplicationContext();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        dbHelper = DBHelper.getInstance(mContext);
        db = DBHelper.getWritableInstance(mContext);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

   private View.OnClickListener mOnAccelClickListener = new View.OnClickListener(){

       @Override
       public void onClick(View view) {
           if (!mAccelOn){
               mAccel.setText(R.string.stop_accel);
               mAccelOn = true;
               mSensorManager.registerListener(mAccelListener, mAccelerometer,SensorManager.SENSOR_DELAY_NORMAL);
           } else {
               mAccel.setText(R.string.start_accel);
               mAccelOn = false;
               mSensorManager.unregisterListener(mAccelListener,mAccelerometer);
           }
       }
   };

   private View.OnClickListener mOnWifiClickListener = new View.OnClickListener(){

        @Override
        public void onClick(View view) {
            if (!mWifiOn){
                mWifi.setText(R.string.stop_wifi);
                mWifiOn = true;
                if (wifiManager.isWifiEnabled() == false)
                {
                    Toast.makeText(getApplicationContext(), "wifi is disabled..making it enabled", Toast.LENGTH_LONG).show();
                    wifiManager.setWifiEnabled(true);
                }
                IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                registerReceiver(mWifiScanner,intentFilter);
            } else {
                mWifi.setText(R.string.start_wifi);
                mWifiOn = false;
                unregisterReceiver(mWifiScanner);
            }
        }
    };

    private View.OnClickListener mOnDumpClickListener = new View.OnClickListener(){

        @Override
        public void onClick(View view) {
            Cursor cAccel = db.query("users_acc_raw",new String[]{"start","x","y","z"},null,null,null,null,null);
            if (cAccel.moveToFirst() ){
                do {
                    Log.d(TAG,"start: " + cAccel.getLong(0) + " x: " + cAccel.getFloat(1) + " y: " + cAccel.getFloat(2) + " z: " + cAccel.getFloat(2));
                } while (cAccel.moveToNext());
            }
            Cursor cWifi = db.query("users_wifi_bssids",new String[]{"start","bssid","level"},null,null,null,null,null);
            if (cWifi.moveToFirst() ){
                do {
                    Log.d(TAG,"start: " + cWifi.getLong(0) + " bssid: " + cWifi.getString(1) + " level: " + cWifi.getInt(2));
                } while (cAccel.moveToNext());
            }
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(mContext, R.string.dump_success, duration);
            toast.show();
        }
    };

     private SensorEventListener mAccelListener = new SensorEventListener() {
         @Override
         public final void onSensorChanged(SensorEvent event) {
             float[] eventValues = event.values;
             // Do something with this sensor value.
             ContentValues values = new ContentValues();
             values.put("start", System.currentTimeMillis());
             values.put("x", eventValues[0]);
             values.put("y", eventValues[1]);
             values.put("z", eventValues[2]);
             Log.d(TAG, "inserting into DB: " + Arrays.toString(eventValues));
             db.insertOrThrow("users_acc_raw", null, values);
         }

         @Override
         public void onAccuracyChanged(Sensor sensor, int i) {

         }
     };

        private BroadcastReceiver mWifiScanner = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent)
            {
                List<ScanResult> results = wifiManager.getScanResults();
                int size = results.size();
                for (int i = 0; i< size; i++){
                    ContentValues values = new ContentValues();
                    values.put("start",System.currentTimeMillis());
                    values.put("bssid",results.get(i).BSSID);
                    values.put("level",results.get(i).level);
                    db.insertOrThrow("users_wifi_bssids",null,values);
                }
            }
        };



}
