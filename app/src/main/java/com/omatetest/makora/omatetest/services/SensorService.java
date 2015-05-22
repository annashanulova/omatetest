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
    private Sensor mGravitySensor;
    private Sensor mLinearAccelerometer;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    //   private WifiManager wifiManager;
    private int ACC_DELAY = SensorManager.SENSOR_DELAY_GAME;

    public SensorService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //start alarms
        //register listeners
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(mSensorListener, mAccelerometer, ACC_DELAY);
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorManager.registerListener(mSensorListener, mGravitySensor, ACC_DELAY);
        mLinearAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener(mSensorListener, mLinearAccelerometer, ACC_DELAY);
        //wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //open DB
        dbHelper = DBHelper.getInstance(this);
        db = DBHelper.getWritableInstance(this);
        Log.d(TAG, "starting service");
        return START_STICKY;
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public final void onSensorChanged(SensorEvent event) {
            float[] eventValues = event.values;
            // Do something with this sensor value.
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

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroying service");
        mSensorManager.unregisterListener(mSensorListener, mAccelerometer);
        mSensorManager.unregisterListener(mSensorListener, mGravitySensor);
        mSensorManager.unregisterListener(mSensorListener, mLinearAccelerometer);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
