package com.omatetest.makora.omatetest;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.omatetest.makora.omatetest.services.SensorService;
import com.omatetest.makora.omatetest.utils.DBHelper;

import net.sqlcipher.Cursor;
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

    private Button mDump, mServiceButton, mWifiScanButton, mSaveTimestampButton;
    private Boolean mServiceOn = false;
    private Boolean mWifiScanOn = false;
    private Context mContext;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private WifiManager wifiManager;
    private ActivityManager activityManager;
    private TextView tvInfoText;
    private EditText etTimestampText;
    private File mLogFile = null;
    private FileOutputStream mFileStream = null;
    private AlarmManager alarmManager;

    private GoogleApiClient mClient = null;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private static final int REQUEST_OAUTH = 57;
    private Long recordingTimeStart = 1438160400000L;
    private int numberOfSteps = 0;
    private final String SCAN_ALARM_INTENT = "initiate_wifi_scan";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvInfoText = (TextView) findViewById(R.id.info_text);
        mWifiScanButton = (Button) findViewById(R.id.scan_wifi);
        mWifiScanButton.setOnClickListener(onWifiScanButtonListener);
        mServiceButton = (Button) findViewById(R.id.start_service_button);
        mServiceButton.setOnClickListener(mOnStartServiceListener);
        etTimestampText = (EditText) findViewById(R.id.timestamp_text);
        mSaveTimestampButton = (Button) findViewById(R.id.save_timestamp);
        mSaveTimestampButton.setOnClickListener(onCustomeTimestampSave);
        mDump = (Button) findViewById(R.id.dump_button);
        mDump.setOnClickListener(mOnDumpClickListener);
        mContext = getApplicationContext();
        dbHelper = DBHelper.getInstance(mContext);
        db = DBHelper.getWritableInstance(mContext);
        wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
        buildFitnessClient();
        alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
    }

    private View.OnClickListener mOnStartServiceListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "clicked the button");
            // int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext); //returns 0: success
            // Log.d(TAG, "status: " + status);
            if (mClient.isConnected()) {
                Log.d(TAG, "disconnecting GoogleFit client");
                removeGoogleFitSensorListener(DataType.TYPE_STEP_COUNT_DELTA);
                removeGoogleFitSensorListener(DataType.TYPE_LOCATION_SAMPLE);
                removeGoogleFitSensorListener(DataType.TYPE_DISTANCE_DELTA);
                removeGoogleFitSensorListener(DataType.TYPE_SPEED);
                mClient.disconnect();
            } else {
                Log.d(TAG, "connecting GoogleFit client");
                recordingTimeStart = System.currentTimeMillis();
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
            setupFolderAndFile("google_fit_steps");
            dumpDBTable("google_fit_steps");
            setupFolderAndFile("google_fit_distance");
            dumpDBTable("google_fit_distance");
            setupFolderAndFile("google_fit_location");
            dumpDBTable("google_fit_location");
            setupFolderAndFile("google_fit_speed");
            dumpDBTable("google_fit_speed");
            setupFolderAndFile("users_sensors_raw");
            dumpDBTable("users_sensors_raw");
            setupFolderAndFile("wifi_scan");
            dumpDBTable("wifi_scan");
            setupFolderAndFile("custom_timestamps");
            dumpDBTable("custom_timestamps");
            Toast toast = Toast.makeText(mContext, R.string.dump_success, Toast.LENGTH_SHORT);
            toast.show();
        }
    };

    View.OnClickListener onCustomeTimestampSave = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String text = etTimestampText.getText().toString();
            if (!text.equals("")) {
                ContentValues values = new ContentValues();
                values.put("timestamp", System.currentTimeMillis());
                values.put("description", text);
                Log.d(TAG, "inserting custom timestamp " + values.toString());
                db.insertOrThrow("custom_timestamps", null, values);
                Toast toast = Toast.makeText(mContext, R.string.ok, Toast.LENGTH_SHORT);
                toast.show();
            } else {
                Log.d(TAG, "EditText is empty");
            }
        }
    };

    private void dumpDBTable(String table_name) {
        Cursor tableC = db.query(table_name, null, null, null, null, null, null);
        String[] columnNames = tableC.getColumnNames();
        String header = new String("");
        for (String name : columnNames) {
            if ((!name.equals("id")) && (!name.equals("uploaded"))) {
                header += name;
                header += ",";
            }
        }
        header = header.substring(0, header.length() - 1);
        header += "\n";
        try {
            mFileStream.write(header.getBytes());
        } catch (IOException e) {
            Log.d(TAG, "Problem writing header to file");
            e.printStackTrace();
        }
        if (table_name.equals("google_fit_steps")) {
            if (tableC.moveToFirst()) {
                do {
                    String row = String.valueOf(tableC.getLong(1)) + "," + String.valueOf(tableC.getInt(2)) + "\n";      //1: timestamp 2:steps
                    try {
                        mFileStream.write(row.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing row to file");
                        e.printStackTrace();
                    }
                } while (tableC.moveToNext());
            }
        } else if (table_name.equals("google_fit_distance")) {
            if (tableC.moveToFirst()) {
                do {
                    String row = String.valueOf(tableC.getLong(1)) + "," + String.valueOf(tableC.getFloat(2)) + "\n";      //1: timestamp 2:distance
                    try {
                        mFileStream.write(row.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing row to file");
                        e.printStackTrace();
                    }
                } while (tableC.moveToNext());
            }
        } else if (table_name.equals("google_fit_speed")) {
            if (tableC.moveToFirst()) {
                do {
                    String row = String.valueOf(tableC.getLong(1)) + "," + String.valueOf(tableC.getFloat(2)) + "\n";      //1: timestamp 2:speed
                    try {
                        mFileStream.write(row.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing row to file");
                        e.printStackTrace();
                    }
                } while (tableC.moveToNext());
            }
        } else if (table_name.equals("google_fit_location")) {
            if (tableC.moveToFirst()) {
                do {
                    String row = String.valueOf(tableC.getLong(1)) + "," + String.valueOf(tableC.getFloat(2)) + "," +
                            String.valueOf(tableC.getFloat(3)) + "," + String.valueOf(tableC.getFloat(4)) + "," +
                            String.valueOf(tableC.getFloat(5)) + "\n";      //1: timestamp 2:lat 3:lon 4:accuracy 5:altitude
                    try {
                        mFileStream.write(row.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing row to file");
                        e.printStackTrace();
                    }
                } while (tableC.moveToNext());
            }
        } else if (table_name.equals("wifi_scan")) {
            if (tableC.moveToFirst()) {
                do {
                    String row = String.valueOf(tableC.getLong(1)) + "," + tableC.getString(2) + "," +
                            String.valueOf(tableC.getFloat(3)) + "," + String.valueOf(tableC.getFloat(4)) + "\n";      //1: timestamp 2:bssid 3:frequency 4:level
                    try {
                        mFileStream.write(row.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing row to file");
                        e.printStackTrace();
                    }
                } while (tableC.moveToNext());
            }
        } else if (table_name.equals("users_sensors_raw")) {
            if (tableC.moveToFirst()) {
                do {
                    String row = String.valueOf(tableC.getInt(1)) + "," + String.valueOf(tableC.getLong(2)) + "," +
                            String.valueOf(tableC.getFloat(3)) + "," + String.valueOf(tableC.getFloat(4)) + "," +
                            String.valueOf(tableC.getFloat(5)) + "\n";      //1: sensor 2:timestamp 3:x 4:y 5:z
                    try {
                        mFileStream.write(row.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing row to file");
                        e.printStackTrace();
                    }
                } while (tableC.moveToNext());
            }
        } else if (table_name.equals("custom_timestamps")) {
            if (tableC.moveToFirst()) {
                do {
                    String row = String.valueOf(tableC.getLong(1)) + "," + tableC.getString(2) + "\n";      //1: timestamp 2:text
                    try {
                        mFileStream.write(row.getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Problem writing row to file");
                        e.printStackTrace();
                    }
                } while (tableC.moveToNext());
            }
        }
        tableC.close();
        Log.d(TAG, "dumped table: " + table_name);
    }

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
    private void setupFolderAndFile(String table_name) {

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
                + File.separator + "log_" + table_name + "_" + System.currentTimeMillis());

        if (!mLogFile.exists()) {
            try {
                mLogFile.createNewFile();
            } catch (IOException e) {
                Log.d(TAG, "Problem opening a new file " + e.getMessage());
                e.printStackTrace();
            }
        }

        mFileStream = null;
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
                                //startGoogleFitRecording(DataType.TYPE_STEP_COUNT_DELTA);
                                //    startGoogleFitRecording(DataType.TYPE_DISTANCE_DELTA);
                                //startGoogleFitRecording(DataType.TYPE_LOCATION_SAMPLE);
                                //     startGoogleFitRecording(DataType.TYPE_SPEED);
                                addGoogleFitSensorListener(DataType.TYPE_STEP_COUNT_DELTA);
                                addGoogleFitSensorListener(DataType.TYPE_LOCATION_SAMPLE);
                                addGoogleFitSensorListener(DataType.TYPE_DISTANCE_DELTA);
                                addGoogleFitSensorListener(DataType.TYPE_SPEED);
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
            ContentValues values = new ContentValues();
            values.put("timestamp", dataPoint.getTimestamp(TimeUnit.MILLISECONDS));
            if (dataType.getName().equals("com.google.step_count.delta")) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    if (field.getName().equals("steps")) {
                        values.put("stepsdelta", dataPoint.getValue(field).asInt());
                    }
                }
                db.insertOrThrow("google_fit_steps", null, values);
            } else if (dataType.getName().equals("com.google.distance.delta")) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    if (field.getName().equals("distance")) {
                        values.put("distancedelta", dataPoint.getValue(field).asFloat());
                    }
                }
                db.insertOrThrow("google_fit_distance", null, values);
            } else if (dataType.getName().equals("com.google.speed")) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    if (field.getName().equals("speed")) {
                        values.put("speedinstant", dataPoint.getValue(field).asFloat());
                    }
                }
                db.insertOrThrow("google_fit_speed", null, values);
            } else if (dataType.getName().equals("com.google.location.sample")) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    if (field.getName().equals("latitude")) {
                        values.put("lat", dataPoint.getValue(field).asFloat());
                    } else if (field.getName().equals("longitude")) {
                        values.put("lon", dataPoint.getValue(field).asFloat());
                    } else if (field.getName().equals("accuracy")) {
                        values.put("accuracy", dataPoint.getValue(field).asFloat());
                    } else if (field.getName().equals("altitude")) {
                        if (!(dataPoint.getValue(field).toString().equals("unset"))) {
                            values.put("altitude", dataPoint.getValue(field).asFloat());
                        }
                    }
                }
                db.insertOrThrow("google_fit_location", null, values);
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


    private View.OnClickListener onWifiScanButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intentStart = new Intent(mContext, MainActivity.class);
            PendingIntent scanStartIntent = PendingIntent.getBroadcast(mContext,0,intentStart,PendingIntent.FLAG_CANCEL_CURRENT);
            if (mWifiScanOn) {
                alarmManager.cancel(scanStartIntent);
                mWifiScanButton.setText(R.string.scan_wifi);
                mWifiScanOn = false;
            } else {
                Long start = System.currentTimeMillis() - 1000;
                Log.d(TAG,"start wifi scan alarm");
                alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, start, (5 * 1000), scanStartIntent);
                mWifiScanButton.setText(R.string.stop_scan);
                mWifiScanOn = true;
            }
        }
    };

    private BroadcastReceiver wifiScanFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            List<ScanResult> wifiResults = wifiManager.getScanResults();
            Long timestamp = System.currentTimeMillis();
            for (ScanResult result : wifiResults) {
                ContentValues values = new ContentValues();
                values.put("timestamp", timestamp);
                values.put("bssid", result.BSSID);
                values.put("frequency", result.frequency);
                values.put("level", result.level);
                Log.d(TAG, "inserting " + values.toString() + "into DB");
                db.insertOrThrow("wifi_scan", null, values);
            }
        }
    };

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }


    @Override
    protected void onStop() {
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter scanFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        this.registerReceiver(wifiScanFinishedReceiver, scanFilter);
        IntentFilter initiateScanFilter = new IntentFilter(this.SCAN_ALARM_INTENT);
        this.registerReceiver(scanAlarmReceiver,initiateScanFilter);
    }

    @Override
    protected void onPause() {
        this.unregisterReceiver(wifiScanFinishedReceiver);
        this.unregisterReceiver(scanAlarmReceiver);
        super.onPause();
    }

    private BroadcastReceiver scanAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "wifi scan alarm received");
            wifiManager.startScan();
            Toast toast = Toast.makeText(mContext, R.string.started_scan, Toast.LENGTH_SHORT);
            toast.show();
        }
    };

}
