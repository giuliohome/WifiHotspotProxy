package com.wordpress.giuliohome.httpproxy;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "ProxyServer";

    private TextView mInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInfo = (TextView)findViewById(R.id.info);
        Log.i(TAG, "\nService starting");

        Intent intent = new Intent(this, ProxyService.class);
        startService(intent);
        Log.i(TAG, "\nService starting");
        mInfo.setText("Service starting");
    }


    private boolean mBound;

}
