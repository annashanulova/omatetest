package com.omatetest.makora.omatetest;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.HandlerThread;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.omatetest.makora.omatetest.services.SensorService;
import com.omatetest.makora.omatetest.utils.DBHelper;

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

    private GoogleApiClient mClient = null;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private static final int REQUEST_OAUTH = 57;

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
        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
        buildFitnessClient();
    }

    private View.OnClickListener mOnStartServiceListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "clicked the button");
            if (mClient.isConnected()) {
                Log.d(TAG, "disconnecting GoogleFit client");
                mClient.disconnect();
            } else {
                Log.d(TAG, "connecting GoogleFit client");
                mClient.connect();
            }
            if (isMyServiceRunning(SensorService.class.getName())) {
                Intent intent = new Intent(mContext, SensorService.class);
                stopService(intent);
                tvInfoText.setText(R.string.service_inactive);
                mServiceButton.setText(R.string.start_service);
            } else {
                Intent intent = new Intent(mContext, SensorService.class);
                ComponentName serviceName = startService(intent);
                if (serviceName != null) {
                    Log.d(TAG, serviceName.toString());
                } else {
                    Log.d(TAG, "service not started");
                }
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
            Cursor cAccel = db.query("users_motion_raw", new String[]{"start", "sensor", "x", "y", "z"}, "start > ?", new String[]{"1433306459000"}, null, null, null);
            int count = 0;
            int currentCount = 0;
            if (cAccel.moveToFirst()) {
                // Log.d(TAG, "start: " + cAccel.getLong(0) + " sensor: " + cAccel.getInt(1) + " x: " + cAccel.getFloat(2) + " y: " + cAccel.getFloat(3) + " z: " + cAccel.getFloat(4));
                String header = "Timestamp (millisecs)" + "\t" + "Sensor" + "\t" + "x" + "\t" + "y" + "\t" + "z" + "\r\n";
                Log.d(TAG, "data found!");
                Log.d(TAG, header);
                try {
                    mFileStream.write(header.getBytes());
                } catch (IOException e) {
                    Log.d(TAG, "Problem writing header to file");
                    e.printStackTrace();
                }
                do {
                    currentCount++;
                    if (currentCount == (count + 10)) {
                        count = currentCount;
                        Log.d(TAG, "data points: " + count);
                    }
                    String formatted = String.valueOf(cAccel.getLong(TIME_INDEX))
                            + "\t" + cAccel.getString(SENSOR_INDEX)
                            + "\t" + String.valueOf(cAccel.getFloat(X_INDEX))
                            + "\t" + String.valueOf(cAccel.getFloat(Y_INDEX))
                            + "\t" + String.valueOf(cAccel.getFloat(Z_INDEX))
                            + "\r\n";
                    // Log.d(TAG, formatted);
                    try {
                        mFileStream.write(formatted.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing to file!");
                        e.printStackTrace();
                    }
                } while (cAccel.moveToNext());
            }
          /* Cursor cWifi = db.query("users_wifi_bssids",new String[]{"start","bssid","level"},null,null,null,null,null);
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
                + File.separator + "log_dump"
                + File.separator + System.currentTimeMillis());

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
        Log.d(TAG, "building fitness client");
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
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            MainActivity.this, 0).show();
                                    Log.i(TAG, "no resolution to GoogleFit connection failure");
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        Log.i(TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(MainActivity.this,
                                                REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.e(TAG,
                                                "Exception while starting resolution activity", e);
                                    }
                                }
                            }
                        }
                )
                .build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }

   /* private void checkTypeOfAccel() {
        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        HandlerThread mAccelThread = null;
        Sensor mAccelerometer = null;
        List<Sensor> listSensor
                = mSensorManager.getSensorList(Sensor.TYPE_ALL);

        List<String> listSensorType = new ArrayList<String>();
        for (int i = 0; i < listSensor.size(); i++) {
            // listSensorType.add(listSensor.get(i).getName());
            Log.d(TAG, listSensor.get(i).getName());
        }*/
         /*   if (!mServiceOn) {*/
     /*   mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true);
        //  mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccelerometer == null) {
            Log.d(TAG, "accelerometer is null");
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }
        //   } else {
        Log.d(TAG, "type of accel: " + mAccelerometer.isWakeUpSensor());
        //   }
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
        //  mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccelerometer == null) {
            Log.d(TAG, "accelerometer is null");
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        //   } else {
        Log.d(TAG, "type of accel: " + mAccelerometer.isWakeUpSensor());
    }*/

    @Override
    protected void onStop() {
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
        super.onStop();
    }


}
