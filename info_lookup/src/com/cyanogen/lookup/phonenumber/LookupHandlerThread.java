package com.cyanogen.lookup.phonenumber;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import com.cyanogen.lookup.phonenumber.provider.LookupProvider;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

public class LookupHandlerThread extends HandlerThread implements Handler.Callback {

    private static final int MSG_FETCH_INFO = 0;

    private Context mContext;
    private Handler mHandler;
    private LookupProvider mLookupProvider;

    public LookupHandlerThread(String name, Context ctx) {
        super(name);
        mContext = ctx;
    }

    public LookupHandlerThread(String name, int priority, Context ctx) {
        super(name, priority);
        mContext = ctx;
    }

    public void initialize() {
        start();
        mHandler = new Handler(getLooper(), this);
        // delegate calls to LookupProvider
        mLookupProvider = new LookupProvider(mContext);
    }

    public boolean fetchInfoForPhoneNumber(LookupRequest lookupRequest) {
        // delegate to provider
        Message msg = mHandler.obtainMessage(MSG_FETCH_INFO);
        msg.obj = lookupRequest;
        return mHandler.sendMessage(msg);
    }

    @Override
    public boolean handleMessage(Message msg) {
        int what = msg.what;
        LookupRequest lookupRequest = (LookupRequest) msg.obj;
        switch (what) {
            case MSG_FETCH_INFO :
                if (mLookupProvider != null) {
                    LookupResponse lookupResponse = mLookupProvider.fetchInfo(lookupRequest);
                    // LookupProvider is responsible for notifying CB of fetched info
                    // lookupRequest.mCallback.onNewInfo(lookupRequest, lookupResponse);
                    // TODO : change this ^ to invoke Handler's CB
                }
        }
        return true;
    }
}