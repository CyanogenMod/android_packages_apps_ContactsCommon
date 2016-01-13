package com.android.contacts.common.util;

import android.app.DialogFragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.contacts.common.R;
import com.android.contacts.common.activity.fragment.BlockContactDialogFragment;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.internal.telephony.util.BlacklistUtils;
import com.cyanogen.lookup.phonenumber.contract.LookupProvider;

import java.util.List;

/**
 * Helper class used to interface with the framework implementation of Blacklist and delegating
 * the apropos information to the active phonenumber LookupProvider, if any.
 */
public class BlockContactHelper {
    private final Context mContext;
    private AsyncTask mBackgroundTask;
    private Contact mContact;
    private LookupProvider mLookupProvider;
    private volatile boolean mIsBlacklisted;
    private volatile boolean mIsProviderInitialized;
    private boolean mBackgroundTaskCompleted;

    public enum BlockMode {
        BLOCK,
        UNBLOCK
    }

    public BlockContactHelper(Context context, LookupProvider lookupProvider) {
        mContext = context;
        mLookupProvider = lookupProvider;
    }

    public void setContactInfo(Contact contact) {
        mContact = contact;
    }

    public void gatherDataInBackground() {
        mBackgroundTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (mContact == null) return null;
                // check blacklist status of all of the contact's numbers
                contact:
                for (RawContact rawContact : mContact.getRawContacts()) {
                    List<DataItem> data = rawContact.getDataItems();
                    for(DataItem item : data) {
                        if (item instanceof PhoneDataItem) {
                            PhoneDataItem phoneDataItem = (PhoneDataItem) item;
                            if (isBlacklisted(phoneDataItem.getNumber())) {
                                mIsBlacklisted = true;
                                break contact;
                            }
                        }
                    }
                }

                if (mLookupProvider.initialize()) {
                    mIsProviderInitialized = true;
                }

                mBackgroundTaskCompleted = true;
                return null;
            }
        }.execute();
    }

    public boolean isContactBlacklisted() {
        return mIsBlacklisted;
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

    public DialogFragment getBlockContactDialog(BlockMode blockMode) {
        BlockContactDialogFragment f = new BlockContactDialogFragment();
        Bundle bundle = new Bundle();
        int launchMode = blockMode == BlockMode.BLOCK ? BlockContactDialogFragment.BLOCK_MODE :
                BlockContactDialogFragment.UNBLOCK_MODE;
        bundle.putInt(BlockContactDialogFragment.KEY_LAUNCH_MODE, launchMode);
        String providerName = mLookupProvider.getDisplayName();
        bundle.putString(BlockContactDialogFragment.KEY_CURRENT_LOOKUP_PROVIDER_NAME, providerName);

        f.setArguments(bundle);
        return f;
    }

    public void blockContact(boolean notifyLookupProvider) {
        for (RawContact rawContact : mContact.getRawContacts()) {
            List<DataItem> data = rawContact.getDataItems();
            for(DataItem item : data) {
                if (item instanceof PhoneDataItem) {
                    PhoneDataItem phoneDataItem = (PhoneDataItem) item;
                    String number = phoneDataItem.getNumber();
                    toggleBlacklistStatus(number, true /*block contact*/);

                    if (notifyLookupProvider && mIsProviderInitialized &&
                            mLookupProvider.supportsSpamReporting()) {
                        String formattedNumber = PhoneNumberHelper.formatPhoneNumber(mContext,
                                number);
                        mLookupProvider.markAsSpam(formattedNumber);
                    }
                }
            }
        }

        gatherDataInBackground();
    }

    public void unblockContact(boolean notifyLookupProvider) {
        for (RawContact rawContact : mContact.getRawContacts()) {
            List<DataItem> data = rawContact.getDataItems();
            for(DataItem item : data) {
                if (item instanceof PhoneDataItem) {
                    PhoneDataItem phoneDataItem = (PhoneDataItem) item;
                    String number = phoneDataItem.getNumber();
                    toggleBlacklistStatus(number, false /*unblock contact*/);

                    if (notifyLookupProvider && mIsProviderInitialized &&
                            mLookupProvider.supportsSpamReporting()) {
                        String formattedNumber = PhoneNumberHelper.formatPhoneNumber(mContext,
                                number);
                        mLookupProvider.unmarkAsSpam(formattedNumber);
                    }
                }
            }
        }

        gatherDataInBackground();
    }

    public void blockContactAsync(final boolean notifyLookupProvider) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                blockContact(notifyLookupProvider);
                return null;
            }
        }.execute();
    }

    public void unblockContactAsync(final boolean notifyLookupProvider) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                unblockContact(notifyLookupProvider);
                return null;
            }
        }.execute();
    }

}
