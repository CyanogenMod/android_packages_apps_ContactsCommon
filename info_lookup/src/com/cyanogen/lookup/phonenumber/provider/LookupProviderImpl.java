package com.cyanogen.lookup.phonenumber.provider;

import android.content.Context;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.extension.CallerInfo;
import com.cyanogen.ambient.callerinfo.results.LookupByNumberResult;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * @author Rohit Yengisetty
 */
public class LookupProviderImpl extends LookupProvider {

    // TODO: move this back to CallerInfoHelper and add 'observable' functionality
    // for Provider Info changes
    private static final String PROVIDER_KEY = "ambient_callerinfo_provider_name";

    private Context mContext;
    private AmbientApiClient mAmbientClient;
    private ProviderInfo mProviderInfo;
    private ContentObserver mProviderObserver;
    private Handler mHandler;
    private String mCurrentProviderName;


    public LookupProviderImpl(Context context, Handler handler) {
        super(context);
        mHandler = handler;
    }

    @Override
    public void initialize() {
        if (AmbientConnectionManager.isAvailable(mContext)) {
            mAmbientClient = AmbientConnectionManager.getClient(mContext);
        }

        mProviderInfo = CallerInfoHelper.getActiveProviderInfo(mContext);
        mCurrentProviderName = Settings.Secure.getString(mContext.getContentResolver(), PROVIDER_KEY);
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
                    System.out.println("Uri change : " + uri);
                    // TODO : figure out what to do
                }
            }
        };
        Uri providerInfoUri = Settings.Secure.getUriFor(PROVIDER_KEY);
        mContext.getContentResolver().registerContentObserver(providerInfoUri, false, mProviderObserver);

    }

    @Override
    public LookupResponse fetchInfo(final LookupRequest request) {
        String number = request.mPhoneNumber;


        if (!TextUtils.isEmpty(number) && mAmbientClient != null &&
                (mAmbientClient.isConnecting() || mAmbientClient.isConnected())) {

            final String normalizedNumber = number; // PhoneNumberUtils.formatNumberToE164(number, countryIso);
            com.cyanogen.ambient.callerinfo.extension.LookupRequest ambientRequest =
                    new com.cyanogen.ambient.callerinfo.extension.LookupRequest(normalizedNumber,
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

                    // exit if caller info search didn't yield usable results
//                            if (callerInfo == null || !hasUsableInfo(callerInfo)) {
                    if (callerInfo == null) {
                        return;
                    }

                    // map caller info to LookupResponse
                    LookupResponse lookupResponse = new LookupResponse();
                    lookupResponse.mName = callerInfo.getName();
                    lookupResponse.mAttributionLogo = mProviderInfo.getBadgeLogo();

                    request.mCallback.onNewInfo(request, lookupResponse);
                }
            });
        }

        // TODO : make the IPC call synchronous ? Can't promise AmbientClient will be connected and
        // ready
        return null;
    }

    @Override
    public void disable() {
        if(mAmbientClient != null) {
            mAmbientClient.disconnect();
        }
        mContext.getContentResolver().unregisterContentObserver(mProviderObserver);
    }
}
