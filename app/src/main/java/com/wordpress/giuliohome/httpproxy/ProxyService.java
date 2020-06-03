package com.wordpress.giuliohome.httpproxy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;


public class ProxyService extends Service {

    private static WebProxy server = null;

    private static final String TAG = "ProxyServer";
    /** Keep these values up-to-date with PacManager.java */

    @Override
    public void onCreate() {
        Log.i(TAG, "service create");
        super.onCreate();
        if (server == null) {
            Log.i(TAG, "server null");
            server = new WebProxy(this);
            server.start();
            Log.i(TAG, "server started");
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "service destroy");
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "service binding");
        return null;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "service onStartCommand");
        //TODO do something useful
        return Service.START_NOT_STICKY;
    }


}

