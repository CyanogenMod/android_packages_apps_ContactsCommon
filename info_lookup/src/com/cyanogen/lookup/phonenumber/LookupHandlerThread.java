package com.cyanogen.lookup.phonenumber;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import com.cyanogen.lookup.phonenumber.provider.LookupProviderImpl;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;

import java.lang.Override;
import java.util.HashSet;

public class LookupHandlerThread extends HandlerThread implements Handler.Callback {

    private static final int MSG_FETCH_INFO = 0;

    private Context mContext;
    private Handler mHandler;
    private LookupProviderImpl mLookupProvider;
    private HashSet<LookupRequest> mSubmittedRequests;
    private HashSet<LookupRequest> mBufferedRequests;

    public LookupHandlerThread(String name, Context ctx) {
        super(name);
        mContext = ctx;
    }

    public LookupHandlerThread(String name, int priority, Context ctx) {
        super(name, priority);
        mContext = ctx;
    }

    public boolean initialize() {
        mLookupProvider = new LookupProviderImpl(mContext, null);
        boolean isSuccessful = mLookupProvider.initialize();
        if (isSuccessful) {
            mSubmittedRequests = new HashSet<>();
            mBufferedRequests = new HashSet<>();
            start();
        } else {
            mLookupProvider = null;
        }

        return isSuccessful;
    }

    @Override
    protected synchronized void onLooperPrepared() {
        mHandler = new Handler(getLooper(), this);
        // process buffered requests
        for (LookupRequest request : mBufferedRequests) {
            submitLookupRequest(request);
        }

        mBufferedRequests = null;
    }

    public synchronized boolean fetchInfoForPhoneNumber(LookupRequest lookupRequest) {
        if (mHandler == null) {  // queue requests if handler hasn't been initialized yet
            if (!mBufferedRequests.contains(lookupRequest)) {
                mBufferedRequests.add(lookupRequest);
                return true;
            }
        } else {
            if (!mSubmittedRequests.contains(lookupRequest)) {
                if (submitLookupRequest(lookupRequest)) {
                    mSubmittedRequests.add(lookupRequest);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean submitLookupRequest(LookupRequest lookupRequest) {
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
                    mLookupProvider.fetchInfo(lookupRequest);
                }
        }
        return true;
    }
}