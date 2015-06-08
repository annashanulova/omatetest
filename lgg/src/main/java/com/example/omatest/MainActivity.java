package com.example.omatest;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.omatest.services.SensorService;
import com.example.omatest.utils.DBHelper;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
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
    private int ACC_DELAY = 34000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rect_activity_main);
        tvInfoText = (TextView) findViewById(R.id.info_text);
        mServiceButton = (Button) findViewById(R.id.start_service_button);
        mServiceButton.setOnClickListener(mOnStartServiceListener);
        mDump = (Button) findViewById(R.id.dump_button);
        // mDump.setOnClickListener(mOnDumpClickListener);
        mContext = getApplicationContext();
        dbHelper = DBHelper.getInstance(mContext);
        db = DBHelper.getWritableInstance(mContext);
    }

    private View.OnClickListener mOnStartServiceListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "clicked the button");
        /*    if (isMyServiceRunning(SensorService.class.getName())) {
                Intent intent = new Intent(mContext, SensorService.class);
                stopService(intent);
                tvInfoText.setText(R.string.service_inactive);
                mServiceButton.setText(R.string.start_service);
            } else {
                Intent intent = new Intent(mContext, SensorService.class);
                ComponentName serviceName = startService(intent);
                if (serviceName !=null) {
                    Log.d(TAG, serviceName.toString());
                } else {
                    Log.d(TAG,"service not started");
                }
                tvInfoText.setText(R.string.service_running);
                mServiceButton.setText(R.string.stop_service);
            }
        }*/
            SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            HandlerThread mAccelThread = null;
            Sensor mAccelerometer = null;
            List<Sensor> listSensor
                    = mSensorManager.getSensorList(Sensor.TYPE_ALL);

            List<String> listSensorType = new ArrayList<String>();
            for(int i=0; i<listSensor.size(); i++){
               // listSensorType.add(listSensor.get(i).getName());
                Log.d(TAG,listSensor.get(i).getName());
            }
            if (!mServiceOn) {
               // mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
                mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (mAccelerometer == null){
                    Log.d(TAG,"accelerometer is null");
                } else {
                    Log.d(TAG, "type of accel: " + mAccelerometer.isWakeUpSensor());
                }
                mAccelThread = new HandlerThread("AccelerometerListener");
                mAccelThread.start();
                Handler accelHandler = new Handler(mAccelThread.getLooper());
                mSensorManager.registerListener(mSensorListener, mAccelerometer, ACC_DELAY, accelHandler);
                mServiceOn = true;
            } else {
                mSensorManager.unregisterListener(mSensorListener, mAccelerometer);
                if (mAccelThread.isAlive()) {
                    mAccelThread.quit();
                }
                mServiceOn = false;
            }
        }
    };

        private SensorEventListener mSensorListener = new SensorEventListener() {
            @Override
            public final void onSensorChanged(SensorEvent event) {
                float[] eventValues = event.values;
                Log.d(TAG,"sensor: " + event.sensor.getType());
                ContentValues values = new ContentValues();
                values.put("start", System.currentTimeMillis());
                values.put("sensor", event.sensor.getType());
                values.put("x", eventValues[0]);
                values.put("y", eventValues[1]);
                values.put("z", eventValues[2]);
                //  Log.d(TAG, "inserting into DB: " + Arrays.toString(eventValues));
                db.insertOrThrow("users_motion_raw", null, values);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

    private View.OnClickListener mOnDumpClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            setupFolderAndFile();
            final int TIME_INDEX = 0;
            final int SENSOR_INDEX = 0;
            final int X_INDEX = 0;
            final int Y_INDEX = 0;
            final int Z_INDEX = 0;
            Cursor cAccel = db.query("users_motion_raw", new String[]{"start", "sensor", "x", "y", "z"}, null, null, null, null, null);
            if (cAccel.moveToFirst()) {
                // Log.d(TAG, "start: " + cAccel.getLong(0) + " sensor: " + cAccel.getInt(1) + " x: " + cAccel.getFloat(2) + " y: " + cAccel.getFloat(3) + " z: " + cAccel.getFloat(4));

                do {
                    String formatted = String.valueOf(cAccel.getLong(TIME_INDEX))
                            + "\t" + String.valueOf(cAccel.getInt(SENSOR_INDEX))
                            + "\t" + String.valueOf(cAccel.getFloat(X_INDEX))
                            + "\t" + String.valueOf(cAccel.getFloat(Y_INDEX))
                            + "\t" + String.valueOf(cAccel.getFloat(Z_INDEX))
                            + "\r\n";
                    Log.d(TAG,formatted);
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
        for (ActivityManager.RunningServiceInfo service : manager
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

        Log.d(TAG,"setting up log file");
        File folder = new File(Environment.getExternalStorageDirectory()
                + File.separator + APP_LOG_FOLDER_NAME);
        Log.d(TAG,Environment.getExternalStorageDirectory()
                + File.separator + APP_LOG_FOLDER_NAME);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        mLogFile = new File(Environment.getExternalStorageDirectory().toString()
                + File.separator + APP_LOG_FOLDER_NAME
                + File.separator + "log_dump"
                + File.separator + System.currentTimeMillis());

        if (!mLogFile.exists()) {
            try {
                mLogFile.createNewFile();
            } catch (IOException e) {
                Log.d(TAG,"Problem opening a new file " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (mFileStream == null) {
            try {
                mFileStream = new FileOutputStream(mLogFile, true);
            } catch (FileNotFoundException e) {
                Log.d(TAG,"Problem creating output stream " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


}
