package com.android.contacts.common.activity.fragment;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Dialog;
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
 * Interacts with the framework implementation of Blacklist and any phonenumber Lookup Providers
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
        Activity parentActivity = getActivity();
        if (parentActivity instanceof BlockContactCallbacks) {
            if (mLaunchMode == BLOCK_MODE) {
                ((BlockContactCallbacks) parentActivity).onBlockContact(mCheckboxStatus);
            } else {
                ((BlockContactCallbacks) parentActivity).onUnblockContact(mCheckboxStatus);
            }
        }
    }

    public interface BlockContactCallbacks {
        /**
         * Callback noting that the user opted to block the contact
         *
         * @param notifyLookupProvider indicates whether the user opted to report the contact
         *                             to the current LookupProvider
         */
        void onBlockContact(boolean notifyLookupProvider);

        /**
         * Callback noting that the user opted to unblock the contact
         *
         * @param notifyLookupProvider indicates whether the user opted to notify the current
         *                             LookupProvider of the unblock
         */
        void onUnblockContact(boolean notifyLookupProvider);
    }
}
