package com.omatetest.makora.omatetest;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.omatetest.makora.omatetest.utils.DBHelper;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;


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
    private TextView tvInfoText, tvStepCount;
    private File mLogFile = null;
    private FileOutputStream mFileStream = null;

    private GoogleApiClient mClient = null;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private static final int REQUEST_OAUTH = 57;
    private Long recordingTimeStart = 1438160400000L;
    private int numberOfSteps = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvInfoText = (TextView) findViewById(R.id.info_text);
        tvStepCount = (TextView) findViewById(R.id.step_count);
        tvStepCount.setText(String.valueOf(numberOfSteps));
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
            // int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext); //returns 0: success
            // Log.d(TAG, "status: " + status);
            if (mClient.isConnected()) {
                Log.d(TAG, "disconnecting GoogleFit client");
                stopGoogleFitRecording(DataType.TYPE_STEP_COUNT_DELTA);
                // stopGoogleFitRecording(DataType.TYPE_DISTANCE_DELTA);
                stopGoogleFitRecording(DataType.TYPE_LOCATION_SAMPLE);
                // stopGoogleFitRecording(DataType.TYPE_SPEED);
                removeGoogleFitSensorListener(DataType.TYPE_STEP_COUNT_DELTA);
                removeGoogleFitSensorListener(DataType.TYPE_LOCATION_SAMPLE);
                mClient.disconnect();
            } else {
                Log.d(TAG, "connecting GoogleFit client");
                recordingTimeStart = System.currentTimeMillis();
                mClient.connect();
            }
         /*   if (isMyServiceRunning(SensorService.class.getName())) {
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
            }*/
        }
    };

    private View.OnClickListener mOnDumpClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
         /*   setupFolderAndFile();
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
            }*/
          /* Cursor cWifi = db.query("users_wifi_bssids",new String[]{"start","bssid","level"},null,null,null,null,null);
            if (cWifi.moveToFirst() ){
                do {
                    Log.d(TAG,"start: " + cWifi.getLong(0) + " bssid: " + cWifi.getString(1) + " level: " + cWifi.getInt(2));
                } while (cAccel.moveToNext());
            }*/
        /*    int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(mContext, R.string.dump_success, duration);
            toast.show();*/
            dumpGoogleFitHistoryData(recordingTimeStart, System.currentTimeMillis());
        }
    };

    private void dumpGoogleFitHistoryData(Long timeStart, Long timeEnd) {
        Log.d(TAG, "dumping GoogleFitHistory data");
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .setTimeRange(timeStart, timeEnd, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .read(DataType.TYPE_DISTANCE_DELTA)
                .read(DataType.TYPE_LOCATION_SAMPLE)
                .read(DataType.TYPE_SPEED)
                .build();
        Fitness.HistoryApi.readData(mClient, readRequest).setResultCallback(new ResultCallback<DataReadResult>() {
            @Override
            public void onResult(DataReadResult dataReadResult) {
                if (dataReadResult != null) {
                    Log.d(TAG, dataReadResult.getStatus().toString());
                    List<DataSet> dataSetList = dataReadResult.getDataSets();
                    for (DataSet ds : dataSetList) {
                        Log.d(TAG, ds.getDataType().getName());
                        Log.d(TAG, "" + ds.getDataPoints().size());
                    }
                } else {
                    Log.d(TAG, "dataReadResult is null");
                }
            }
        });
    }

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
                                Log.d(TAG, "GoogleFit connected");
                                startGoogleFitRecording(DataType.TYPE_STEP_COUNT_DELTA);
                                //    startGoogleFitRecording(DataType.TYPE_DISTANCE_DELTA);
                                startGoogleFitRecording(DataType.TYPE_LOCATION_SAMPLE);
                                //     startGoogleFitRecording(DataType.TYPE_SPEED);
                                addGoogleFitSensorListener(DataType.TYPE_STEP_COUNT_DELTA);
                                addGoogleFitSensorListener(DataType.TYPE_LOCATION_SAMPLE);
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
        Log.d(TAG, "build GoogFit client");
    }

    private void startGoogleFitRecording(DataType dataType) {
        Log.d(TAG, "starting " + dataType.getName() + " recoding");
        Fitness.RecordingApi.subscribe(mClient, dataType)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(TAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(TAG, "Successfully subscribed!");
                            }
                        } else {
                            Log.i(TAG, "There was a problem subscribing.");
                        }
                    }
                });
    }

    private void stopGoogleFitRecording(DataType dataType) {
        Log.d(TAG, "stopping " + dataType.getName() + " recoding");
        Fitness.RecordingApi.unsubscribe(mClient, dataType).
                setResultCallback(new ResultCallback<Status>() {
                                      @Override
                                      public void onResult(Status status) {
                                          if (status.isSuccess()) {
                                              Log.i(TAG, "Successfully unsubscribed for data type " + status.toString());
                                          } else {
                                              // Subscription not removed
                                              Log.i(TAG, "Failed to unsubscribe for data type " + status.toString());
                                          }
                                      }
                                  }

                );
    }

    OnDataPointListener mFitnessDataListener = new OnDataPointListener() {
        @Override
        public void onDataPoint(DataPoint dataPoint) {
            Log.d(TAG, "received data: " + dataPoint.getDataType().getName());
            DataType dataType = dataPoint.getDataType();
            for (Field field : dataPoint.getDataType().getFields()) {
                Value val = dataPoint.getValue(field);
                Log.i(TAG, "Detected DataPoint field: " + field.getName());
                Log.i(TAG, "Detected DataPoint value: " + val);
                if (dataType.getName().equals("com.google.step_count.delta")) {
                    numberOfSteps += val.asInt();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStepCount.setText(String.valueOf(numberOfSteps));
                        }
                    });
                    Log.d(TAG, "new number of steps: " + numberOfSteps);
                }
            }
        }
    };

    private void addGoogleFitSensorListener(DataType dataType) {
        Log.d(TAG, "adding listener to: " + dataType.getName());
        Fitness.SensorsApi.add(
                mClient,
                new SensorRequest.Builder()
                        .setDataType(dataType) // Can't be omitted.
                        .setSamplingRate(500, TimeUnit.MILLISECONDS)
                        .build(),
                mFitnessDataListener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Listener registered!");
                        } else {
                            Log.i(TAG, "Listener not registered.");
                        }
                    }
                });
    }

    private void removeGoogleFitSensorListener(DataType dataType) {

        Log.d(TAG, "removing listener to: " + dataType.getName());

        Fitness.SensorsApi.remove(mClient, mFitnessDataListener)
                .setResultCallback(new ResultCallback<Status>() {
                                       @Override
                                       public void onResult(Status status) {
                                           if (status.isSuccess()) {
                                               Log.i(TAG, "Listener was removed!");
                                           } else {
                                               Log.i(TAG, "Listener was not removed.");
                                           }
                                       }
                                   }

                );
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
