/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.contacts.common.util;

import java.util.ArrayList;
import java.util.HashMap;

import com.android.contacts.common.list.DefaultContactListAdapter;

import android.provider.ContactsContract.CommonDataKinds.Phone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Data;

public class ContactsCommonRcsUtil {

    public static final String RCS_CAPABILITY_CHANGED = "rcs_capability_changed";

    public static final String RCS_CAPABILITY_CHANGED_CONTACT_ID = "rcs_capability_changed_contact_id";

    public static final String RCS_CAPABILITY_CHANGED_VALUE = "rcs_capability_changed_value";

    public static final String RCS_CAPABILITY_MIMETYPE = "vnd.android.cursor.item/capability";

    public static final HashMap<Long, Boolean> RcsCapabilityMap = new HashMap<Long, Boolean>();

    public static final HashMap<Long, Boolean> RcsCapabilityMapCache = new HashMap<Long, Boolean>();

    private static boolean isRcs = false;

    // private static long rcsCapabilityUpdatedContactId = -1;

    /*
     * public static long getRcsCapabilityUpdatedId() { return
     * rcsCapabilityUpdatedContactId; } public static void
     * setRcsCapabilityUpdatedId(long id) { rcsCapabilityUpdatedContactId = id;
     * }
     */

    public static boolean getIsRcs() {
        return isRcs;
    }

    public static void setIsRcs(boolean flag) {
        isRcs = flag;
    }

    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int)(dipValue * scale + 0.5f);
    }

    public static void loadRcsCapabilityOfContacts(final Context context,
            final DefaultContactListAdapter adapter) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ContentResolver resolver = context.getContentResolver();
                Cursor c = resolver.query(Contacts.CONTENT_URI, new String[] {
                    Contacts._ID
                }, null, null, null);
                ArrayList<Long> contactIdList = new ArrayList<Long>();
                try {
                    if (c != null && c.moveToFirst()) {
                        do {
                            Long contactId = c.getLong(0);
                            contactIdList.add(contactId);
                        } while (c.moveToNext());
                    }
                } finally {
                    if(null != c){
                        c.close();
                    }
                }
                for (Long contactId : contactIdList) {
                    if (ContactsCommonRcsUtil.isRCSUser(context, contactId)) {
                        RcsCapabilityMap.put(contactId, true);
                    } else {
                        RcsCapabilityMap.put(contactId, false);
                    }
                }
                return null;
            }

            protected void onPostExecute(Void result) {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }.execute();
    }

    public static boolean isRCSUser(final Context context, long contactId) {
        Cursor c = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                new String[] {
                    ContactsContract.Data.DATA2
                }, Data.MIMETYPE + " = ?  and " + ContactsContract.Data.DATA1 + " = ?",
                new String[] {
                        RCS_CAPABILITY_MIMETYPE, String.valueOf(contactId)
                }, null);
        try {
            if (c != null && c.moveToFirst()) {
                do {
                    if (c.getInt(0) == 1) {
                        return true;
                    }
                } while (c.moveToNext());
            }
        } finally {
            if(null != c){
                c.close();
            }
        }
        return false;
    }
}
