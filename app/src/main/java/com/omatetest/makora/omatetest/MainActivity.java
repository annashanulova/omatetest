package com.omatetest.makora.omatetest;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.omatetest.makora.omatetest.services.SensorService;
import com.omatetest.makora.omatetest.utils.DBHelper;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


public class MainActivity extends ActionBarActivity {

    private final String TAG = "Watch: MainActivity";

    private final String APP_LOG_FOLDER_NAME = "mySensorListener";

    private Button mDump, mServiceButton;
    private Boolean mServiceOn = false;
    private Context mContext;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private WifiManager wifiManager;
    private ActivityManager activityManager;
    private TextView tvInfoText;
    private File mLogFile = null;
    private FileOutputStream mFileStream = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvInfoText = (TextView) findViewById(R.id.info_text);
        mServiceButton = (Button) findViewById(R.id.start_service_button);
        mServiceButton.setOnClickListener(mOnStartServiceListener);
        mDump = (Button) findViewById(R.id.dump_button);
        mDump.setOnClickListener(mOnDumpClickListener);
        mContext = getApplicationContext();
        dbHelper = DBHelper.getInstance(mContext);
        db = DBHelper.getWritableInstance(mContext);
    }

    private View.OnClickListener mOnStartServiceListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "clicked the button");
            if (isMyServiceRunning(SensorService.class.getName())) {
                Intent intent = new Intent(mContext, SensorService.class);
                stopService(intent);
                tvInfoText.setText(R.string.service_inactive);
                mServiceButton.setText(R.string.start_service);
            } else {
                Intent intent = new Intent(mContext, SensorService.class);
                startService(intent);
                tvInfoText.setText(R.string.service_running);
                mServiceButton.setText(R.string.stop_service);
            }
        }
    };

    private View.OnClickListener mOnDumpClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            setupFolderAndFile();
            final int TIME_INDEX = 0;
            final int SENSOR_INDEX = 1;
            final int X_INDEX = 2;
            final int Y_INDEX = 3;
            final int Z_INDEX = 4;
            Cursor cAccel = db.query("users_motion_raw", new String[]{"start", "sensor", "x", "y", "z"}, null, null, null, null, null);
            if (cAccel.moveToFirst()) {
                // Log.d(TAG, "start: " + cAccel.getLong(0) + " sensor: " + cAccel.getInt(1) + " x: " + cAccel.getFloat(2) + " y: " + cAccel.getFloat(3) + " z: " + cAccel.getFloat(4));
                String header = "Timestamp (millisecs)" + "\t" + "Sensor" + "\t" + "x" + "\t" + "y" + "\t" + "z" + "\r\n";
                Log.d(TAG, header);
                try {
                    mFileStream.write(header.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                do {
                    String formatted = String.valueOf(cAccel.getLong(TIME_INDEX))
                            + "\t" + cAccel.getString(SENSOR_INDEX)
                            + "\t" + String.valueOf(cAccel.getFloat(X_INDEX))
                            + "\t" + String.valueOf(cAccel.getFloat(Y_INDEX))
                            + "\t" + String.valueOf(cAccel.getFloat(Z_INDEX))
                            + "\r\n";
                    Log.d(TAG, formatted);
                    try {
                        mFileStream.write(formatted.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } while (cAccel.moveToNext());
            }
         /*   Cursor cWifi = db.query("users_wifi_bssids",new String[]{"start","bssid","level"},null,null,null,null,null);
            if (cWifi.moveToFirst() ){
                do {
                    Log.d(TAG,"start: " + cWifi.getLong(0) + " bssid: " + cWifi.getString(1) + " level: " + cWifi.getInt(2));
                } while (cAccel.moveToNext());
            }*/
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(mContext, R.string.dump_success, duration);
            toast.show();
        }
    };

    private boolean isMyServiceRunning(String serviceClassName) {

        ActivityManager manager = (ActivityManager) this
                .getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager
                .getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClassName.equals(
                    service.service.getClassName())) {
                Log.i(TAG, "isMyServiceRunning( " + serviceClassName + " )-> TRUE EOF");
                return true;
            }
        }
        return false;
    }

    /**
     * Sets up folder and file to log the file on it
     * http://pastebin.com/QuHd0LNU
     */
    private void setupFolderAndFile() {

        Log.d(TAG, "setting up log file");
        File folder = new File(Environment.getExternalStorageDirectory()
                + File.separator + APP_LOG_FOLDER_NAME);
        Log.d(TAG, Environment.getExternalStorageDirectory()
                + File.separator + APP_LOG_FOLDER_NAME);

        if (!folder.exists()) {
            Boolean success = folder.mkdirs();
            Log.d(TAG, "success " + success);
        } else {
            Log.d(TAG, "Folder exists");
        }

        mLogFile = new File(Environment.getExternalStorageDirectory().toString()
                + File.separator + APP_LOG_FOLDER_NAME
                + File.separator + "log_dump_"
                + System.currentTimeMillis());

        if (!mLogFile.exists()) {
            try {
                mLogFile.createNewFile();
            } catch (IOException e) {
                Log.d(TAG, "Problem opening a new file " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (mFileStream == null) {
            try {
                mFileStream = new FileOutputStream(mLogFile, true);
            } catch (FileNotFoundException e) {
                Log.d(TAG, "Problem creating output stream " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

  /* private View.OnClickListener mOnWifiClickListener = new View.OnClickListener(){

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
    };*/

    /*private BroadcastReceiver mWifiScanner = new BroadcastReceiver()
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

   */


}
