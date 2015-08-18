/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.vcard.ImportVCardActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for selectiong an Account for importing contact(s)
 */
public class AccountSelectionUtil {
    // TODO: maybe useful for EditContactActivity.java...
    private static final String LOG_TAG = "AccountSelectionUtil";

    public static boolean mVCardShare = false;
    private static int SIM_ID_INVALID = -1;
    private static int mSelectedSim = SIM_ID_INVALID;
    // Constant value to know option is import from all SIM's
    private static int IMPORT_FROM_ALL = 8;

    public static Uri mPath;
    // QRD enhancement: import subscription selected by user
    private static int mImportSub = SimContactsConstants.SUB_INVALID;

    public static class AccountSelectedListener
            implements DialogInterface.OnClickListener {

        final private Context mContext;
        final private int mResId;
        final private int mSubscriptionId;

        protected List<AccountWithDataSet> mAccountList;

        public AccountSelectedListener(Context context, List<AccountWithDataSet> accountList,
                int resId, int subscriptionId) {
            if (accountList == null || accountList.size() == 0) {
                Log.e(LOG_TAG, "The size of Account list is 0.");
            }
            mContext = context;
            mAccountList = accountList;
            mResId = resId;
            mSubscriptionId = subscriptionId;
        }

        public AccountSelectedListener(Context context, List<AccountWithDataSet> accountList,
                int resId) {
            // Subscription id is only needed for importing from SIM card. We can safely ignore
            // its value for SD card importing.
            this(context, accountList, resId, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }

        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            doImport(mContext, mResId, mAccountList.get(which), mSubscriptionId);
        }
        /**
         * Reset the account list for this listener, to make sure the selected
         * items reflect the displayed items.
         *
         * @param accountList The reset account list.
         */
        void setAccountList(List<AccountWithDataSet> accountList) {
            mAccountList = accountList;
        }
    }

    public static void setImportSubscription(int subscription) {
        mImportSub = subscription;
    }

    public static Dialog getSelectAccountDialog(Context context, int resId) {
        return getSelectAccountDialog(context, resId, null, null);
    }

    public static Dialog getSelectAccountDialog(Context context, int resId,
            DialogInterface.OnClickListener onClickListener) {
        return getSelectAccountDialog(context, resId, onClickListener, null);
    }

    public static Dialog getSelectAccountDialog(Context context, int resId,
            DialogInterface.OnClickListener onClickListener,
            DialogInterface.OnCancelListener onCancelListener) {
        return getSelectAccountDialog(context, resId, onClickListener,
            onCancelListener, true);
    }

    /**
     * When OnClickListener or OnCancelListener is null, uses a default listener.
     * The default OnCancelListener just closes itself with {@link Dialog#dismiss()}.
     */
    public static Dialog getSelectAccountDialog(Context context, int resId,
            DialogInterface.OnClickListener onClickListener,
            DialogInterface.OnCancelListener onCancelListener, boolean includeSIM) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        List<AccountWithDataSet> writableAccountList = accountTypes.getAccounts(true);
        if (includeSIM) {
            writableAccountList = accountTypes.getAccounts(true);
        } else {
            writableAccountList = accountTypes.getAccounts(true,
                AccountTypeManager.FLAG_ALL_ACCOUNTS_WITHOUT_SIM);
        }

        Log.i(LOG_TAG, "The number of available accounts: " + writableAccountList.size());

        // Assume accountList.size() > 1

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(
                context, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ArrayAdapter<AccountWithDataSet> accountAdapter =
            new ArrayAdapter<AccountWithDataSet>(context, android.R.layout.simple_list_item_2,
                    writableAccountList) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(
                            android.R.layout.simple_list_item_2,
                            parent, false);
                }

                // TODO: show icon along with title
                final TextView text1 =
                        (TextView)convertView.findViewById(android.R.id.text1);
                final TextView text2 =
                        (TextView)convertView.findViewById(android.R.id.text2);

                final AccountWithDataSet account = this.getItem(position);
                final AccountType accountType = accountTypes.getAccountType(
                        account.type, account.dataSet);
                final Context context = getContext();

                text1.setText(account.name);
                text2.setText(accountType.getDisplayLabel(context));

                return convertView;
            }
        };

        if (onClickListener == null) {
            AccountSelectedListener accountSelectedListener =
                new AccountSelectedListener(context, writableAccountList, resId);
            onClickListener = accountSelectedListener;
        } else if (onClickListener instanceof AccountSelectedListener) {
            // Because the writableAccountList is different if includeSIM or not, so
            // should reset the account list for the AccountSelectedListener which
            // is initialized with FLAG_ALL_ACCOUNTS.
            // Reset the account list to make sure the selected account is contained
            // in these display accounts.
            ((AccountSelectedListener) onClickListener).setAccountList(writableAccountList);
        }
        if (onCancelListener == null) {
            onCancelListener = new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                }
            };
        }
        return new AlertDialog.Builder(context)
            .setTitle(R.string.dialog_new_contact_account)
            .setSingleChoiceItems(accountAdapter, 0, onClickListener)
            .setOnCancelListener(onCancelListener)
            .create();
    }

    public static void doImport(Context context, int resId, AccountWithDataSet account,
            int subscriptionId) {
        switch (resId) {
            case R.string.import_from_sim: {
                doImportFromSim(context, account, subscriptionId);
                break;
            }
            case R.string.import_from_sdcard: {
                doImportFromSdCard(context, account);
                break;
            }
        }
    }

    public static void doImportFromSim(Context context, AccountWithDataSet account,
            int subscriptionId) {
        Intent importIntent = new Intent(Intent.ACTION_VIEW);
        importIntent.setType("vnd.android.cursor.item/sim-contact");
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
        }
        importIntent.putExtra("sim_index", SubscriptionManager.getPhoneId(subscriptionId));
        importIntent.setClassName("com.android.phone", "com.android.phone.SimContacts");
        context.startActivity(importIntent);
    }

    public static void doImportFromSdCard(Context context, AccountWithDataSet account) {
        Intent importIntent = new Intent(context, ImportVCardActivity.class);
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
        }

        if (mVCardShare) {
            importIntent.setAction(Intent.ACTION_VIEW);
            importIntent.setData(mPath);
        }
        mVCardShare = false;
        mPath = null;
        context.startActivity(importIntent);
    }

    public static class SimSelectedListener
            implements DialogInterface.OnClickListener {

        final private Context mContext;
        final private AccountWithDataSet mAccount;

        public SimSelectedListener(Context context, AccountWithDataSet account) {
            mContext = context;
            mAccount = account;
        }

        public void onClick(DialogInterface dialog, int which) {
            Log.d(LOG_TAG, "onClick OK: mSelectedSim = " + mSelectedSim);
            if (mSelectedSim != SIM_ID_INVALID) {
                doImportFromSim(mContext, mAccount, mSelectedSim);
            }
        }
    }

    private static void displaySelectSimDialog(Context context,
                SimSelectedListener simSelListner) {
        Log.d(LOG_TAG, "displaySelectSimDialog");

        mSelectedSim = SIM_ID_INVALID;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.select_sim);
        final int numPhones = TelephonyManager.getDefault().getPhoneCount();
        CharSequence[] subList = new CharSequence[numPhones + 1];
        int i;
        for (i = 1; i <= numPhones; i++) {
            subList[i-1] = "SIM" + i;
        }
        subList[i-1] = context.getString(R.string.import_contacts_from_all_cards);
        builder.setSingleChoiceItems(subList, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(LOG_TAG, "onClicked Dialog on which = " + which);
                mSelectedSim = which;
                if (mSelectedSim == numPhones) {
                    mSelectedSim = IMPORT_FROM_ALL;
                }
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                context.getString(com.android.internal.R.string.ok), simSelListner);
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(
                com.android.internal.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(LOG_TAG, "onClicked Cancel");
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener () {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(LOG_TAG, "onDismiss");
                Log.d(LOG_TAG, "Selected SUB = " + mSelectedSim);
            }
        });
        dialog.show();
    }
}
