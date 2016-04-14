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

package com.android.contacts.common.util;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.telephony.PhoneNumberUtils;

import com.android.contacts.common.activity.fragment.BlockContactDialogFragment;
import com.android.contacts.common.model.BlockRequest;
import com.android.contacts.common.model.Contact;
import com.android.internal.telephony.util.BlacklistUtils;
import com.cyanogen.lookup.phonenumber.contract.LookupProvider;
import com.cyanogen.lookup.phonenumber.provider.LookupProviderImpl;

/**
 * Helper class used to interface with the framework implementation of Blacklist and delegating
 * the apropos information to the active phonenumber LookupProvider, if any.
 *
 * Ensure that {@link #destroy()} is called so that the necessary resource cleanup
 * takes place
 */
public class BlockContactHelper {
    private final Context mContext;
    private AsyncTask mBackgroundTask;
    private BlockRequest mBlockRequest;
    private LookupProvider mLookupProvider;
    private volatile boolean mIsBlacklisted;
    private volatile boolean mIsProviderInitialized;
    private boolean mBackgroundTaskCompleted;
    private StatusCallbacks mListener;

    public enum BlockOperation {
        BLOCK,
        UNBLOCK
    }

    public BlockContactHelper(Context context, LookupProvider lookupProvider) {
        mContext = context;
        mLookupProvider = lookupProvider;
    }

    public void setContactInfo(Contact contact) {
        mBlockRequest = BlockRequest.createFrom(mContext, contact);
        gatherDataInBackgroundAsync();
    }

    public void setContactInfo(String phoneNumber) {
        mBlockRequest = BlockRequest.createFrom(mContext, phoneNumber);
        gatherDataInBackgroundAsync();
    }

    public void setStatusListener(StatusCallbacks listener) {
        mListener = listener;
    }

    public void gatherDataInBackgroundAsync() {
        mBackgroundTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                gatherDataInBackground();
                mBackgroundTaskCompleted = true;
                return null;
            }

            @Override
            protected void onPostExecute(Void Void) {
                if (mListener != null) {
                    mListener.onInfoAvailable();
                }
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    public void gatherDataInBackground() {
        if (mBlockRequest == null || mBlockRequest == BlockRequest.EMPTY) {
            return;
        }

        mIsBlacklisted = false;

        // check blacklist status of all of the contact's numbers
        for (String phoneNumber : mBlockRequest.phoneNumbers) {
            if (isBlacklisted(phoneNumber)) {
                mIsBlacklisted = true;
                break;
            }
        }

        if (mLookupProvider.initialize()) {
            mIsProviderInitialized = true;
        }
    }

    public boolean isContactBlacklisted() {
        return mIsBlacklisted;
    }

    public boolean canBlockContact(Context context) {
        boolean isBlacklistEnabled = BlacklistUtils.isBlacklistEnabled(context);
        return isBlacklistEnabled && mBlockRequest != null && mBlockRequest != BlockRequest.EMPTY;
    }

    public String getLookupProviderName() {
        if (mIsProviderInitialized) {
            return mLookupProvider.getDisplayName();
        } else {
            return null;
        }
    }

    /**
     * Note: run on non-ui thread as this call executes a content provider query
     */
    private void toggleBlacklistStatus(String number, boolean shouldBlacklist) {
        String nn = PhoneNumberUtils.normalizeNumber(number);
        BlacklistUtils.addOrUpdate(mContext, nn,
                shouldBlacklist ? BlacklistUtils.BLOCK_CALLS | BlacklistUtils.BLOCK_MESSAGES
                        : 0, BlacklistUtils.BLOCK_CALLS | BlacklistUtils.BLOCK_MESSAGES);
    }

    /**
     * Note: run on non-ui thread as this call executes a content provider query
     */
    private boolean isBlacklisted(String number) {
        String nn = PhoneNumberUtils.normalizeNumber(number);
        return BlacklistUtils.isListed(mContext, nn, BlacklistUtils.BLOCK_CALLS)
                != BlacklistUtils.MATCH_NONE;
    }

    public DialogFragment getBlockContactDialog(BlockOperation blockMode) {
        return getBlockContactDialog(blockMode, null);
    }

    public DialogFragment getBlockContactDialog(BlockOperation blockMode, Fragment targetFragment) {
        int launchMode = blockMode == BlockOperation.BLOCK ? BlockContactDialogFragment.BLOCK_MODE :
                BlockContactDialogFragment.UNBLOCK_MODE;
        return BlockContactDialogFragment.create(launchMode, mLookupProvider.getDisplayName(),
                targetFragment);
    }

    public void blockContact(boolean notifyLookupProvider) {
        blockContact(notifyLookupProvider, true /*notify status-listener*/);
    }

    private void blockContact(boolean notifyLookupProvider, boolean notifyListener) {
        for (String phoneNumber : mBlockRequest.phoneNumbers) {
            toggleBlacklistStatus(phoneNumber, true /*block contact*/);

            if (notifyLookupProvider && mIsProviderInitialized &&
                    mLookupProvider.supportsSpamReporting()) {
                mLookupProvider.markAsSpam(phoneNumber);
            }
        }

        if (notifyListener && mListener != null) {
            mListener.onBlockCompleted();
        }

        gatherDataInBackground();
    }

    public void unblockContact(boolean notifyLookupProvider) {
        unblockContact(notifyLookupProvider, true /*notify status-listener*/);
    }

    private void unblockContact(boolean notifyLookupProvider, boolean notifyListener) {
        for (String phoneNumber : mBlockRequest.phoneNumbers) {
            toggleBlacklistStatus(phoneNumber, false /*unblock contact*/);

            if (notifyLookupProvider && mIsProviderInitialized &&
                    mLookupProvider.supportsSpamReporting()) {
                mLookupProvider.unmarkAsSpam(phoneNumber);
            }
        }

        if (notifyListener && mListener != null) {
            mListener.onUnblockCompleted();
        }

        gatherDataInBackground();
    }

    public void blockContactAsync(final boolean notifyLookupProvider) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                blockContact(notifyLookupProvider, false);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if(mListener != null) {
                    mListener.onBlockCompleted();
                }
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    public void unblockContactAsync(final boolean notifyLookupProvider) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                unblockContact(notifyLookupProvider, false);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if(mListener != null) {
                    mListener.onUnblockCompleted();
                }
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Clean-up any external resources that are used
     */
    public void destroy() {
        if (mBackgroundTask != null) {
            mBackgroundTask.cancel(true /*interrupt*/);
        }
        mLookupProvider.disable();
    }

    public interface StatusCallbacks {
        /**
         * Callback indicating that Blacklist information about the contact has been fetched
         */
        void onInfoAvailable();

        /**
         * Callback indicating that block action has completed
         */
        void onBlockCompleted();

        /**
         * Callback indicating that unblock action has completed
         */
        void onUnblockCompleted();
    }
}
