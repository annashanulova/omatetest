package com.omatetest.makora.omatetest;

import android.content.Context;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {

    private Button mAccel, mWifi, mDump;
    private Boolean mAccelOn = false, mWifiOn = false;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAccel = (Button) findViewById(R.id.accel_button);
        mAccel.setOnClickListener(mOnAccelClickListener);
        mWifi = (Button) findViewById(R.id.wifi_button);
        mWifi.setOnClickListener(mOnWifiClickListener);
        mDump = (Button) findViewById(R.id.dump_button);
        mDump.setOnClickListener(mOnDumpClickListener);
        mContext = getApplicationContext();
    }

   private View.OnClickListener mOnAccelClickListener = new View.OnClickListener(){

       @Override
       public void onClick(View view) {
           if (!mAccelOn){
               mAccel.setText(R.string.stop_accel);
               mAccelOn = true;
           } else {
               mAccel.setText(R.string.start_accel);
               mAccelOn = false;
           }
       }
   };

   private View.OnClickListener mOnWifiClickListener = new View.OnClickListener(){

        @Override
        public void onClick(View view) {
            if (!mWifiOn){
                mWifi.setText(R.string.stop_accel);
                mWifiOn = true;
            } else {
                mWifi.setText(R.string.start_accel);
                mWifiOn = false;
            }
        }
    };

    private View.OnClickListener mOnDumpClickListener = new View.OnClickListener(){

        @Override
        public void onClick(View view) {
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(mContext, R.string.dump_success, duration);
            toast.show();
        }
    };

}
