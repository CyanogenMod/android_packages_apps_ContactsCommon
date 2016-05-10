/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogen.lookup.phonenumber.provider;

import android.content.Context;

import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import com.android.contacts.common.util.SingletonHolder;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.extension.CallerInfo;
import com.cyanogen.ambient.callerinfo.results.LookupByNumberResult;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.PluginStatus;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.callerinfo.util.ProviderUpdateListener;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.CommonStatusCodes;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.Result;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.common.api.Status;
import com.cyanogen.lookup.phonenumber.contract.LookupProvider;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;
import com.cyanogen.lookup.phonenumber.response.StatusCode;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link LookupProvider} that delegates calls to Ambient
 */
public class LookupProviderImpl implements LookupProvider {

    public static final SingletonHolder.RefCountedSingletonHolder<LookupProviderImpl, Context>
            INSTANCE =
                new SingletonHolder.RefCountedSingletonHolder<LookupProviderImpl, Context>() {
                    @Override
                    protected LookupProviderImpl create(Context context) {
                        return new LookupProviderImpl(context.getApplicationContext());
                    }

                    @Override
                    protected void destroy(LookupProviderImpl instance) {
                        instance.disable();
                    }
                };

    private static final String TAG = LookupProviderImpl.class.getSimpleName();

    private final Context mContext;
    private final Handler mMainHandler;
    private final HashSet<StatusCallback> mStatusChangeCallbacks;

    private AmbientApiClient mAmbientClient;
    private ProviderInfo mProviderInfo;
    private ContentObserver mProviderObserver;
    private ProviderUpdateListener mProviderUpdateListener;
    private Object mLock = new Object();

    private LookupProviderImpl(Context context) {
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());
        mStatusChangeCallbacks = new HashSet<StatusCallback>();

        if (AmbientConnectionManager.isAvailable(mContext)) {
            mAmbientClient = AmbientConnectionManager.requestClient(mContext);
            mProviderInfo = CallerInfoHelper.getActiveProviderInfo2(mContext);
            mProviderUpdateListener = new ProviderUpdateListener(mContext,
                    new ProviderUpdateListener.Callback() {
                        @Override
                        public void onProviderChanged(ProviderInfo providerInfo) {
                            synchronized (mLock) {
                                boolean isProviderEnabled;
                                if (providerInfo != null &&
                                        providerInfo.getStatus() == PluginStatus.ACTIVE) {
                                    mProviderInfo = providerInfo;
                                    isProviderEnabled = true;
                                } else {
                                    mProviderInfo = null;
                                    isProviderEnabled = false;
                                }
                                // notify callbacks
                                for (StatusCallback callback : mStatusChangeCallbacks) {
                                    callback.onStatusChanged(isProviderEnabled);
                                }
                            }
                        }
                    });
        }
    }

    @Override
    public void registerStatusCallback(StatusCallback callback) {
        if (callback != null) {
            mStatusChangeCallbacks.add(callback);

        }
    }

    @Override
    public void unregisterStatusCallback(StatusCallback callback) {
        mStatusChangeCallbacks.remove(callback);
    }

    @Override
    public boolean isEnabled() {
        synchronized (mLock) {
            return mProviderInfo != null;
        }
    }

    @Override
    public void fetchInfo(final LookupRequest request) {
        PendingResult<LookupByNumberResult> pendingResult = issueAmbientRequest(request);
        if (pendingResult != null) {
            pendingResult.setResultCallback(new ResultCallback<LookupByNumberResult>() {
                @Override
                public void onResult(LookupByNumberResult lookupByNumberResult) {
                    LookupResponse lookupResponse = createLookupResponse(lookupByNumberResult);
                    request.mCallback.onNewInfo(request, lookupResponse);
                }
            });
        }
    }

    @Override
    public LookupResponse blockingFetchInfo(final LookupRequest request) {
        PendingResult<LookupByNumberResult> pendingResult = issueAmbientRequest(request);
        if (pendingResult != null) {
            LookupByNumberResult lookupResult = pendingResult.await(5L, TimeUnit.SECONDS);
            return createLookupResponse(lookupResult);
        }

        return null;
    }

    private PendingResult<LookupByNumberResult> issueAmbientRequest(LookupRequest request) {
        String number = request.mPhoneNumber;
        synchronized (mLock) {
            if (mProviderInfo != null && !TextUtils.isEmpty(number) && mAmbientClient != null &&
                    (mAmbientClient.isConnecting() || mAmbientClient.isConnected())) {

                int originCode;
                switch (request.mRequestOrigin) {
                    case INCOMING_CALL:
                        originCode = com.cyanogen.ambient.callerinfo.extension.LookupRequest.ORIGIN_CODE_INCOMING_CALL;
                        break;
                    case OUTGOING_CALL:
                        originCode = com.cyanogen.ambient.callerinfo.extension.LookupRequest.ORIGIN_CODE_OUTGOING_CALL;
                        break;
                    case INCOMING_SMS:
                        originCode = com.cyanogen.ambient.callerinfo.extension.LookupRequest.ORIGIN_CODE_INCOMING_SMS;
                        break;
                    case OUTGOING_SMS:
                        originCode = com.cyanogen.ambient.callerinfo.extension.LookupRequest.ORIGIN_CODE_OUTGOING_SMS;
                        break;
                    default:
                        originCode = com.cyanogen.ambient.callerinfo.extension.LookupRequest.ORIGIN_CODE_HISTORY;
                        break;
                }
                com.cyanogen.ambient.callerinfo.extension.LookupRequest ambientRequest =
                        new com.cyanogen.ambient.callerinfo.extension.LookupRequest(number,
                                com.cyanogen.ambient.callerinfo.extension.LookupRequest.ORIGIN_CODE_HISTORY);
                PendingResult<LookupByNumberResult> result = CallerInfoServices.CallerInfoApi.
                        lookupByNumber(mAmbientClient, ambientRequest);

                return result;
            }

            return null;
        }
    }

    private LookupResponse createLookupResponse(
            LookupByNumberResult lookupByNumberResult) {
        synchronized (mLock) {
            if (mProviderInfo == null) {
                // lookup provider has been inactivated
                return null;
            }
            LookupResponse lookupResponse = new LookupResponse();
            CallerInfo callerInfo = lookupByNumberResult.getCallerInfo();
            int lookupStatusCode = lookupByNumberResult.getStatus().getStatusCode();

            // always include Provider information
            lookupResponse.mProviderName = mProviderInfo.getTitle();
            lookupResponse.mAttributionLogo = mProviderInfo.getBadgeLogo();

            if (lookupStatusCode == CommonStatusCodes.RESOLUTION_REQUIRED) {
                lookupResponse.mStatusCode = StatusCode.CONFIG_ERROR;
            } else if (!lookupByNumberResult.getStatus().isSuccess()) {
                lookupResponse.mStatusCode = StatusCode.FAIL;
            } else if (!hasUsableInfo(callerInfo)) {
                lookupResponse.mStatusCode = StatusCode.NO_RESULT;
            } else {
                lookupResponse.mStatusCode = StatusCode.SUCCESS;
                // map CallerInfo to LookupResponse
                lookupResponse.mName = callerInfo.getName();
                lookupResponse.mNumber = callerInfo.getNumber();
                lookupResponse.mAddress = callerInfo.getAddress();
                lookupResponse.mPhotoUrl = callerInfo.getPhotoUrl();
                lookupResponse.mSpamCount = callerInfo.getSpamCount();
                lookupResponse.mIsSpam = callerInfo.isSpam();
            }

            return lookupResponse;
        }
    }

    private boolean hasUsableInfo(CallerInfo callerInfo) {
        return (callerInfo != null &&
                (!TextUtils.isEmpty(callerInfo.getName()) || callerInfo.getSpamCount() > 0));
    }

    private void disable() {
        // most of the fields are initialized only if the provider is enabled
        // check the validity of the fields before attempting clean-up
        if(mAmbientClient != null) {
            AmbientConnectionManager.discardClient();
        }
        if (mProviderUpdateListener != null) {
            mProviderUpdateListener.destroy();
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
                        Log.d(TAG, "Status: " + status.getStatusMessage());
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
                        Log.d(TAG, "Status: " + status.getStatusMessage());
                    }
                }
            });
        }
    }

    @Override
    public boolean supportsSpamReporting() {
        synchronized (mLock) {
            if (mProviderInfo != null) {
                return mProviderInfo.hasProperty(ProviderInfo.PROPERTY_SUPPORTS_SPAM);
            } else {
                return false;
            }
        }
    }

    @Override
    public String getDisplayName() {
        synchronized (mLock) {
            if (mProviderInfo != null) {
                return mProviderInfo.getTitle();
            } else {
                return null;
            }
        }
    }
    @Override
    public String getUniqueIdentifier() {
        synchronized (mLock) {
            if (mProviderInfo != null) {
                return mProviderInfo.getPackageName();
            }
            return null;
        }
    }

}
