package com.cyanogen.lookup.phonenumber.provider;

import android.content.Context;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.extension.CallerInfo;
import com.cyanogen.ambient.callerinfo.results.LookupByNumberResult;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.Result;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.common.api.Status;
import com.cyanogen.lookup.phonenumber.contract.LookupProvider;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * @author Rohit Yengisetty
 */
public class LookupProviderImpl implements LookupProvider {

    private static final String PROVIDER_KEY = "ambient_callerinfo_provider_name";
    private static final String TAG = LookupProviderImpl.class.getSimpleName();

    private Context mContext;
    private AmbientApiClient mAmbientClient;
    private ProviderInfo mProviderInfo;
    private ContentObserver mProviderObserver;
    private Handler mHandler;
    private String mCurrentProviderName;

    public LookupProviderImpl(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean initialize() {
        if (isEnabled()) {
            mAmbientClient = AmbientConnectionManager.requestClient(mContext);
            mProviderInfo = CallerInfoHelper.getActiveProviderInfo(mContext);
            mCurrentProviderName = Settings.Secure.getString(mContext.getContentResolver(),
                    PROVIDER_KEY);

            // update provider info on caller info provider changes
            mProviderObserver = new ContentObserver(mHandler) {
                @Override
                public boolean deliverSelfNotifications() {
                    return false;
                }

                @Override
                public void onChange(boolean selfChange) {
                    onChange(selfChange, null);
                }

                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    if (uri != null) {
                        mProviderInfo = CallerInfoHelper.getActiveProviderInfo(mContext);
                    }
                }
            };
            Uri providerInfoUri = Settings.Secure.getUriFor(PROVIDER_KEY);
            mContext.getContentResolver().registerContentObserver(providerInfoUri, false,
                    mProviderObserver);
            return true;
        }

        return false;
    }

    @Override
    public boolean isEnabled() {
        return AmbientConnectionManager.isAvailable(mContext);
    }

    @Override
    public void fetchInfo(final LookupRequest request) {
        String number = request.mPhoneNumber;
        if (!TextUtils.isEmpty(number) && mAmbientClient != null &&
                (mAmbientClient.isConnecting() || mAmbientClient.isConnected())) {

            com.cyanogen.ambient.callerinfo.extension.LookupRequest ambientRequest =
                    new com.cyanogen.ambient.callerinfo.extension.LookupRequest(number,
                    com.cyanogen.ambient.callerinfo.extension.LookupRequest.ORIGIN_CODE_HISTORY);
            PendingResult<LookupByNumberResult> result = CallerInfoServices.CallerInfoApi.
                    lookupByNumber(mAmbientClient, ambientRequest);

            result.setResultCallback(new ResultCallback<LookupByNumberResult>() {
                @Override
                public void onResult(LookupByNumberResult lookupByNumberResult) {

                    if (!lookupByNumberResult.getStatus().isSuccess()) {
                        return;
                    }
                    CallerInfo callerInfo = lookupByNumberResult.getCallerInfo();

                    if (!hasUsableInfo(callerInfo)) {
                        return;
                    }

                    // map caller info to LookupResponse
                    LookupResponse lookupResponse = new LookupResponse();
                    lookupResponse.mProviderName = mProviderInfo.getTitle();
                    lookupResponse.mName = callerInfo.getName();
                    lookupResponse.mNumber = callerInfo.getNumber();
                    lookupResponse.mAddress = callerInfo.getAddress();
                    lookupResponse.mPhotoUrl = callerInfo.getPhotoUrl();
                    lookupResponse.mAttributionLogo = mProviderInfo.getBadgeLogo();
                    lookupResponse.mSpamCount = callerInfo.getSpamCount();

                    request.mCallback.onNewInfo(request, lookupResponse);
                }
            });
        }
    }

    private boolean hasUsableInfo(CallerInfo callerInfo) {
        return (callerInfo != null &&
                (!TextUtils.isEmpty(callerInfo.getName()) || callerInfo.getSpamCount() > 0));
    }

    @Override
    public void disable() {
        // most of the fields are initialized only if the provider is enabled
        // check the validity of the fields before attempting clean-up
        if(mAmbientClient != null) {
            AmbientConnectionManager.discardClient();
        }
        if (mProviderObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mProviderObserver);
        }
    }

    @Override
    public void markAsSpam(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return;
        }
        if (!supportsSpamReporting()) {
            return;
        }
        if (mAmbientClient != null &&
                (mAmbientClient.isConnecting() || mAmbientClient.isConnected())) {
            PendingResult<Result> result =
                    CallerInfoServices.CallerInfoApi.markAsSpam(mAmbientClient, phoneNumber);
            result.setResultCallback(new ResultCallback<Result>() {
                @Override
                public void onResult(Result result) {
                    Status status = result.getStatus();
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(LookupProviderImpl.class.getSimpleName(),
                                "Status: " + status.getStatusMessage());
                    }
                }
            });
        }
    }

    @Override
    public void unmarkAsSpam(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return;
        }
        if (!supportsSpamReporting()) {
            return;
        }
        if (mAmbientClient != null &&
                (mAmbientClient.isConnecting() || mAmbientClient.isConnected())) {
            PendingResult<Result> result =
                    CallerInfoServices.CallerInfoApi.unMarkAsSpam(mAmbientClient, phoneNumber);
            result.setResultCallback(new ResultCallback<Result>() {
                @Override
                public void onResult(Result result) {
                    Status status = result.getStatus();
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(LookupProviderImpl.class.getSimpleName(),
                                "Status: " + status.getStatusMessage());
                    }
                }
            });
        }
    }

    @Override
    public boolean supportsSpamReporting() {
        ProviderInfo providerInfo = CallerInfoHelper.getActiveProviderInfo(mContext);
        return providerInfo != null &&
                providerInfo.hasProperty(ProviderInfo.PROPERTY_SUPPORTS_SPAM);
    }

    @Override
    public String getDisplayName() {
        String provider = null;
        ProviderInfo providerInfo = CallerInfoHelper.getActiveProviderInfo(mContext);
        if (CyanogenAmbientUtil.isCyanogenAmbientAvailable(mContext) == CyanogenAmbientUtil
                .SUCCESS && providerInfo != null) {
            provider = providerInfo.getTitle();
        }
        return provider;
    }

}
