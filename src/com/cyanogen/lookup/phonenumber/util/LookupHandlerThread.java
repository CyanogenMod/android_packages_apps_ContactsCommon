package com.cyanogen.lookup.phonenumber.util;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import com.cyanogen.lookup.phonenumber.contract.LookupProvider;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

import java.util.HashSet;

public class LookupHandlerThread extends HandlerThread implements Handler.Callback {

    private static final int MSG_FETCH_INFO = 0;
    private static final int MSG_MARK_AS_SPAM = 1;
    private static final String TAG = LookupHandlerThread.class.getSimpleName();

    private Context mContext;
    private Handler mHandler;
    private LookupProvider mLookupProvider;
    private HashSet<LookupRequest> mSubmittedRequests;
    private boolean mInitialized = false;

    public LookupHandlerThread(String threadName, Context ctx, LookupProvider lookupProvider) {
        super(threadName);
        mContext = ctx;
        mLookupProvider = lookupProvider;
    }

    public LookupHandlerThread(String name, int priority, Context ctx,
            LookupProvider lookupProvider) {
        super(name, priority);
        mContext = ctx;
        mLookupProvider = lookupProvider;
    }

    public boolean initialize() {
        if (mInitialized) {
            return true;
        }

        mInitialized = mLookupProvider.initialize();
        if (mInitialized) {
            mSubmittedRequests = new HashSet<>();
            start();
            mHandler = new Handler(getLooper(), this);
        } else {
            Log.w(TAG, "Failed to initialize!");
        }

        return mInitialized;
    }

    public boolean isProviderEnabled() {
        return mLookupProvider.isEnabled();
    }

    public void tearDown() {
        if (mInitialized) {
            quit();
            mLookupProvider.disable();
            mInitialized = false;
        }
    }

    public LookupResponse blockingFetchInfoForPhoneNumber(LookupRequest lookupRequest) {
        return mLookupProvider.blockingFetchInfo(lookupRequest);
    }

    public boolean fetchInfoForPhoneNumber(LookupRequest lookupRequest) {
        if (!mSubmittedRequests.contains(lookupRequest)) {
            Message msg = mHandler.obtainMessage(MSG_FETCH_INFO);
            msg.obj = lookupRequest;
            boolean requested = mHandler.sendMessage(msg);
            if (requested) {
                mSubmittedRequests.add(lookupRequest);
            }
            return requested;
        }

        return false;
    }

    /**
     * Posts a message to {@link #mHandler} which later dispatches a request to the provider
     * implementation that knows how to mark a phone number as spam
     *
     * @param phoneNumber {@link String}
     */
    public void markAsSpam(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return;
        }
        if (mHandler != null) {
            Message msg = mHandler.obtainMessage(MSG_MARK_AS_SPAM);
            msg.obj = phoneNumber;
            mHandler.sendMessage(msg);
        } else {
            Log.w(TAG, "No handler!");
        }
    }

    /**
     * Check if the provider supports spam reporting
     *
     * @return {@link Boolean}
     */
    public boolean isProviderInterestedInSpam() {
        return mLookupProvider.supportsSpamReporting();
    }

    /**
     * Get the display name of the provider
     *
     * @return {@link String}
     */
    public String getProviderName() {
        return mLookupProvider.getDisplayName();
    }

    @Override
    public boolean handleMessage(Message msg) {
        int what = msg.what;
        switch (what) {
            case MSG_FETCH_INFO :
                if (mInitialized) {
                    mLookupProvider.fetchInfo((LookupRequest) msg.obj);
                }
                break;
            case MSG_MARK_AS_SPAM :
                if (mInitialized) {
                    mLookupProvider.markAsSpam((String) msg.obj);
                }
                break;
        }
        return true;
    }
}
