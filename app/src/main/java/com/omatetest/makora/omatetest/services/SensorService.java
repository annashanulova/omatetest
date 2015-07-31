package com.omatetest.makora.omatetest.services;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;

import com.omatetest.makora.omatetest.MainActivity;
import com.omatetest.makora.omatetest.R;
import com.omatetest.makora.omatetest.utils.DBHelper;

import net.sqlcipher.database.SQLiteDatabase;

public class SensorService extends Service {

    private static final String TAG = "Watch: SensorService";
    private final int ACC_DELAY = 20000;  //50Hz
    private final int NOTIFICATION_ID = 42;
    private final int GPS_START = 42;
    private final int GPS_STOP = 43;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGravitySensor;
    private Sensor mLinearAccelerometer;
    private Sensor mLocationSensor;
    private Sensor mGyroscope;
    private Sensor mGeomagneticRotation;
    private Sensor mMagneticField;
    private Sensor mRotationSensor;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    //   private WifiManager wifiManager;
    private HandlerThread mAccelThread, mGravityThread, mLinearThread, mGPSThread, mGyroscopeThread, mGeomagneticRotThread, mMagneticThread, mRotationThread;
    private NotificationManager mNotificationMngr;
    private AlarmManager alarmManager;
    private LocationManager locationManager;
    private Context mContext;
    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public final void onSensorChanged(SensorEvent event) {
            float[] eventValues = event.values;
            Log.d(TAG, "sensor: " + event.sensor.getType());
            ContentValues values = new ContentValues();
            values.put("start", System.currentTimeMillis());
            values.put("sensor", event.sensor.getType()); //1: Accelerometer, 9: gravity, 10: linear accelerometer
            values.put("x", eventValues[0]);
            values.put("y", eventValues[1]);
            values.put("z", eventValues[2]);
            Log.d(TAG, "inserting into DB: " + values.toString());
            if (db == null) {
                db = DBHelper.getWritableInstance(mContext);
            }
            db.insertOrThrow("users_sensors_raw", null, values);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };
    private LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {

        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    public SensorService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //open DB
        dbHelper = DBHelper.getInstance(this);
        db = DBHelper.getWritableInstance(this);
        mContext = this.getApplicationContext();
        //  startAlarms();
        registerSensorListeners();
        Log.d(TAG, "starting service");
        return START_STICKY;
    }

    private void startAlarms() {
        alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        Intent intentStart = new Intent(this, AlarmReceiver.class);
        intentStart.putExtra("type", GPS_START);
        PendingIntent gpsStartIntent = PendingIntent.getBroadcast(this, GPS_START, intentStart, PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_HALF_HOUR,
                AlarmManager.INTERVAL_HALF_HOUR, gpsStartIntent);
        Intent intentStop = new Intent(this, AlarmReceiver.class);
        intentStart.putExtra("type", GPS_STOP);
        PendingIntent gpsStopIntent = PendingIntent.getBroadcast(this, GPS_STOP, intentStop, PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_HALF_HOUR + (10 * 60 * 1000),
                AlarmManager.INTERVAL_HALF_HOUR, gpsStopIntent);
    }

    private void registerSensorListeners() {
        //register listeners
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //register accelerometer
      /*  mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccelThread = new HandlerThread("AccelerometerListener");
        mAccelThread.start();
        Handler accelHandler = new Handler(mAccelThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mAccelerometer, ACC_DELAY, accelHandler);
        //register gravity
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mGravityThread = new HandlerThread("GravityListener");
        mGravityThread.start();
        Handler gravityHandler = new Handler(mGravityThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mGravitySensor, ACC_DELAY, gravityHandler);*/
        //register linear accelerometer
        mLinearAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mLinearThread = new HandlerThread("LinearAccelerometerListener");
        mLinearThread.start();
        Handler linearHandler = new Handler(mLinearThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mLinearAccelerometer, ACC_DELAY, linearHandler);
        //register gyroscope
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGyroscopeThread = new HandlerThread("GyroscopeListener");
        mGyroscopeThread.start();
        Handler gyroscopeHandler = new Handler(mGyroscopeThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mGyroscope, ACC_DELAY, gyroscopeHandler);
        //register magnetic field sensor
        mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mMagneticThread = new HandlerThread("MagneticFieldListener");
        mMagneticThread.start();
        Handler magneticHandler = new Handler(mMagneticThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mMagneticField, ACC_DELAY, magneticHandler);
        //register rotation sensor
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mRotationThread = new HandlerThread("RotationListener");
        mRotationThread.start();
        Handler rotationHandler = new Handler(mRotationThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mRotationSensor, ACC_DELAY, rotationHandler);
        //register geomagnetic rotation sensor
        mGeomagneticRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        mGeomagneticRotThread = new HandlerThread("GeomagneticRotationListener");
        mGeomagneticRotThread.start();
        Handler rotHandler = new Handler(mGeomagneticRotThread.getLooper());
        mSensorManager.registerListener(mSensorListener, mGeomagneticRotation, ACC_DELAY, rotHandler);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroying service");
     /*   mSensorManager.unregisterListener(mSensorListener, mAccelerometer);
        if (mAccelThread.isAlive()) {
            mAccelThread.quit();
        }
        mSensorManager.unregisterListener(mSensorListener, mGravitySensor);
        if (mGravityThread.isAlive()) {
            mGravityThread.quit();
        }*/
        mSensorManager.unregisterListener(mSensorListener, mLinearAccelerometer);
        if (mLinearThread.isAlive()) {
            mLinearThread.quit();
        }
        mSensorManager.unregisterListener(mSensorListener, mGyroscope);
        if (mGyroscopeThread.isAlive()) {
            mGyroscopeThread.quit();
        }
        mSensorManager.unregisterListener(mSensorListener, mRotationSensor);
        if (mRotationThread.isAlive()) {
            mRotationThread.quit();
        }
        mSensorManager.unregisterListener(mSensorListener, mGeomagneticRotation);
        if (mGeomagneticRotThread.isAlive()) {
            mGeomagneticRotThread.quit();
        }
        mSensorManager.unregisterListener(mSensorListener, mMagneticField);
        if (mMagneticThread.isAlive()) {
            mMagneticThread.quit();
        }
        //  mNotificationMngr.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    private void showNotification() {
        mNotificationMngr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        CharSequence text = getText(R.string.sensor_service_running);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_name)
                        .setContentTitle("Omatetest service")
                        .setContentText(text);

        // The PendingIntent to launch our activity if the user selects this notification
        Intent startAppIntent = new Intent(this, MainActivity.class);
        PendingIntent startAppPending;
        startAppPending = PendingIntent.getActivity(this, 0, startAppIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        mBuilder.setContentIntent(startAppPending);
        mBuilder.setAutoCancel(false);
        mBuilder.setOngoing(true);

        // Set the info for the views that show in the notification panel.

        // Send the notification.
        mNotificationMngr.notify(NOTIFICATION_ID, mBuilder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra("type", GPS_STOP)) {
                case GPS_START:
                    if (mGPSThread != null && (!mGPSThread.isAlive())) {
                        mGPSThread = new HandlerThread("GPS Listener");
                        mGPSThread.start();
                        Handler GPSHandler = new Handler(mGPSThread.getLooper());
                        //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener,GPSHandler);
                    }
                    break;
                case GPS_STOP:
                    if (mGPSThread != null && mGPSThread.isAlive()) {
                        mGPSThread.quit();
                    }
                    break;
            }
        }
    }


}
