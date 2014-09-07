/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.common;

import android.accounts.Account;
import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.SimAccountType;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.msim.IIccPhoneBookMSim;

import java.util.ArrayList;

/**
 * Shared static contact utility methods.
 */
public class MoreContactUtils {

    private static final String WAIT_SYMBOL_AS_STRING = String.valueOf(PhoneNumberUtils.WAIT);
    private static final boolean DBG = true;
    private static final String TAG = "MoreContactUtils";
    private static final int MAX_LENGTH_NAME_IN_SIM = 14;
    private static final int MAX_LENGTH_NAME_WITH_CHINESE_IN_SIM = 6;
    public static final int MAX_LENGTH_NUMBER_IN_SIM = 20;
    public static final int MAX_LENGTH_EMAIL_IN_SIM = 40;
    private static final int NAME_POS = 0;
    private static final int NUMBER_POS = 1;
    private static final int EMAIL_POS = 2;
    private static final int ANR_POS = 3;
    private static final String PHONEBOOK_MSIM = "simphonebook_msim";
    private static final String PHONEBOOK = "simphonebook";
    public static final String[] MULTI_SIM_NAME = { "perferred_name_sub1",
            "perferred_name_sub2" };

    public static final String[] IPCALL_PREFIX = { "ip_call_prefix_sub1",
            "ip_call_prefix_sub2" };

    /**
     * Returns true if two data with mimetypes which represent values in contact entries are
     * considered equal for collapsing in the GUI. For caller-id, use
     * {@link android.telephony.PhoneNumberUtils#compare(android.content.Context, String, String)}
     * instead
     */
    public static boolean shouldCollapse(CharSequence mimetype1, CharSequence data1,
              CharSequence mimetype2, CharSequence data2) {
        // different mimetypes? don't collapse
        if (!TextUtils.equals(mimetype1, mimetype2)) return false;

        // exact same string? good, bail out early
        if (TextUtils.equals(data1, data2)) return true;

        // so if either is null, these two must be different
        if (data1 == null || data2 == null) return false;

        // if this is not about phone numbers, we know this is not a match (of course, some
        // mimetypes could have more sophisticated matching is the future, e.g. addresses)
        if (!TextUtils.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                mimetype1)) {
            return false;
        }

        return shouldCollapsePhoneNumbers(data1.toString(), data2.toString());
    }

    private static boolean shouldCollapsePhoneNumbers(String number1, String number2) {
        // Now do the full phone number thing. split into parts, separated by waiting symbol
        // and compare them individually
        final String[] dataParts1 = number1.split(WAIT_SYMBOL_AS_STRING);
        final String[] dataParts2 = number2.split(WAIT_SYMBOL_AS_STRING);
        if (dataParts1.length != dataParts2.length) return false;
        final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        for (int i = 0; i < dataParts1.length; i++) {
            // Match phone numbers represented by keypad letters, in which case prefer the
            // phone number with letters.
            final String dataPart1 = PhoneNumberUtils.convertKeypadLettersToDigits(dataParts1[i]);
            final String dataPart2 = dataParts2[i];

            // substrings equal? shortcut, don't parse
            if (TextUtils.equals(dataPart1, dataPart2)) continue;

            // do a full parse of the numbers
            final PhoneNumberUtil.MatchType result = util.isNumberMatch(dataPart1, dataPart2);
            switch (result) {
                case NOT_A_NUMBER:
                    // don't understand the numbers? let's play it safe
                    return false;
                case NO_MATCH:
                    return false;
                case EXACT_MATCH:
                    break;
                case NSN_MATCH:
                    try {
                        // For NANP phone numbers, match when one has +1 and the other does not.
                        // In this case, prefer the +1 version.
                        if (util.parse(dataPart1, null).getCountryCode() == 1) {
                            // At this point, the numbers can be either case 1 or 2 below....
                            //
                            // case 1)
                            // +14155551212    <--- country code 1
                            //  14155551212    <--- 1 is trunk prefix, not country code
                            //
                            // and
                            //
                            // case 2)
                            // +14155551212
                            //   4155551212
                            //
                            // From b/7519057, case 2 needs to be equal.  But also that bug, case 3
                            // below should not be equal.
                            //
                            // case 3)
                            // 14155551212
                            //  4155551212
                            //
                            // So in order to make sure transitive equality is valid, case 1 cannot
                            // be equal.  Otherwise, transitive equality breaks and the following
                            // would all be collapsed:
                            //   4155551212  |
                            //  14155551212  |---->   +14155551212
                            // +14155551212  |
                            //
                            // With transitive equality, the collapsed values should be:
                            //   4155551212  |         14155551212
                            //  14155551212  |---->   +14155551212
                            // +14155551212  |

                            // Distinguish between case 1 and 2 by checking for trunk prefix '1'
                            // at the start of number 2.
                            if (dataPart2.trim().charAt(0) == '1') {
                                // case 1
                                return false;
                            }
                            break;
                        }
                    } catch (NumberParseException e) {
                        // This is the case where the first number does not have a country code.
                        // examples:
                        // (123) 456-7890   &   123-456-7890  (collapse)
                        // 0049 (8092) 1234   &   +49/80921234  (unit test says do not collapse)

                        // Check the second number.  If it also does not have a country code, then
                        // we should collapse.  If it has a country code, then it's a different
                        // number and we should not collapse (this conclusion is based on an
                        // existing unit test).
                        try {
                            util.parse(dataPart2, null);
                        } catch (NumberParseException e2) {
                            // Number 2 also does not have a country.  Collapse.
                            break;
                        }
                    }
                    return false;
                case SHORT_NSN_MATCH:
                    return false;
                default:
                    throw new IllegalStateException("Unknown result value from phone number " +
                            "library");
            }
        }
        return true;
    }

    /**
     * Returns the {@link android.graphics.Rect} with left, top, right, and bottom coordinates
     * that are equivalent to the given {@link android.view.View}'s bounds. This is equivalent to
     * how the target {@link android.graphics.Rect} is calculated in
     * {@link android.provider.ContactsContract.QuickContact#showQuickContact}.
     */
    public static Rect getTargetRectFromView(Context context, View view) {
        final float appScale = context.getResources().getCompatibilityInfo().applicationScale;
        final int[] pos = new int[2];
        view.getLocationOnScreen(pos);

        final Rect rect = new Rect();
        rect.left = (int) (pos[0] * appScale + 0.5f);
        rect.top = (int) (pos[1] * appScale + 0.5f);
        rect.right = (int) ((pos[0] + view.getWidth()) * appScale + 0.5f);
        rect.bottom = (int) ((pos[1] + view.getHeight()) * appScale + 0.5f);
        return rect;
    }

    /**
     * Returns a header view based on the R.layout.list_separator, where the
     * containing {@link android.widget.TextView} is set using the given textResourceId.
     */
    public static View createHeaderView(Context context, int textResourceId) {
        View view = View.inflate(context, R.layout.list_separator, null);
        TextView textView = (TextView) view.findViewById(R.id.title);
        textView.setText(context.getString(textResourceId));
        textView.setAllCaps(true);
        return view;
    }

    /**
     * Returns the intent to launch for the given invitable account type and contact lookup URI.
     * This will return null if the account type is not invitable (i.e. there is no
     * {@link AccountType#getInviteContactActivityClassName()} or
     * {@link AccountType#syncAdapterPackageName}).
     */
    public static Intent getInvitableIntent(AccountType accountType, Uri lookupUri) {
        String syncAdapterPackageName = accountType.syncAdapterPackageName;
        String className = accountType.getInviteContactActivityClassName();
        if (TextUtils.isEmpty(syncAdapterPackageName) || TextUtils.isEmpty(className)) {
            return null;
        }
        Intent intent = new Intent();
        intent.setClassName(syncAdapterPackageName, className);

        intent.setAction(ContactsContract.Intents.INVITE_CONTACT);

        // Data is the lookup URI.
        intent.setData(lookupUri);
        return intent;
    }

    public static int getSubscription(String accountType, String accountName) {
        int subscription = -1;
        if (accountType == null || accountName == null)
            return subscription;
        if (accountType.equals(SimContactsConstants.ACCOUNT_TYPE_SIM)) {
            if (accountName.equals(SimContactsConstants.SIM_NAME)
                    || accountName.equals(SimContactsConstants.SIM_NAME_1)) {
                subscription = SimContactsConstants.SUB_1;
            } else if (accountName.equals(SimContactsConstants.SIM_NAME_2)) {
                subscription = SimContactsConstants.SUB_2;
            }
        }
        return subscription;
    }

    public static void insertToPhone(String[] values, final ContentResolver resolver,int sub) {
        Account account = getAccount(sub);
        final String name = values[NAME_POS];
        final String phoneNumber = values[NUMBER_POS];
        final String emailAddresses = values[EMAIL_POS];
        final String anrs = values[ANR_POS];

        final String[] emailAddressArray;
        final String[] anrArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(",");
        } else {
            anrArray = null;
        }
        if (DBG) {
            Log.d(TAG, "insertToPhone: name= " + name + ", phoneNumber= " + phoneNumber
                    + ", emails= " + emailAddresses + ", anrs= " + anrs + ", account= " + account);
        }
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder = ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);

        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
        }
        operationList.add(builder.build());

        // do not allow empty value insert into database.
        if (!TextUtils.isEmpty(name)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            builder.withValue(StructuredName.DISPLAY_NAME, name);
            operationList.add(builder.build());
        }

        if (!TextUtils.isEmpty(phoneNumber)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
            builder.withValue(Phone.NUMBER, phoneNumber);
            builder.withValue(Data.IS_PRIMARY, 1);
            operationList.add(builder.build());
        }

        if (anrArray != null) {
            for (String anr : anrArray) {
                if (!TextUtils.isEmpty(anr)) {
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                    builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                    builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                    builder.withValue(Phone.NUMBER, anr);
                    operationList.add(builder.build());
                }
            }
        }

        if (emailAddressArray != null) {
            for (String emailAddress : emailAddressArray) {
                if (!TextUtils.isEmpty(emailAddress)) {
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                    builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                    builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                    builder.withValue(Email.ADDRESS, emailAddress);
                    operationList.add(builder.build());
                }
            }
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    public static Uri insertToCard(Context context, String name, String number, String emails,
            String anrNumber, int subscription) {
        // add the max count limit of Chinese code or not
        if (!TextUtils.isEmpty(name)) {
            final int maxLen = hasChinese(name) ? MAX_LENGTH_NAME_WITH_CHINESE_IN_SIM
                    : MAX_LENGTH_NAME_IN_SIM;
            if (name.length() > maxLen) {
                name = name.substring(0, maxLen);
            }
        }
        Uri result;
        ContentValues mValues = new ContentValues();
        mValues.clear();
        mValues.put(SimContactsConstants.STR_TAG, name);
        if (!TextUtils.isEmpty(number)) {
            number = number.replaceAll("[^0123456789PWN\\,\\;\\*\\#\\+]", "");
            if (number.length() > MAX_LENGTH_NUMBER_IN_SIM) {
                number = number.substring(0, MAX_LENGTH_NUMBER_IN_SIM);
            }

            mValues.put(SimContactsConstants.STR_NUMBER, number);
        }
        if (!TextUtils.isEmpty(emails)) {
            mValues.put(SimContactsConstants.STR_EMAILS, emails);
        }
        if (!TextUtils.isEmpty(anrNumber)) {
            anrNumber = anrNumber.replaceAll("[^0123456789PWN\\,\\;\\*\\#\\+]", "");
            mValues.put(SimContactsConstants.STR_ANRS, anrNumber);
        }

        SimContactsOperation mSimContactsOperation = new SimContactsOperation(context);
        result = mSimContactsOperation.insert(mValues, subscription);

        if (result != null) {
            // we should import the contact to the sim account at the same time.
            String[] value = new String[] {
                    name, number, emails, anrNumber
            };
            insertToPhone(value, context.getContentResolver(),subscription);
        } else {
            Log.e(TAG, "export contact: [" + name + ", " + number + ", " + emails + "] to slot "
                    + subscription + " failed");
        }
        return result;
    }

    private static Account getAccount(int sub) {
        Account account = null;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            if (sub == SimContactsConstants.SUB_1) {
                account = new Account(SimContactsConstants.SIM_NAME_1,
                        SimContactsConstants.ACCOUNT_TYPE_SIM);
            } else if (sub == SimContactsConstants.SUB_2) {
                account = new Account(SimContactsConstants.SIM_NAME_2,
                        SimContactsConstants.ACCOUNT_TYPE_SIM);
            }
        } else {
            if (sub == SimContactsConstants.SUB_1) {
                account = new Account(SimContactsConstants.SIM_NAME,
                        SimContactsConstants.ACCOUNT_TYPE_SIM);
            }
        }
        if (account == null) {
            account = new Account(SimContactsConstants.PHONE_NAME,
                    SimContactsConstants.ACCOUNT_TYPE_PHONE);
        }
        return account;
    }

    public static int getSimFreeCount(Context context, int sub) {
        String accountName = getAccount(sub).name;
        int count = 0;

        if (context == null) {
            return 0;
        }

        Cursor queryCursor = context.getContentResolver().query(
                RawContacts.CONTENT_URI,
                new String[] {
                    RawContacts._ID
                },
                RawContacts.ACCOUNT_NAME + " = '" + accountName + "' AND " + RawContacts.DELETED
                        + " = 0", null, null);
        if (queryCursor != null) {
            try {
                count = queryCursor.getCount();
            } finally {
                queryCursor.close();
            }
        }
        return getAdnCount(sub) - count;
    }

    public static int getAnrCount(int sub) {
        int anrCount = 0;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK_MSIM));
                if (iccIpb != null) {
                    anrCount = iccIpb.getAnrCount(sub);
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        } else {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK));
                if (iccIpb != null) {
                    anrCount = iccIpb.getAnrCount();
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        }
        if (DBG) {
            Log.d(TAG, "getAnrCount(" + sub + ") = " + anrCount);
        }
        return anrCount;
    }

    public static int getOneSimAnrCount(int sub) {
        int count = 0;
        int anrCount = getAnrCount(sub);
        int adnCount = getAdnCount(sub);
        if (adnCount > 0) {
            count = anrCount % adnCount != 0 ? (anrCount / adnCount + 1)
                    : (anrCount / adnCount);
        }
        return count;
    }

    public static int getOneSimEmailCount(int sub) {
        int count = 0;
        int emailCount = getEmailCount(sub);
        int adnCount = getAdnCount(sub);
        if (adnCount > 0) {
            count = emailCount % adnCount != 0 ? (emailCount
                    / adnCount + 1)
                    : (emailCount / adnCount);
        }
        return count;
    }

    public static int getEmailCount(int sub) {
        int emailCount = 0;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK_MSIM));
                if (iccIpb != null) {
                    emailCount = iccIpb.getEmailCount(sub);
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        } else {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK));
                if (iccIpb != null) {
                    emailCount = iccIpb.getEmailCount();
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        }
        if (DBG) {
            Log.d(TAG, "getEmailCount(" + sub + ") = " + emailCount);
        }
        return emailCount;
    }

    public static int getSpareAnrCount(int sub) {
        int anrCount = 0;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK_MSIM));
                if (iccIpb != null) {
                    anrCount = iccIpb.getSpareAnrCount(sub);
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        } else {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK));
                if (iccIpb != null) {
                    anrCount = iccIpb.getSpareAnrCount();
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        }
        if (DBG) {
            Log.d(TAG, "getSpareAnrCount(" + sub + ") = " + anrCount);
        }
        return anrCount;
    }

    public static int getSpareEmailCount(int sub) {
        int emailCount = 0;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK_MSIM));
                if (iccIpb != null) {
                    emailCount = iccIpb.getSpareEmailCount(sub);
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        } else {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK));
                if (iccIpb != null) {
                    emailCount = iccIpb.getSpareEmailCount();
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        }
        if (DBG) {
            Log.d(TAG, "getSpareEmailCount(" + sub + ") = " + emailCount);
        }
        return emailCount;
    }

    private static boolean hasChinese(String name) {
        return name != null && name.getBytes().length > name.length();
    }

    public static int getAdnCount(int sub) {
        int adnCount = 0;
        if (sub == -1) {
            return Integer.MAX_VALUE;
        }
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            try {
                IIccPhoneBookMSim iccIpb = IIccPhoneBookMSim.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK_MSIM));
                if (iccIpb != null) {
                    adnCount = iccIpb.getAdnCount(sub);
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString());
            } catch (Exception ex) {
            }
        } else {
            try {
                IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(ServiceManager
                        .getService(PHONEBOOK));
                if (iccIpb != null) {
                    adnCount = iccIpb.getAdnCount();
                }
            } catch (RemoteException ex) {
                // ignore it
            } catch (SecurityException ex) {
                Log.i(TAG, ex.toString(), (new Exception()));
            } catch (Exception ex) {
            }
        }
        if (DBG) {
            Log.d(TAG, "getAdnCount(" + sub + ") = " + adnCount);
        }
        return adnCount;
    }

    /**
     * Returns the subscription's card can save anr or not.
     */
    public static boolean canSaveAnr(int subscription) {
        return getAnrCount(subscription) > 0 ? true : false;
    }

    /**
     * Returns the subscription's card can save email or not.
     */
    public static boolean canSaveEmail(int subscription) {
        return getEmailCount(subscription) > 0 ? true : false;
    }

    /**
     * Check one SIM card is enabled
     */
    public static boolean isMultiSimEnable(Context context, int slotId) {
        MSimTelephonyManager mSimTelManager = getMSimTelephonyManager();
        if (mSimTelManager.isMultiSimEnabled()) {
            if (Settings.System.getInt(context.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) != 0
                    || TelephonyManager.SIM_STATE_READY != mSimTelManager
                            .getSimState(slotId)) {
                return false;
            }
            return true;
        } else {
            if (slotId == MSimConstants.SUB1)
                return true;
            return false;
        }
    }

    public static int getEnabledSimCount() {
        MSimTelephonyManager mSimTelManager = getMSimTelephonyManager();
        int mPhoneCount = mSimTelManager.getPhoneCount();
        int enabledSimCount = 0;
        for (int i = 0; i < mPhoneCount; i++) {
            if (TelephonyManager.SIM_STATE_READY == mSimTelManager.getSimState(i)) {
                enabledSimCount++;
            }
        }
        return enabledSimCount;
    }

    private static MSimTelephonyManager getMSimTelephonyManager() {
        return MSimTelephonyManager.getDefault();
    }

    /**
     * Get SIM card SPN name, e.g. China Union
     */
    public static String getSimSpnName(int subscription) {
        MSimTelephonyManager mSimTelManager = getMSimTelephonyManager();
        String simSpnName = "";
        simSpnName = mSimTelManager.getSimOperatorName(subscription);
        if (TextUtils.isEmpty(simSpnName)) {
            // if could not get the operator name, use account name instead of
            simSpnName = getSimAccountName(subscription);
        }
        return simSpnName;
    }

    /**
     * Check IP call number existing
     */
    public static boolean isIPNumberExist(Context mContext, int subscription) {
        if (mContext == null) {
            return false;
        }
        String ipCallPrefix = Settings.System.getString(mContext.getContentResolver(),
                IPCALL_PREFIX[subscription]);
        if (TextUtils.isEmpty(ipCallPrefix)) {
            return false;
        }
        return true;
    }

    /**
     * Display IP call setting dialog
     */
    public static void showNoIPNumberDialog(final Context mContext, final int subscription) {
        try {
            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.no_ip_number)
                    .setMessage(R.string.no_ip_number_on_sim_card)
                    .setPositiveButton(R.string.set_ip_number,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    setIPNumber(mContext, subscription);
                                }
                            }).setNegativeButton(android.R.string.cancel, null).show();
        } catch (Exception e) {
        }
    }

    /**
     * Setting IP Call number
     */
    public static void setIPNumber(final Context mContext, final int subscription) {
        try {
            LayoutInflater mInflater = LayoutInflater.from(mContext);
            View v = mInflater.inflate(R.layout.ip_prefix_dialog, null);
            final EditText edit = (EditText) v.findViewById(R.id.ip_prefix_dialog_edit);
            String ip_prefix = Settings.System.getString(mContext.getContentResolver(),
                    IPCALL_PREFIX[subscription]);
            edit.setText(ip_prefix);

            new AlertDialog.Builder(mContext).setTitle(R.string.ipcall_dialog_title)
                    .setIcon(android.R.drawable.ic_dialog_info).setView(v)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String ip_prefix = edit.getText().toString();
                            Settings.System.putString(mContext.getContentResolver(),
                                    IPCALL_PREFIX[subscription], ip_prefix);
                        }
                    }).setNegativeButton(android.R.string.cancel, null).show();
        } catch (Exception e) {
        }
    }

    /** get disabled SIM card's name */
    public static String getDisabledSimFilter() {
        int count = MSimTelephonyManager.getDefault().getPhoneCount();
        StringBuilder simFilter = new StringBuilder("");

        for (int i = 0; i < count; i++) {
            if (!MSimTelephonyManager.getDefault().hasIccCard(i)) {
                continue;
            }
            if (TelephonyManager.SIM_STATE_UNKNOWN == MSimTelephonyManager
                    .getDefault().getSimState(i)) {
                simFilter.append(getSimAccountName(i) + ',');
            }
        }

        return simFilter.toString();
    }

    public static boolean isAPMOnAndSIMPowerDown(Context context) {
        if (context == null) {
            return false;
        }
        boolean isAirPlaneMode = Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
        boolean isSIMPowerDown = SystemProperties.getInt("persist.radio.apm_sim_not_pwdn", 0) == 0;
        return isAirPlaneMode && isSIMPowerDown;
    }

    public static boolean sdCardExist(Context context) {
        String sdcardPath = getSDPath(context);
        if (sdcardPath == null) {
            return false;
        }
        StorageManager mStorageManager = (StorageManager) context
                .getSystemService(Context.STORAGE_SERVICE);
        return mStorageManager.getVolumeState(sdcardPath).equals(
                android.os.Environment.MEDIA_MOUNTED);
    }

    public static String getSDPath(Context context) {
        final String sdcardDesc = context.getString(com.android.internal.R.string.storage_sd_card);
        StorageManager mStorageManager = (StorageManager) context
                .getSystemService(Context.STORAGE_SERVICE);
        StorageVolume[] volumes = mStorageManager.getVolumeList();
        for (int i = 0; i < volumes.length; i++) {
            if (volumes[i].isRemovable() && volumes[i].allowMassStorage()
                    && volumes[i].getDescription(context).equals(sdcardDesc)) {
                return volumes[i].getPath();
            }
        }
        return null;
    }

    /**
     * Get SIM card account name
     */
    public static String getSimAccountName(int subscription) {
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            return SimContactsConstants.SIM_NAME + (subscription + 1);
        } else {
            return SimContactsConstants.SIM_NAME;
        }
    }

    /**
     * Get SIM card subscription from account name
     */
    public static int getSubFromAccountName(String accountName) {
        MSimTelephonyManager stm = getMSimTelephonyManager();
        if (stm.isMultiSimEnabled()) {
            int phonecount = stm.getPhoneCount();
            for (int i = 0; i < phonecount; i++) {
                if (getSimAccountName(i).equals(accountName)) {
                    return i;
                }
            }
        }
        return MSimConstants.DEFAULT_SUBSCRIPTION;
    }

    /**
     * Get SIM card aliases name, which defined in Settings
     */
    public static String getMultiSimAliasesName(Context context, int subscription) {
        if (context == null || subscription < 0) {
            return null;
        }
        MSimTelephonyManager stm = getMSimTelephonyManager();
        if (stm.isMultiSimEnabled()) {
            String name = Settings.System.getString(context.getContentResolver(),
                    MULTI_SIM_NAME[subscription]);
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
            return context.getString(R.string.account_sim) + " " + (subscription + 1);
        }

        return context.getString(R.string.account_sim);
    }
}
