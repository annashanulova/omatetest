package com.omatetest.makora.omatetest.services;

import android.app.AlarmManager;
import android.app.Notification;
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
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.omatetest.makora.omatetest.MainActivity;
import com.omatetest.makora.omatetest.R;
import com.omatetest.makora.omatetest.utils.DBHelper;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;

public class SensorService extends Service {

    private static final String TAG = "Watch: SensorService";

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGravitySensor;
    private Sensor mLinearAccelerometer;
    private Sensor mLocationSensor;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    //   private WifiManager wifiManager;
    private HandlerThread mAccelThread, mGravityThread, mLinearThread, mGPSThread;
    private final int ACC_DELAY = 20000;
    private final int NOTIFICATION_ID = 42;
    private NotificationManager mNotificationMngr;
    private AlarmManager alarmManager;
    private LocationManager locationManager;
    private GoogleApiClient mClient = null;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private Context mContext;

    private final int GPS_START = 42;
    private final int GPS_STOP = 43;

    public SensorService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAlarms();
        registerSensorListeners();
        buildFitnessClient();
        //open DB
        dbHelper = DBHelper.getInstance(this);
        db = DBHelper.getWritableInstance(this);
        mContext = this.getApplicationContext();
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
       /* mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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
        //wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    }

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
            Log.d(TAG, "inserting into DB: " + values.getAsLong("start") + " " + values.getAsInteger("sensor"));
            db.insertOrThrow("users_motion_raw", null, values);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

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
        //  mNotificationMngr.cancel(NOTIFICATION_ID);
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
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

    /**
     * SOURCE: https://developers.google.com/fit/android/get-started
     * Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     * to connect to Fitness APIs. The scopes included should match the scopes your app needs
     * (see documentation for details). Authentication will occasionally fail intentionally,
     * and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     * can address. Examples of this include the user never having signed in before, or having
     * multiple accounts on the device and needing to specify which account to use, etc.
     */
    private void buildFitnessClient() {
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.RECORDING_API)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.SENSORS_API)
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .setAccountName("makora.ch@gmail.com")
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                //register relevant listeners
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                                //do I need to stop recording? de-register listeners?
                            }
                        }
                )
                .build();
        mClient.connect();
    }

}
