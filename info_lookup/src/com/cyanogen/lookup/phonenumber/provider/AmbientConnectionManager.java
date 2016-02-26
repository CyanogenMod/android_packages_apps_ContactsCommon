package com.cyanogen.lookup.phonenumber.provider;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.AmbientApiClient.OnDisconnectionListener;
import com.cyanogen.ambient.common.api.AmbientApiClient.OnConnectionFailedListener;
import com.cyanogen.ambient.common.api.AmbientApiClient.ConnectionCallbacks;
import com.cyanogen.ambient.common.ConnectionResult;

public class AmbientConnectionManager {

    private static final String TAG = "AmbientConnectionManager";
    private static AmbientApiClient sClient;
    private static int mActiveSubscribers = 0;

    public static synchronized AmbientApiClient requestClient(Context context) {
        if (sClient == null) {
            sClient =
                    new AmbientApiClient.Builder(context.getApplicationContext())
                            .addApi(CallerInfoServices.API).build();
            sClient.registerConnectionFailedListener(
                    new OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult result) {
                            Log.w(TAG, "CallerInfo connection failed: " + result);
                        }
                    });
            sClient.registerDisconnectionListener(
                    new OnDisconnectionListener() {
                        @Override
                        public void onDisconnection() {
                            Log.d(TAG, "CallerInfo connection disconnected");
                        }
                    });
            sClient.registerConnectionCallbacks(
                    new ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle connectionHint) {
                            Log.d(TAG, "CallerInfo connection established");
                        }

                        @Override
                        public void onConnectionSuspended(int cause) {
                            Log.d(TAG, "CallerInfo connection suspended");
                        }
                    });
        }

        if (!(sClient.isConnecting() || sClient.isConnected())) {
            sClient.connect();
        }

        ++ mActiveSubscribers;
        return sClient;
    }

    /**
     * Convenience method to access the AmbientApiClient instance after creating it
     * via {@link #requestClient(Context)}
     *
     * This method should only be used *after* ensuring that {@link #requestClient(Context)} has
     * been called.{@link #discardClient()} should be called in the appropriate place to manage
     * the lifecycle of the AmbientApiClient according to the lifecycle of components
     * making use of it.
     */
    public static AmbientApiClient getClient() {
        return sClient;
    }

    public static synchronized void discardClient() {
        -- mActiveSubscribers;
        if (mActiveSubscribers == 0) {
            // disconnect from AmbientClient when the last subscriber disconnects
            sClient.disconnect();
        }
    }

    public static boolean isAvailable(Context context) {
        boolean ambientAvailable = CyanogenAmbientUtil.isCyanogenAmbientAvailable(context) == CyanogenAmbientUtil.SUCCESS;
        boolean activeProvider = CallerInfoHelper.hasActiveProvider(context);
        Log.d(TAG, "ambientAvailable=" + ambientAvailable +
                ", activeProvider=" + activeProvider);
        return ambientAvailable && activeProvider;
    }

}