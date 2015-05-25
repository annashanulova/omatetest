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
import android.os.Handler;
import android.os.HandlerThread;
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
    private HandlerThread mAccelThread, mGravityThread, mLinearThread;
    private int ACC_DELAY = 34000;

    public SensorService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //start alarms
        //register listeners
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //register accelerometer
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccelThread = new HandlerThread("AccelerometerListener");
        mAccelThread.start();
        Handler accelHandler = new Handler(mAccelThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mAccelerometer, ACC_DELAY, accelHandler);
        //register gravity
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mGravityThread = new HandlerThread("GravityListener");
        mGravityThread.start();
        Handler gravityHandler = new Handler(mGravityThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mGravitySensor, ACC_DELAY, gravityHandler);
        //register linear accelerometer
        mLinearAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mLinearThread = new HandlerThread("LinearAccelerometerListener");
        mLinearThread.start();
        Handler linearHandler = new Handler(mLinearThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mLinearAccelerometer, ACC_DELAY,linearHandler);
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

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroying service");
        mSensorManager.unregisterListener(mSensorListener, mAccelerometer);
        if (mAccelThread.isAlive()){
            mAccelThread.quit();
        }
        mSensorManager.unregisterListener(mSensorListener, mGravitySensor);
        if (mGravityThread.isAlive()){
            mGravityThread.quit();
        }
        mSensorManager.unregisterListener(mSensorListener, mLinearAccelerometer);
        if (mLinearThread.isAlive()){
            mLinearThread.quit();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
