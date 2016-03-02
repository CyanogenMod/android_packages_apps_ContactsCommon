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

package com.android.contacts.common.activity.fragment;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import com.android.contacts.common.R;

/**
 * Interacts with the framework implementation of Blacklist and any phonenumber Lookup Provider
 * interested in spam collection
 *
 * NOTE: ensure you have Blacklist permissions before using this class
 */
public class BlockContactDialogFragment extends DialogFragment
        implements DialogInterface.OnClickListener {

    public static final int BLOCK_MODE = 0;
    public static final int UNBLOCK_MODE = 1;
    public static final String KEY_CURRENT_LOOKUP_PROVIDER_NAME = "CURRENT_LOOKUP_PROVIDER_NAME";
    public static final String KEY_LAUNCH_MODE = "LAUNCH_MODE";

    private int mLaunchMode;
    private CheckBox mNotifyProviderCheckBox;

    public static BlockContactDialogFragment create(int mode, String lookupProvider) {
        return create(mode, lookupProvider, null);
    }

    public static BlockContactDialogFragment create(int mode, String lookupProvider,
            Fragment targetFragment) {
        BlockContactDialogFragment f = new BlockContactDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(BlockContactDialogFragment.KEY_LAUNCH_MODE, mode);
        bundle.putString(BlockContactDialogFragment.KEY_CURRENT_LOOKUP_PROVIDER_NAME,
                lookupProvider);
        f.setArguments(bundle);
        if (targetFragment != null) {
            f.setTargetFragment(targetFragment, 0);
        }
        return f;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String lookupProviderName = null;
        if (getArguments() != null) {
            Bundle bundle = getArguments();
            mLaunchMode = bundle.getInt(KEY_LAUNCH_MODE);
            lookupProviderName = bundle.getString(KEY_CURRENT_LOOKUP_PROVIDER_NAME);
        }

        Activity hostActivity = getActivity();
        boolean blockMode = mLaunchMode == BLOCK_MODE;
        String dialogTitle;
        String positiveButtonText;

        AlertDialog.Builder builder = new AlertDialog.Builder(hostActivity);
        LayoutInflater inflater = (LayoutInflater) hostActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.block_contact_dialog_view, null);
        mNotifyProviderCheckBox = (CheckBox) v.findViewById(R.id.spamCheckbox);

        if (blockMode) {
            dialogTitle = hostActivity.getString(R.string.block_dialog_title);
            positiveButtonText = hostActivity.getString(R.string.block_dialog_positive);
        } else {
            // unblock mode
            dialogTitle = hostActivity.getString(R.string.unblock_dialog_title);
            positiveButtonText = hostActivity.getString(R.string.unblock_dialog_positive);
            v.findViewById(R.id.description).setVisibility(View.GONE);
        }

        String checkboxDescription = TextUtils.isEmpty(lookupProviderName) ?
                null :
                hostActivity.getResources().getString(
                        blockMode ? R.string.block_dialog_report_spam :
                                R.string.block_dialog_report_nonspam,
                        lookupProviderName);

        if (TextUtils.isEmpty(checkboxDescription)) {
            mNotifyProviderCheckBox.setChecked(false);
            mNotifyProviderCheckBox.setVisibility(View.GONE);
        } else {
            mNotifyProviderCheckBox.setText(checkboxDescription);
            mNotifyProviderCheckBox.setVisibility(View.VISIBLE);
        }

        builder.setTitle(dialogTitle)
                .setView(v)
                .setPositiveButton(positiveButtonText, this)
                .setNegativeButton(R.string.block_dialog_negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {}

    @Override
    public void onClick(DialogInterface dialog, int which) {
        boolean mCheckboxStatus = mNotifyProviderCheckBox.isChecked();
        // determine if a Callback is present
        // priority is given to a TargetFragment if one is set
        // otherwise the host activity is chosen, if it adheres to the Callbacks interface
        Callbacks callback = null;
        Fragment targetFragment = getTargetFragment();
        if (targetFragment != null) {
            // throw a runtime exception if a TargetFragment is set that doesn't implement
            // the Callbacks interface
            callback = (Callbacks) targetFragment;
        } else {
            Activity parentActivity = getActivity();
            if (parentActivity instanceof Callbacks) {
                callback = (Callbacks) parentActivity;
            }
        }

        if (callback != null) {
            if (mLaunchMode == BLOCK_MODE) {
                callback.onBlockSelected(mCheckboxStatus);
            } else {
                callback.onUnblockSelected(mCheckboxStatus);
            }
        }
    }

    public interface Callbacks {
        /**
         * Callback noting that the user opted to block the contact
         *
         * @param notifyLookupProvider indicates whether the user opted to report the contact
         *                             to the current LookupProvider
         */
        void onBlockSelected(boolean notifyLookupProvider);

        /**
         * Callback noting that the user opted to unblock the contact
         *
         * @param notifyLookupProvider indicates whether the user opted to notify the current
         *                             LookupProvider of the unblock
         */
        void onUnblockSelected(boolean notifyLookupProvider);
    }
}
