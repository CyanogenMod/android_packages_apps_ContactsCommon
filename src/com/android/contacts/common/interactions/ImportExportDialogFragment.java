/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.common.interactions;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.text.TextUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsOperation;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.R;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountSelectionUtil;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.common.vcard.ExportVCardActivity;
import com.android.contacts.common.vcard.VCardCommonArguments;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.internal.telephony.PhoneConstants;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportExportDialogFragment extends DialogFragment
        implements SelectAccountDialogFragment.Listener {
    public static final String TAG = "ImportExportDialogFragment";

    private static final String KEY_RES_ID = "resourceId";
    private static final String KEY_SUBSCRIPTION_ID = "subscriptionId";
    private static final String ARG_CONTACTS_ARE_AVAILABLE = "CONTACTS_ARE_AVAILABLE";

    private static final boolean DEBUG = true;

    public static final int SUBACTIVITY_EXPORT_CONTACTS = 100;

    // This values must be consistent with ImportExportDialogFragment.SUBACTIVITY_EXPORT_CONTACTS.
    // This values is set 101,That is avoid to conflict with other new subactivity.
    public static final int SUBACTIVITY_SHARE_VISILBLE_CONTACTS = 101;
    public static final int MAX_COUNT_ALLOW_SHARE_CONTACT = 2000;

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    static final int PHONE_ID_COLUMN_INDEX = 0;
    static final int PHONE_TYPE_COLUMN_INDEX = 1;
    static final int PHONE_LABEL_COLUMN_INDEX = 2;
    static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;
    static final int PHONE_CONTACT_ID_COLUMN_INDEX = 5;
    // This value needs to start at 7. See {@link PeopleActivity}.
    public static final int SUBACTIVITY_MULTI_PICK_CONTACT = 7;
    //TODO: we need to refactor the export code in future release.
    // QRD enhancement: export subscription selected by user
    public static int mExportSub;
    //this flag is the same as defined in MultiPickContactActivit
    private static final String EXT_NOT_SHOW_SIM_FLAG = "not_sim_show";
    // QRD enhancement: Toast handler for exporting concat to sim card
    private static final int TOAST_EXPORT_FAILED = 0;
    private static final int TOAST_EXPORT_FINISHED = 1;
    // only for sim card is full
    private static final int TOAST_SIM_CARD_FULL = 2;
    // only for contact name too long
    private static final int TOAST_CONTACT_NAME_TOO_LONG = 3;
    // there is a case export is canceled by user
    private static final int TOAST_EXPORT_CANCELED = 4;
    // only for not have phone number or email address
    private static final int TOAST_EXPORT_NO_PHONE_OR_EMAIL = 5;
    // only for sim contacts haven't been loaded completely
    private static final int TOAST_SIM_CARD_NOT_LOAD_COMPLETE = 6;
    private SimContactsOperation mSimContactsOperation;
    private ArrayAdapter<AdapterEntry> mAdapter;
    private Activity mActivity;
    private static boolean isExportingToSIM = false;
    public static boolean isExportingToSIM(){
        return isExportingToSIM;
    }
    private static ExportToSimThread mExportThread = null;
    public ExportToSimThread createExportToSimThread(int subscription,
            ArrayList<String[]> contactList, Activity mActivity) {
        if (mExportThread == null)
            mExportThread = new ExportToSimThread(subscription, contactList,  mActivity);
        return mExportThread;
    }

    public static void destroyExportToSimThread(){
        mExportThread = null;
    }
    public void showExportToSIMProgressDialog(Activity activity){
        mExportThread.showExportProgressDialog(activity);
    }

    private SubscriptionManager mSubscriptionManager;

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager, boolean contactsAreAvailable,
            Class callingActivity) {
        final ImportExportDialogFragment fragment = new ImportExportDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_CONTACTS_ARE_AVAILABLE, contactsAreAvailable);
        args.putString(VCardCommonArguments.ARG_CALLING_ACTIVITY, callingActivity.getName());
        fragment.setArguments(args);
        fragment.show(fragmentManager, ImportExportDialogFragment.TAG);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        AnalyticsUtil.sendScreenView(this);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        mActivity = getActivity();
        final Resources res = getActivity().getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final boolean contactsAreAvailable = getArguments().getBoolean(ARG_CONTACTS_ARE_AVAILABLE);
        final String callingActivity = getArguments().getString(
                VCardCommonArguments.ARG_CALLING_ACTIVITY);

        // Adapter that shows a list of string resources
        mAdapter = new ArrayAdapter<AdapterEntry>(getActivity(),
                R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false));

                result.setText(getItem(position).mLabel);
                return result;
            }
        };

        // Manually call notifyDataSetChanged() to refresh the list.
        mAdapter.setNotifyOnChange(false);
        loadData(contactsAreAvailable);

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final int resId = mAdapter.getItem(which).mChoiceResourceId;
                switch (resId) {
                    case R.string.import_from_sim: {
                        handleImportFromSimRequest(resId);
                        break;
                    }
                    case R.string.import_from_vcf_file: {
                        handleImportRequest(resId);
                        break;
                    }
                    case R.string.export_to_sim: {
                        handleExportToSimRequest(resId);
                        break;
                    }
                    case R.string.export_to_vcf_file: {
                        Intent exportIntent = new Intent(SimContactsConstants.ACTION_MULTI_PICK,
                                Contacts.CONTENT_URI);
                        exportIntent.putExtra(SimContactsConstants.IS_CONTACT, true);
                        getActivity().startActivityForResult(exportIntent,
                                SUBACTIVITY_EXPORT_CONTACTS);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        Log.e(TAG, "Unexpected resource: "
                                + getActivity().getResources().getResourceEntryName(resId));
                    }
                }
                dialog.dismiss();
            }
        };
        return new AlertDialog.Builder(getActivity())
                .setTitle(contactsAreAvailable
                        ? R.string.dialog_import_export
                        : R.string.dialog_import)
                .setSingleChoiceItems(mAdapter, -1, clickListener)
                .create();
    }

    /**
     * Loading the menu list data.
     * @param contactsAreAvailable
     */
    private void loadData(boolean contactsAreAvailable) {
        if (null == mActivity && null == mAdapter) {
            return;
        }

        mAdapter.clear();
        final Resources res = mActivity.getResources();
        boolean hasIccCard = false;
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
           for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
               hasIccCard = TelephonyManager.getDefault().hasIccCard(i);
               if (hasIccCard) {
                  break;
               }
           }
        } else {
           hasIccCard = TelephonyManager.getDefault().hasIccCard();
        }

        if (hasIccCard
                && res.getBoolean(R.bool.config_allow_sim_import)) {
            mAdapter.add(new AdapterEntry(getString(R.string.import_from_sim),
                    R.string.import_from_sim));
        }
        if (res.getBoolean(R.bool.config_allow_import_from_vcf_file)) {
            mAdapter.add(new AdapterEntry(getString(R.string.import_from_vcf_file),
                    R.string.import_from_vcf_file));
        }

        if (hasIccCard) {
            mAdapter.add(new AdapterEntry(getString(R.string.export_to_sim),
                    R.string.export_to_sim));
        }
        if (res.getBoolean(R.bool.config_allow_export)) {
            // If contacts are available and there is at least one contact in
            // database, show "Export to SD card" menu item. Otherwise hide it
            // because it makes no sense.
            if (contactsAreAvailable) {
                mAdapter.add(new AdapterEntry(getString(R.string.export_to_vcf_file),
                        R.string.export_to_vcf_file));
            }
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            if (contactsAreAvailable) {
                mAdapter.add(new AdapterEntry(getString(R.string.share_visible_contacts),
                        R.string.share_visible_contacts));
            }
        }
    }

    private void doShareVisibleContacts() {
        Intent intent = new Intent(SimContactsConstants.ACTION_MULTI_PICK);
        intent.setType(Contacts.CONTENT_TYPE);
        intent.putExtra(SimContactsConstants.IS_CONTACT,true);
        getActivity().startActivityForResult(intent, SUBACTIVITY_SHARE_VISILBLE_CONTACTS);
    }

    /**
     * Handle "import from SIM" and "import from SD".
     *
     * @return {@code true} if the dialog show be closed.  {@code false} otherwise.
     */
    private boolean handleImportRequest(int resId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mActivity);
        final List<AccountWithDataSet> accountList = accountTypes.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            // Send over to the account selector
            final Bundle args = new Bundle();
            args.putInt(KEY_RES_ID, resId);
            SelectAccountDialogFragment.show(mActivity.getFragmentManager(),
                    this, R.string.dialog_new_contact_account,
                    AccountListFilter.ACCOUNTS_CONTACT_WRITABLE_WITHOUT_SIM,
                    args);

            // In this case, because this DialogFragment is used as a target fragment to
            // SelectAccountDialogFragment, we can't close it yet.  We close the dialog when
            // we get a callback from it.
            return false;
        }

        AccountSelectionUtil.doImport(mActivity, resId,
                (size == 1 ? accountList.get(0) : null));
        return true; // Close the dialog.
    }

    /**
     * Called when an account is selected on {@link SelectAccountDialogFragment}.
     */
    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        AccountSelectionUtil.doImport(mActivity, extraArgs.getInt(KEY_RES_ID), account);

        // At this point the dialog is still showing (which is why we can use getActivity() above)
        // So close it.
        dismiss();
    }

    @Override
    public void onAccountSelectorCancelled() {
        // See onAccountChosen() -- at this point the dialog is still showing.  Close it.
        dismiss();
    }

    private class ExportToSimSelectListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0) {
                mExportSub = which;
            } else if (which == DialogInterface.BUTTON_POSITIVE) {
                Intent pickPhoneIntent = new Intent(
                        SimContactsConstants.ACTION_MULTI_PICK, Contacts.CONTENT_URI);
                // do not show the contacts in SIM card
                pickPhoneIntent.putExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER,
                        ContactListFilter
                                .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
                pickPhoneIntent.putExtra(EXT_NOT_SHOW_SIM_FLAG, true);
                pickPhoneIntent.putExtra(SimContactsConstants.IS_CONTACT,true);
                mActivity.startActivityForResult(pickPhoneIntent, SUBACTIVITY_MULTI_PICK_CONTACT);
            }
        }
    }

    public class ImportFromSimSelectListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0) {
                AccountSelectionUtil.setImportSubscription(which);
            } else if (which == DialogInterface.BUTTON_POSITIVE) {
                handleImportRequest(R.string.import_from_sim);
            }
        }
    }

    /**
     * A thread that export contacts to sim card
     */
    public class ExportToSimThread extends Thread {
        private int subscription;
        private boolean canceled;
        private ArrayList<String[]> contactList;
        private ProgressDialog mExportProgressDlg;
        private ContentValues mValues = new ContentValues();
        Activity mPeople;
        private int freeSimCount = 0;

        public ExportToSimThread(int subscription, ArrayList<String[]> contactList,
            Activity mActivity) {
            super();
            this.subscription = subscription;
            this.contactList = contactList;
            canceled = false;
            mPeople = mActivity;
            showExportProgressDialog(mPeople);
        }

        @Override
        public void run() {
            isExportingToSIM = true;
            Account account = MoreContactUtils.getAcount(subscription);
            boolean isAirplaneMode = false;
            boolean isSimCardFull = false;
            boolean isSimCardLoaded = true;
            // GoogleSource.createMyContactsIfNotExist(account, getActivity());
            // in case export is stopped, record the count of inserted successfully
            int insertCount = 0;

            mSimContactsOperation = new SimContactsOperation(mPeople);
            Cursor cr = null;
            // call query first, otherwise insert will fail if this insert is called
            // without any query before
            try{
                int[] subId = SubscriptionManager.getSubId(subscription);
                if (subId != null
                        && TelephonyManager.getDefault().isMultiSimEnabled()) {
                    cr = mPeople.getContentResolver().query(
                            Uri.parse(SimContactsConstants.SIM_SUB_URI
                                    + subId[0]), null, null, null, null);
                } else {
                    cr = mPeople.getContentResolver().query(
                            Uri.parse(SimContactsConstants.SIM_URI), null,
                            null, null, null);
                }
            } catch (NullPointerException e) {
                Log.e(TAG, "Exception:" + e);
            } finally {
                if (cr != null) {
                    cr.close();
                }
            }
            freeSimCount = MoreContactUtils.getSimFreeCount(mPeople,subscription);
            boolean canSaveAnr = MoreContactUtils.canSaveAnr(subscription);
            boolean canSaveEmail = MoreContactUtils.canSaveEmail(subscription);
            int emptyAnr = MoreContactUtils.getSpareAnrCount(subscription);
            int emptyEmail = MoreContactUtils
                    .getSpareEmailCount(subscription);
            int emptyNumber = freeSimCount + emptyAnr;

            Log.d(TAG, "freeSimCount = " + freeSimCount);
            String emails = null;
                if (contactList != null) {
                    Iterator<String[]> iterator = contactList.iterator();
                    while (iterator.hasNext() && !canceled && !isAirplaneMode && isSimCardLoaded) {
                        String[] contactInfo = iterator.next();
                        //contacts name has been existed in contactInfo,so no need query it again
                        String name = contactInfo[4];
                        ArrayList<String> arrayNumber = new ArrayList<String>();
                        ArrayList<String> arrayEmail = new ArrayList<String>();

                        Uri dataUri = Uri.withAppendedPath(
                                ContentUris.withAppendedId(Contacts.CONTENT_URI,
                                        Long.parseLong(contactInfo[1])),
                                Contacts.Data.CONTENT_DIRECTORY);
                        final String[] projection = new String[] {
                                Contacts._ID, Contacts.Data.MIMETYPE, Contacts.Data.DATA1,
                        };
                        Cursor c = mPeople.getContentResolver().query(dataUri, projection, null,
                                null, null);

                        if (c != null && c.moveToFirst()) {
                            do {
                                String mimeType = c.getString(1);
                                if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                    String number = c.getString(2);
                                    if (!TextUtils.isEmpty(number) && emptyNumber-- >0) {
                                        arrayNumber.add(number);
                                    }
                                }
                                if (canSaveEmail) {
                                    if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                        String email = c.getString(2);
                                        if (!TextUtils.isEmpty(email) && emptyEmail-- > 0) {
                                            arrayEmail.add(email);
                                        }
                                    }
                                }
                            } while (c.moveToNext());
                        }
                        if (c != null) {
                            c.close();
                        }

                        if (freeSimCount > 0 && 0 == arrayNumber.size()
                                && 0 == arrayEmail.size()) {
                            mToastHandler.sendMessage(mToastHandler.obtainMessage(
                                    TOAST_EXPORT_NO_PHONE_OR_EMAIL, name));
                            continue;
                        }

                        int phoneCountInOneSimContact = 1;
                        int emailCountInOneSimContact = 0;
                        if (canSaveAnr) {
                            int num = MoreContactUtils.getOneSimAnrCount(subscription);
                            phoneCountInOneSimContact = num > 1 ? (num + 1) : 2;
                        }
                        if (canSaveEmail) {
                            emailCountInOneSimContact = MoreContactUtils
                                    .getOneSimEmailCount(subscription);
                        }
                        int nameCount = (name != null && !name.equals("")) ? 1 : 0;
                        int groupNumCount = (arrayNumber.size() % phoneCountInOneSimContact) != 0 ?
                                (arrayNumber.size() / phoneCountInOneSimContact + 1)
                                : (arrayNumber.size() / phoneCountInOneSimContact);
                        int groupEmailCount = emailCountInOneSimContact == 0 ? 0
                                : ((arrayEmail.size() % emailCountInOneSimContact) != 0 ? (
                                        arrayEmail.size() / emailCountInOneSimContact + 1)
                                        : (arrayEmail.size() / emailCountInOneSimContact));
                        //recalute the group when spare anr is not enough
                        if (canSaveAnr && emptyAnr >=0 && emptyAnr <= groupNumCount) {
                            groupNumCount = arrayNumber.size() - emptyAnr;
                        }
                        int groupCount = Math.max(groupEmailCount,
                                Math.max(nameCount, groupNumCount));

                        Uri result = null;
                        if (DEBUG) {
                            Log.d(TAG, "GroupCount = " + groupCount);
                        }
                        for (int i = 0; i < groupCount; i++) {
                            if (freeSimCount > 0) {
                                String num = arrayNumber.size() > 0 ? arrayNumber.remove(0) : null;
                                StringBuilder anrNum = new StringBuilder();
                                StringBuilder email = new StringBuilder();
                                if (canSaveAnr) {
                                    for (int j = 1; j < phoneCountInOneSimContact; j++) {
                                        if (arrayNumber.size() > 0 && emptyAnr-- > 0 ) {
                                            String s = arrayNumber.remove(0);
                                            if (s.length() > MoreContactUtils
                                                    .MAX_LENGTH_NUMBER_IN_SIM) {
                                                s = s.substring(0,
                                                        MoreContactUtils.MAX_LENGTH_NUMBER_IN_SIM);
                                            }
                                            anrNum.append(s);
                                            anrNum.append(SimContactsConstants.ANR_SEP);
                                        }
                                    }
                                }
                                if (canSaveEmail) {
                                    for (int j = 0; j < emailCountInOneSimContact; j++) {
                                        if (arrayEmail.size() > 0) {
                                            String s = arrayEmail.remove(0);
                                            if (s.length() > MoreContactUtils
                                                    .MAX_LENGTH_EMAIL_IN_SIM) {
                                                s = s.substring(0,
                                                        MoreContactUtils.MAX_LENGTH_EMAIL_IN_SIM);
                                            }
                                            email.append(s);
                                            email.append(SimContactsConstants.EMAIL_SEP);
                                        }
                                    }
                                }

                                result = MoreContactUtils.insertToCard(mPeople, name, num,
                                        email.toString(), anrNum.toString(), subscription);

                                if (null == result) {
                                    // add toast handler when sim card is full
                                    if ((MoreContactUtils.getAdnCount(subscription) > 0)
                                            && (MoreContactUtils.getSimFreeCount(mPeople,
                                                    subscription) == 0)) {
                                        isSimCardFull = true;
                                        mToastHandler.sendMessage(mToastHandler.obtainMessage(
                                                TOAST_SIM_CARD_FULL, insertCount, 0));
                                        break;
                                    } else {
                                        isAirplaneMode = MoreContactUtils
                                                .isAPMOnAndSIMPowerDown(mPeople);
                                        if (isAirplaneMode) {
                                            mToastHandler.sendMessage(mToastHandler.obtainMessage(
                                                    TOAST_EXPORT_FAILED, insertCount, 0));
                                            break;
                                        } else {
                                            // Failed to insert to SIM card
                                            int anrNumber = 0;
                                            if (!TextUtils.isEmpty(anrNum)) {
                                                anrNumber += anrNum.toString().split(
                                                        SimContactsConstants.ANR_SEP).length;
                                            }
                                            // reset emptyNumber and emptyAnr to the value before
                                            // the insert operation
                                            emptyAnr += anrNumber;
                                            emptyNumber += anrNumber;
                                            if (!TextUtils.isEmpty(num)) {
                                                emptyNumber++;
                                            }

                                            if (!TextUtils.isEmpty(email)) {
                                                // reset emptyEmail to the value before the insert
                                                // operation
                                                emptyEmail += email.toString().split(
                                                        SimContactsConstants.EMAIL_SEP).length;
                                            }
                                            continue;
                                        }
                                    }
                                } else {
                                    if (DEBUG) {
                                        Log.d(TAG, "Exported contact [" + name + ", "
                                                + contactInfo[0] + ", " + contactInfo[1]
                                                + "] to sub " + subscription);
                                    }
                                    insertCount++;
                                    freeSimCount--;
                                }
                            } else {
                                if (MoreContactUtils.getAdnCount(subscription) == 0) {
                                    isSimCardLoaded = false;
                                    mToastHandler.sendEmptyMessage(
                                            TOAST_SIM_CARD_NOT_LOAD_COMPLETE);
                                } else {
                                    isSimCardFull = true;
                                    mToastHandler.sendMessage(mToastHandler.obtainMessage(
                                            TOAST_SIM_CARD_FULL, insertCount, 0));
                                }
                                break;
                            }
                        }

                        if (isSimCardFull) {
                            break;
                        }
                    }
                }
            if (mExportProgressDlg != null) {
                mExportProgressDlg.dismiss();
                mExportProgressDlg = null;
            }

            if (!isAirplaneMode && !isSimCardFull) {
                // if canceled, show toast indicating export is interrupted.
                if (canceled) {
                    mToastHandler.sendMessage(mToastHandler.obtainMessage(TOAST_EXPORT_CANCELED,
                            insertCount, 0));
                } else {
                    mToastHandler.sendEmptyMessage(TOAST_EXPORT_FINISHED);
                }
            }
            isExportingToSIM = false;
            Intent intent = new Intent(SimContactsConstants.INTENT_EXPORT_COMPLETE);
            mPeople.sendBroadcast(intent);
        }

        private Handler mToastHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                int exportCount = 0;
                switch (msg.what) {
                    case TOAST_EXPORT_FAILED:
                        exportCount = msg.arg1;
                        Toast.makeText(mPeople, mPeople.getString(R.string.export_to_sim_failed,
                                exportCount), Toast.LENGTH_SHORT).show();
                        break;
                    case TOAST_EXPORT_FINISHED:
                        Toast.makeText(mPeople, R.string.export_finished, Toast.LENGTH_SHORT)
                            .show();
                        break;

                    // add toast handler when sim card is full
                    case TOAST_SIM_CARD_FULL:
                        exportCount = msg.arg1;
                        Toast.makeText(mPeople, mPeople.getString(R.string.export_sim_card_full,
                                exportCount), Toast.LENGTH_SHORT).show();
                        break;

                    //add the max count limit of Chinese code or not
                    case TOAST_CONTACT_NAME_TOO_LONG:
                        Toast.makeText(mPeople, R.string.tag_too_long, Toast.LENGTH_SHORT).show();
                        break;

                     // add toast handler when export is canceled
                    case TOAST_EXPORT_CANCELED:
                        exportCount = msg.arg1;
                        Toast.makeText(mPeople,mPeople.getString(R.string.export_cancelled,
                            String.valueOf(exportCount)), Toast.LENGTH_SHORT).show();
                        break;

                    // add toast handler when no phone or email
                    case TOAST_EXPORT_NO_PHONE_OR_EMAIL:
                        String name = (String) msg.obj;
                        Toast.makeText(mPeople,
                                mPeople.getString(R.string.export_no_phone_or_email, name),
                                Toast.LENGTH_SHORT).show();
                        break;
                    case TOAST_SIM_CARD_NOT_LOAD_COMPLETE:
                        Toast.makeText(mPeople, R.string.sim_contacts_not_load,
                                Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

        public void showExportProgressDialog(Activity activity){
            mPeople = activity;
            mExportProgressDlg = new ProgressDialog(mPeople);
            mExportProgressDlg.setTitle(R.string.export_to_sim);
            mExportProgressDlg.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Log.d(TAG, "Cancel exporting contacts");
                    canceled = true;
                }
            });
            mExportProgressDlg.setMessage(mPeople.getString(R.string.exporting));
            mExportProgressDlg.setProgressNumberFormat(mPeople.getString(
                R.string.reading_vcard_files));
            mExportProgressDlg.setMax(contactList.size());
            //mExportProgressDlg.setProgress(insertCount);

            // set cancel dialog by touching outside disabled.
            mExportProgressDlg.setCanceledOnTouchOutside(false);

            // add a cancel button to let user cancel explicitly.
            mExportProgressDlg.setButton(DialogInterface.BUTTON_NEGATIVE,
                mPeople.getString(R.string.progressdialog_cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (DEBUG) {
                                Log.d(TAG, "Cancel exporting contacts by click button");
                            }
                            canceled = true;
                        }
                    });

            mExportProgressDlg.show();
        }
    }

    public ImportFromSimSelectListener listener;
    /**
     * Create a {@link Dialog} that allows the user to pick from a bulk import
     * or bulk export task across all contacts.
     */
    private Dialog displayImportExportDialog(int id, Bundle bundle) {
    Dialog diag;
        switch (id) {
            case R.string.import_from_sim_select: {
                    listener = new ImportFromSimSelectListener();
                    showSimSelectDialog();
                    break;
            }
            case R.string.export_to_sim: {
                String[] items = new String[TelephonyManager.getDefault().getPhoneCount()];
                for (int i = 0; i < items.length; i++) {
                items[i] = getString(R.string.export_to_sim) + ": "
                        + MoreContactUtils.getMultiSimAliasesName(mActivity, i);
                }
                mExportSub = PhoneConstants.SUB1;
                ExportToSimSelectListener listener = new ExportToSimSelectListener();
                return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.export_to_sim)
                    .setPositiveButton(android.R.string.ok, listener)
                    .setSingleChoiceItems(items, 0, listener).create();
            }
        }
        return null;
    }

    public void showSimSelectDialog() {
        AccountSelectionUtil.setImportSubscription(PhoneConstants.SUB1);
        // item is for sim account to show
        String[] items = new String[TelephonyManager.getDefault().getPhoneCount()];
        for (int i = 0; i < items.length; i++) {
            items[i] = getString(R.string.import_from_sim) + ": "
                    + MoreContactUtils.getMultiSimAliasesName(mActivity, i);
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.import_from_sim)
                .setPositiveButton(android.R.string.ok, listener)
                .setSingleChoiceItems(items, 0, listener).create().show();
    }

    private void handleImportFromSimRequest(int Id) {
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (MoreContactUtils.getEnabledSimCount() > 1) {
                displayImportExportDialog(R.string.import_from_sim_select
                ,null);
            } else {
                AccountSelectionUtil.setImportSubscription(getEnabledIccCard());
                handleImportRequest(Id);
            }
        } else {
            handleImportRequest(Id);
        }
    }

    private void handleExportToSimRequest(int Id) {
        if (MoreContactUtils.getEnabledSimCount() >1) {
            //has two enalbed sim cards, prompt dialog to select one
            displayImportExportDialog(Id, null).show();
        } else {
            mExportSub = getEnabledIccCard();
            Intent pickPhoneIntent = new Intent(
                    SimContactsConstants.ACTION_MULTI_PICK, Contacts.CONTENT_URI);
            // do not show the contacts in SIM card
            pickPhoneIntent.putExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER,
                    ContactListFilter
                            .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
            pickPhoneIntent.putExtra(EXT_NOT_SHOW_SIM_FLAG, true);
            pickPhoneIntent.putExtra(SimContactsConstants.IS_CONTACT,true);
            mActivity.startActivityForResult(pickPhoneIntent, SUBACTIVITY_MULTI_PICK_CONTACT);
        }
    }

    private boolean hasEnabledIccCard(int subscription) {
        return TelephonyManager.getDefault().hasIccCard(subscription)
                && TelephonyManager.getDefault().getSimState(subscription)
                == TelephonyManager.SIM_STATE_READY;
    }

    private int getEnabledIccCard() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            if (hasEnabledIccCard(i)) {
                return i;
            }
        }
        return PhoneConstants.SUB1;
    }

    private CharSequence getSubDescription(SubscriptionInfo record, boolean isImport) {
        CharSequence name = record.getDisplayName();
        if (TextUtils.isEmpty(record.getNumber())) {
            // Don't include the phone number in the description, since we don't know the number.
            return isImport ? getString(R.string.import_from_sim_summary_no_number, name) :
                    getString(R.string.export_to_sim_summary_no_number, name);
        }
        return isImport ? TextUtils.expandTemplate(getString(R.string.import_from_sim_summary)
                , name, PhoneNumberUtils.createTtsSpannable(record.getNumber())) :
                TextUtils.expandTemplate(getString(R.string.export_to_sim_summary)
                , name, PhoneNumberUtils.createTtsSpannable(record.getNumber()));
    }

    private static class AdapterEntry {
        public final CharSequence mLabel;
        public final int mChoiceResourceId;
        public final int mSubscriptionId;

        public AdapterEntry(CharSequence label, int resId, int subId) {
            mLabel = label;
            mChoiceResourceId = resId;
            mSubscriptionId = subId;
        }

        public AdapterEntry(String label, int resId) {
            // Store a nonsense value for mSubscriptionId. If this constructor is used,
            // the mSubscriptionId value should not be read later.
            this(label, resId, /* subId = */ -1);
        }
    }
}
