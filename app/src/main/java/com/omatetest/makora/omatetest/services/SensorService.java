package com.omatetest.makora.omatetest.services;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import com.omatetest.makora.omatetest.utils.DBHelper;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;

public class SensorService extends Service {

    private static final String TAG = "Watch: SensorService";

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    //   private WifiManager wifiManager;

    public SensorService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //start alarms
        //register listeners
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(mAccelListener, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        //   wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //open DB
        dbHelper = DBHelper.getInstance(this);
        db = DBHelper.getWritableInstance(this);
        return START_STICKY;
    }

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

    @Override
    public void onDestroy() {
        mSensorManager.unregisterListener(mAccelListener, mAccelerometer);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
