package com.android.contacts.common.util;

import android.app.DialogFragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.util.BlacklistUtils;
import com.android.contacts.common.activity.fragment.BlockContactDialogFragment;
import com.android.contacts.common.R;
import com.android.contacts.common.model.Contact;
import com.cyanogen.lookup.phonenumber.contract.LookupProvider;

import java.io.Serializable;

/**
 * Helper class used to interface with the framework implementation of Blacklist and delegating
 * the apropos information to the active phonenumber LookupProvider, if any.
 */
public class BlockNumberHelper implements BlockContactDialogFragment.BlockContactCallbacks,
        Serializable {
    private final Context mContext;
    private AsyncTask mBackgroundTask;
    private String mNumber;
    private LookupProvider mLookupProvider;
    private volatile boolean mIsProviderInitialized;
    private BlockActionCallbacks mTaskCompletionCallbacks;

    public enum BlockMode {
        BLOCK,
        UNBLOCK
    }

    public BlockNumberHelper(Context context, LookupProvider lookupProvider) {
        mContext = context;
        mLookupProvider = lookupProvider;
    }

    public void setNumberInfo(String number) {
        mNumber = number;
    }

    public void setTaskCompletionCallbacks(BlockActionCallbacks taskCompletionCallbacks) {
        mTaskCompletionCallbacks = taskCompletionCallbacks;
    }

    public void gatherDataInBackground() {
        mBackgroundTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (mNumber == null) return null;

                if (mLookupProvider.initialize()) {
                    mIsProviderInitialized = true;
                }

                return null;
            }
        }.execute();
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

    public DialogFragment getBlockNumberDialog(BlockMode blockMode) {
        BlockContactDialogFragment f = new BlockContactDialogFragment();
        Bundle bundle = new Bundle();
        int launchMode = blockMode == BlockMode.BLOCK ? BlockContactDialogFragment.BLOCK_MODE :
                BlockContactDialogFragment.UNBLOCK_MODE;
        bundle.putInt(BlockContactDialogFragment.KEY_LAUNCH_MODE, launchMode);
        String providerName = mLookupProvider.getDisplayName();
        bundle.putString(BlockContactDialogFragment.KEY_CURRENT_LOOKUP_PROVIDER_NAME, providerName);
        bundle.putSerializable(BlockContactDialogFragment.BLOCK_UI_RESULT_CALLBACK,
                (Serializable)this);

        f.setArguments(bundle);
        return f;
    }

    private void blockNumber(String number, boolean notifyLookupProvider) {
        toggleBlacklistStatus(number, true /*block number*/);

        if (notifyLookupProvider && mIsProviderInitialized &&
                mLookupProvider.supportsSpamReporting()) {
            String formattedNumber = PhoneNumberHelper.formatPhoneNumber(mContext,
                    number);
            mLookupProvider.markAsSpam(formattedNumber);
        }
    }

    private void unblockNumber(String number, boolean notifyLookupProvider) {
        toggleBlacklistStatus(number, false /*unblock number*/);

        if (notifyLookupProvider && mIsProviderInitialized &&
                mLookupProvider.supportsSpamReporting()) {
            String formattedNumber = PhoneNumberHelper.formatPhoneNumber(mContext,
                    number);
            mLookupProvider.unmarkAsSpam(formattedNumber);
        }
    }

    private void blockNumberAsync(final String number, final boolean notifyLookupProvider) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                blockNumber(number, notifyLookupProvider);
                return null;
            }
            @Override
            protected void onPostExecute(Void v) {
                if(mTaskCompletionCallbacks != null
                        && mTaskCompletionCallbacks instanceof BlockActionCallbacks) {
                    ((BlockActionCallbacks)mTaskCompletionCallbacks).onBlockCompleted();
                }
            }
        }.execute();
    }

    private void unblockNumberAsync(final String number, final boolean notifyLookupProvider) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                unblockNumber(number, notifyLookupProvider);
                return null;
            }
            @Override
            protected void onPostExecute(Void v) {
                if(mTaskCompletionCallbacks != null
                        && mTaskCompletionCallbacks instanceof BlockActionCallbacks) {
                    ((BlockActionCallbacks)mTaskCompletionCallbacks).onUnblockCompleted();
                }
            }
        }.execute();
    }

    @Override
    public void onBlockContact(boolean notifyLookupProvider) {
        if (mNumber != null) {
            blockNumberAsync(mNumber, notifyLookupProvider);
        }
    }

    @Override
    public void onUnblockContact(boolean notifyLookupProvider) {
        if (mNumber != null) {
            unblockNumberAsync(mNumber, notifyLookupProvider);
        }
    }


    public interface BlockActionCallbacks {
        /**
         * Callback indicating that block action has completedd
         */
        void onBlockCompleted();

        /**
         * Callback indicating that unblock action has completed
         */
        void onUnblockCompleted();
    }


}
