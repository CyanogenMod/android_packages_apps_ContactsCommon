/*
 * Copyright (C) 2014, The Linux Foundation. All Rights Reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of The Linux Foundation nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package com.android.contacts.common;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.SimContactsConstants;

public class SimContactsOperation {

    private static final String  TAG = "SimContactsOperation";
    private static final boolean DBG = true;
    private static final int QUERY_TOKEN = 0;
    private static final int INSERT_TOKEN = 1;
    private static final int UPDATE_TOKEN = 2;
    private static final int DELETE_TOKEN = 3;

    public static final String[] ACCOUNT_PROJECTION = new String[] {
        RawContacts._ID,
        RawContacts.CONTACT_ID,
        RawContacts.ACCOUNT_NAME,
        RawContacts.ACCOUNT_TYPE,
    };


    private static final int ACCOUNT_COLUMN_RAW_ID = 0;
    private static final int ACCOUNT_COLUMN_CONTACT_ID = 1;
    private static final int ACCOUNT_COLUMN_NAME = 2;
    private static final int ACCOUNT_COLUMN_TYPE = 3;
    private static final int ACCOUNT_COLUMN_PHONE_NAME = 4;



    private static Context mContext;
    private ContentResolver mResolver;
    private ContentValues mValues = new ContentValues();


    public SimContactsOperation(Context context) {
        this.mContext = context;
        this.mResolver = context.getContentResolver();
    }


    public Uri insert(ContentValues values, int subscription) {

        Uri uri = getContentUri(subscription);
        if (uri == null) {
            return null;
        }
        String number = values.getAsString(SimContactsConstants.STR_NUMBER);
        String anrs = values.getAsString(SimContactsConstants.STR_ANRS);
        String emails = values.getAsString(SimContactsConstants.STR_EMAILS);
        values.put(SimContactsConstants.STR_NUMBER,PhoneNumberUtils.stripSeparators(number));
        values.put(SimContactsConstants.STR_ANRS,PhoneNumberUtils.stripSeparators(anrs));
        values.put(SimContactsConstants.STR_EMAILS,emails);

        Uri resultUri;
        resultUri = mResolver.insert(uri,values);
        return resultUri;
    }

    public int update(ContentValues values,int subscription) {
        Uri uri = getContentUri(subscription);
        if (uri == null) {
            return -1;
        }
        int result;
        String oldNumber = values.getAsString(SimContactsConstants.STR_NUMBER);
        String newNumber = values.getAsString(SimContactsConstants.STR_NEW_NUMBER);
        String oldAnrs = values.getAsString(SimContactsConstants.STR_ANRS);
        String newAnrs = values.getAsString(SimContactsConstants.STR_NEW_ANRS);
        values.put(SimContactsConstants.STR_NUMBER,PhoneNumberUtils.stripSeparators(oldNumber));
        values.put(SimContactsConstants.STR_NEW_NUMBER,PhoneNumberUtils.stripSeparators(newNumber));
        values.put(SimContactsConstants.STR_ANRS,PhoneNumberUtils.stripSeparators(oldAnrs));
        values.put(SimContactsConstants.STR_NEW_ANRS,PhoneNumberUtils.stripSeparators(newAnrs));

        result = mResolver.update(uri,values,null,null);
        return result;

    }

    public int delete(ContentValues values, int subscription) {
        int result;
        StringBuilder buf = new StringBuilder();
        String num = null;
        String name = values.getAsString(SimContactsConstants.STR_TAG);
        String number = values.getAsString(SimContactsConstants.STR_NUMBER);
        String emails = values.getAsString(SimContactsConstants.STR_EMAILS);
        String anrs = values.getAsString(SimContactsConstants.STR_ANRS);
        if (number != null)
            num = PhoneNumberUtils.stripSeparators(number);
        if (anrs != null)
            anrs = PhoneNumberUtils.stripSeparators(anrs);
        Uri uri = getContentUri(subscription);

        if (uri == null) {
            return -1;
        }


        if (!TextUtils.isEmpty(name)) {
            buf.append("tag='");
            buf.append(name);
            buf.append("'");
        }
        if (!TextUtils.isEmpty(num)) {
            buf.append(" AND number='");
            buf.append(num);
            buf.append("'");
        }
        if (!TextUtils.isEmpty(emails)) {
            buf.append(" AND emails='");
            buf.append(emails);
            buf.append("'");
        }
        if (!TextUtils.isEmpty(anrs)) {
            buf.append(" AND anrs='");
            buf.append(anrs);
            buf.append("'");
        }

        result = mResolver.delete(uri,buf.toString(),null);
        return result;

    }

    private Uri getContentUri(int subscription) {
        Uri uri = null;
        long[] subId = SubscriptionManager.getSubId(subscription);

        if (subId != null && TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (subId.length < 1 || subId[0] < 0) {
                return null;
            }
            uri = Uri.parse(SimContactsConstants.SIM_SUB_URI + subId[0]);
        } else {
            uri = Uri.parse(SimContactsConstants.SIM_URI);
        }
        return uri;
    }

    private static Cursor setupAccountCursor(long contactId) {
        ContentResolver resolver = mContext.getContentResolver();

        Cursor cursor = null;
        try {
            cursor = resolver.query(RawContacts.CONTENT_URI,
                    ACCOUNT_PROJECTION,
                    RawContacts.CONTACT_ID + "="
                    + Long.toString(contactId), null, null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor;
            }
            if (cursor != null) {
                cursor.close();
            }
            cursor = null;
            return null;
        }
    }

    public static ContentValues getSimAccountValues(long contactId) {
        ContentValues mValues = new ContentValues();
        Cursor cursor = setupAccountCursor(contactId);
        if (cursor == null || cursor.getCount() == 0) {
            mValues.clear();
            return mValues;
        }
        long rawContactId = cursor.getLong(cursor.getColumnIndex(RawContacts._ID));
        String accountName = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_NAME));
        String accountType = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_TYPE));
        cursor.close();
        if (SimContactsConstants.ACCOUNT_TYPE_SIM.equals(accountType)) {
            mValues.clear();
            String name = getContactItems(rawContactId,StructuredName.CONTENT_ITEM_TYPE,
                     ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
            mValues.put(SimContactsConstants.STR_TAG,name);

            String number = getContactPhoneNumber(rawContactId,
                Phone.CONTENT_ITEM_TYPE, String.valueOf(Phone.TYPE_MOBILE),
                ContactsContract.CommonDataKinds.Phone.DATA);
            mValues.put(SimContactsConstants.STR_NUMBER,number);

            int sub = getSimSubscription(contactId);

            if (MoreContactUtils.canSaveAnr(sub)) {
                String anrs = getContactPhoneNumber(rawContactId,
                        Phone.CONTENT_ITEM_TYPE, String.valueOf(Phone.TYPE_HOME),
                        ContactsContract.CommonDataKinds.Phone.DATA);
                mValues.put(SimContactsConstants.STR_ANRS, anrs);
            }

            if (MoreContactUtils.canSaveEmail(sub)) {
                String emails = getContactItems(rawContactId,
                        Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.DATA);
                mValues.put(SimContactsConstants.STR_EMAILS, emails);
            }
        }
        return mValues;
    }

    public static int getSimSubscription(long contactId) {
        int subscription = SimContactsConstants.SUB_INVALID;
        Cursor cursor = setupAccountCursor(contactId);
        if (cursor == null || cursor.getCount() == 0) {
            return subscription;
        }

        String accountName = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_NAME));
        String accountType = cursor.getString(cursor.getColumnIndex(RawContacts.ACCOUNT_TYPE));
        if (accountType == null || accountName == null) {
            return subscription;
        }
        if (SimContactsConstants.ACCOUNT_TYPE_SIM.equals(accountType)) {
            subscription = MoreContactUtils.getSubscription(accountType, accountName);
        }
        cursor.close();
        return subscription;
    }


    private static String getContactItems(long rawContactId, String selectionArg,
                                                String columnName) {
        StringBuilder retval = new StringBuilder();
        Uri baseUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, RawContacts.Data.CONTENT_DIRECTORY);

        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(dataUri, null,
                    Data.MIMETYPE + "=?", new String[] {selectionArg}, null);
            if (c == null || c.getCount() == 0) {
                if(c != null) {
                    c.close();
                }
                return null;
            }
            c.moveToPosition(-1);

            while (c.moveToNext()) {
                if (!TextUtils.isEmpty(retval.toString())) {
                    retval.append(",");
                }
                retval.append(c.getString(c.getColumnIndex(columnName)));
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return retval.toString();
    }

    private static
    String getContactPhoneNumber(long rawContactId, String selectionArg1,
                            String selectionArg2, String columnName) {
        StringBuilder retval = new StringBuilder();
        Uri baseUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, RawContacts.Data.CONTENT_DIRECTORY);

        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(dataUri, null,
                    Data.MIMETYPE + "=? AND " + Phone.TYPE + "=?",
                    new String[] {selectionArg1,selectionArg2}, null);
            if (c == null || c.getCount() == 0) {
                if(c != null) {
                    c.close();
                }
                return null;
            }
            c.moveToPosition(-1);

            while (c.moveToNext()) {
                if (!TextUtils.isEmpty(retval.toString())) {
                    retval.append(",");
                }
                retval.append(c.getString(c.getColumnIndex(columnName)));
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return retval.toString();
    }


    private void log(String msg) {
        if (DBG) Log.d(TAG,  msg);
    }

}
