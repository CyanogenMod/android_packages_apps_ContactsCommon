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
package com.android.contacts.common.list;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import android.widget.Toast;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.R;
import com.android.contacts.common.model.Contact;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 * Also includes support for including the {@link ContactsContract.Profile} record in the
 * list.
 */
public abstract class ContactListAdapter extends ContactEntryListAdapter {

    protected static class ContactQuery {
        private static final String[] CONTACT_PROJECTION_PRIMARY = new String[] {
            Contacts._ID,                           // 0
            Contacts.DISPLAY_NAME_PRIMARY,          // 1
            Contacts.CONTACT_PRESENCE,              // 2
            Contacts.CONTACT_STATUS,                // 3
            Contacts.PHOTO_ID,                      // 4
            Contacts.PHOTO_THUMBNAIL_URI,           // 5
            Contacts.LOOKUP_KEY,                    // 6
            Contacts.IS_USER_PROFILE,               // 7
            Contacts.HAS_PHONE_NUMBER               // 8
        };

        private static final String[] CONTACT_PROJECTION_ALTERNATIVE = new String[] {
            Contacts._ID,                           // 0
            Contacts.DISPLAY_NAME_ALTERNATIVE,      // 1
            Contacts.CONTACT_PRESENCE,              // 2
            Contacts.CONTACT_STATUS,                // 3
            Contacts.PHOTO_ID,                      // 4
            Contacts.PHOTO_THUMBNAIL_URI,           // 5
            Contacts.LOOKUP_KEY,                    // 6
            Contacts.IS_USER_PROFILE,               // 7
            Contacts.HAS_PHONE_NUMBER,              // 8
        };

        private static final String[] FILTER_PROJECTION_PRIMARY = new String[] {
            Contacts._ID,                           // 0
            Contacts.DISPLAY_NAME_PRIMARY,          // 1
            Contacts.CONTACT_PRESENCE,              // 2
            Contacts.CONTACT_STATUS,                // 3
            Contacts.PHOTO_ID,                      // 4
            Contacts.PHOTO_THUMBNAIL_URI,           // 5
            Contacts.LOOKUP_KEY,                    // 6
            Contacts.IS_USER_PROFILE,               // 7
            Contacts.HAS_PHONE_NUMBER,              // 8
            SearchSnippetColumns.SNIPPET,           // 9
        };

        private static final String[] FILTER_PROJECTION_ALTERNATIVE = new String[] {
            Contacts._ID,                           // 0
            Contacts.DISPLAY_NAME_ALTERNATIVE,      // 1
            Contacts.CONTACT_PRESENCE,              // 2
            Contacts.CONTACT_STATUS,                // 3
            Contacts.PHOTO_ID,                      // 4
            Contacts.PHOTO_THUMBNAIL_URI,           // 5
            Contacts.LOOKUP_KEY,                    // 6
            Contacts.IS_USER_PROFILE,               // 7
            Contacts.HAS_PHONE_NUMBER,              // 8
            SearchSnippetColumns.SNIPPET,           // 9
        };

        public static final int CONTACT_ID               = 0;
        public static final int CONTACT_DISPLAY_NAME     = 1;
        public static final int CONTACT_PRESENCE_STATUS  = 2;
        public static final int CONTACT_CONTACT_STATUS   = 3;
        public static final int CONTACT_PHOTO_ID         = 4;
        public static final int CONTACT_PHOTO_URI        = 5;
        public static final int CONTACT_LOOKUP_KEY       = 6;
        public static final int CONTACT_IS_USER_PROFILE  = 7;
        public static final int CONTACT_HAS_NUMBER = 8;
        public static final int CONTACT_SNIPPET          = 8;
    }

    private CharSequence mUnknownNameText;

    private long mSelectedContactDirectoryId;
    private String mSelectedContactLookupKey;
    private long mSelectedContactId;
    private ContactListItemView.PhotoPosition mPhotoPosition;

    public ContactListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(R.string.missing_name);
    }

    public void setPhotoPosition(ContactListItemView.PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
    }

    public ContactListItemView.PhotoPosition getPhotoPosition() {
        return mPhotoPosition;
    }

    public CharSequence getUnknownNameText() {
        return mUnknownNameText;
    }

    public long getSelectedContactDirectoryId() {
        return mSelectedContactDirectoryId;
    }

    public String getSelectedContactLookupKey() {
        return mSelectedContactLookupKey;
    }

    public long getSelectedContactId() {
        return mSelectedContactId;
    }

    public void setSelectedContact(long selectedDirectoryId, String lookupKey, long contactId) {
        mSelectedContactDirectoryId = selectedDirectoryId;
        mSelectedContactLookupKey = lookupKey;
        mSelectedContactId = contactId;
    }

    protected static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(ContactQuery.CONTACT_DISPLAY_NAME);
    }

    /**
     * Builds the {@link Contacts#CONTENT_LOOKUP_URI} for the given
     * {@link ListView} position.
     */
    public Uri getContactUri(int position) {
        int partitionIndex = getPartitionForPosition(position);
        Cursor item = (Cursor)getItem(position);
        return item != null ? getContactUri(partitionIndex, item) : null;
    }

    public Uri getContactUri(int partitionIndex, Cursor cursor) {
        long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
        String lookupKey = cursor.getString(ContactQuery.CONTACT_LOOKUP_KEY);
        Uri uri = Contacts.getLookupUri(contactId, lookupKey);
        long directoryId = ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        if (directoryId != Directory.DEFAULT) {
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
        }
        return uri;
    }

    /**
     * Returns true if the specified contact is selected in the list. For a
     * contact to be shown as selected, we need both the directory and and the
     * lookup key to be the same. We are paying no attention to the contactId,
     * because it is volatile, especially in the case of directories.
     */
    public boolean isSelectedContact(int partitionIndex, Cursor cursor) {
        long directoryId = ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        if (getSelectedContactDirectoryId() != directoryId) {
            return false;
        }
        String lookupKey = getSelectedContactLookupKey();
        if (lookupKey != null && TextUtils.equals(lookupKey,
                cursor.getString(ContactQuery.CONTACT_LOOKUP_KEY))) {
            return true;
        }

        return directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE
                && getSelectedContactId() == cursor.getLong(ContactQuery.CONTACT_ID);
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        if (isQuickContactEnabled()) {
            view.setQuickContactEnabled(isQuickContactEnabled());
            view.setQuickCallButtonImageResource(R.drawable.ic_action_call);
            view.setQuickCallButtonBackgroundResource(
                    R.drawable.ic_action_call_background);
        }
        view.setActivatedStateSupported(isSelectionVisible());
        if (mPhotoPosition != null) {
            view.setPhotoPosition(mPhotoPosition);
        }
        return view;
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            String lookup = ((ContactListItemView) view.getParent()).getQuickCallLookup();
            Cursor cursor = mContext.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY},
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY + "=?",
                    new String[] { lookup }, null);

            if (cursor != null) {
                if (cursor.moveToNext()) {
                    String phoneNumber = cursor.getString(0);
                    Uri uri = Uri.parse("tel: " + phoneNumber);
                    Intent intent = new Intent(Intent.ACTION_CALL, uri);
                    mContext.startActivity(intent);
                }
                cursor.close();
            }
        }
    };

    protected void bindSectionHeaderAndDivider(ContactListItemView view, int position,
            Cursor cursor) {
        if (isSectionHeaderDisplayEnabled()) {
            Placement placement = getItemPlacementInSection(position);

            // First position, set the contacts number string
            if (position == 0 && cursor.getInt(ContactQuery.CONTACT_IS_USER_PROFILE) == 1) {
                view.setCountView(getContactsCount());
            } else {
                view.setCountView(null);
            }
            view.setSectionHeader(placement.sectionHeader);
            view.setDividerVisible(!placement.lastInSection);
        } else {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
            view.setCountView(null);
        }
    }

    protected void bindPhoto(final ContactListItemView view, int partitionIndex, Cursor cursor) {
        if (!isPhotoSupported(partitionIndex)) {
            view.removePhotoView();
            return;
        }

        // Set the photo, if available
        long photoId = 0;
        if (!cursor.isNull(ContactQuery.CONTACT_PHOTO_ID)) {
            photoId = cursor.getLong(ContactQuery.CONTACT_PHOTO_ID);
        }

        if (photoId != 0) {
            getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, null);
        } else {
            final String photoUriString = cursor.getString(ContactQuery.CONTACT_PHOTO_URI);
            final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
            DefaultImageRequest request = null;
            if (photoUri == null) {
                String displayName = cursor.getString(ContactQuery.CONTACT_DISPLAY_NAME);
                String lookupKey = cursor.getString(ContactQuery.CONTACT_LOOKUP_KEY);
                request = new DefaultImageRequest(displayName, lookupKey);
            }
            getPhotoLoader().loadDirectoryPhoto(view.getPhotoView(), photoUri, false, request);
        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(
                cursor, ContactQuery.CONTACT_DISPLAY_NAME, getContactNameDisplayOrder());
        // Note: we don't show phonetic any more (See issue 5265330)
    }

    protected void bindQuickCallView(final ContactListItemView view, Cursor cursor) {
        view.showQuickCallView(cursor, ContactQuery.CONTACT_HAS_NUMBER,
                ContactQuery.CONTACT_LOOKUP_KEY);
        view.setOnQuickCallClickListener(mClickListener);
    }

    protected void bindPresenceAndStatusMessage(final ContactListItemView view, Cursor cursor) {
        view.showPresenceAndStatusMessage(cursor, ContactQuery.CONTACT_PRESENCE_STATUS,
                ContactQuery.CONTACT_CONTACT_STATUS);
    }

    protected void bindSearchSnippet(final ContactListItemView view, Cursor cursor) {
        view.showSnippet(cursor, ContactQuery.CONTACT_SNIPPET);
    }

    public int getSelectedContactPosition() {
        if (mSelectedContactLookupKey == null && mSelectedContactId == 0) {
            return -1;
        }

        Cursor cursor = null;
        int partitionIndex = -1;
        int partitionCount = getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            DirectoryPartition partition = (DirectoryPartition) getPartition(i);
            if (partition.getDirectoryId() == mSelectedContactDirectoryId) {
                partitionIndex = i;
                break;
            }
        }
        if (partitionIndex == -1) {
            return -1;
        }

        cursor = getCursor(partitionIndex);
        if (cursor == null) {
            return -1;
        }

        cursor.moveToPosition(-1);      // Reset cursor
        int offset = -1;
        while (cursor.moveToNext()) {
            if (mSelectedContactLookupKey != null) {
                String lookupKey = cursor.getString(ContactQuery.CONTACT_LOOKUP_KEY);
                if (mSelectedContactLookupKey.equals(lookupKey)) {
                    offset = cursor.getPosition();
                    break;
                }
            }
            if (mSelectedContactId != 0 && (mSelectedContactDirectoryId == Directory.DEFAULT
                    || mSelectedContactDirectoryId == Directory.LOCAL_INVISIBLE)) {
                long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
                if (contactId == mSelectedContactId) {
                    offset = cursor.getPosition();
                    break;
                }
            }
        }
        if (offset == -1) {
            return -1;
        }

        int position = getPositionForPartition(partitionIndex) + offset;
        if (hasHeader(partitionIndex)) {
            position++;
        }
        return position;
    }

    public boolean hasValidSelection() {
        return getSelectedContactPosition() != -1;
    }

    public Uri getFirstContactUri() {
        int partitionCount = getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            DirectoryPartition partition = (DirectoryPartition) getPartition(i);
            if (partition.isLoading()) {
                continue;
            }

            Cursor cursor = getCursor(i);
            if (cursor == null) {
                continue;
            }

            if (!cursor.moveToFirst()) {
                continue;
            }

            return getContactUri(i, cursor);
        }

        return null;
    }

    @Override
    public void changeCursor(int partitionIndex, Cursor cursor) {
        super.changeCursor(partitionIndex, cursor);

        // Check if a profile exists
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            setProfileExists(cursor.getInt(ContactQuery.CONTACT_IS_USER_PROFILE) == 1);
        }
    }

    /**
     * @return Projection useful for children.
     */
    protected final String[] getProjection(boolean forSearch) {
        final int sortOrder = getContactNameDisplayOrder();
        if (forSearch) {
            if (sortOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                return ContactQuery.FILTER_PROJECTION_PRIMARY;
            } else {
                return ContactQuery.FILTER_PROJECTION_ALTERNATIVE;
            }
        } else {
            if (sortOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                return ContactQuery.CONTACT_PROJECTION_PRIMARY;
            } else {
                return ContactQuery.CONTACT_PROJECTION_ALTERNATIVE;
            }
        }
    }
}
