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

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import com.android.contacts.common.model.account.SimAccountType;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.SimContactsConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public class DefaultContactListAdapter extends ContactListAdapter {

    public static final char SNIPPET_START_MATCH = '\u0001';
    public static final char SNIPPET_END_MATCH = '\u0001';
    public static final String SNIPPET_ELLIPSIS = "\u2026";
    public static final int SNIPPET_MAX_TOKENS = 5;
    public static final int AIRPLANE_MODE_ON_VALUE = 1;
    public static final int AIRPLANE_MODE_OFF_VALUE = 0;
    public static final String WITHOUT_SIM_FLAG = "no_sim";

    public static final String SNIPPET_ARGS = SNIPPET_START_MATCH + "," + SNIPPET_END_MATCH + ","
            + SNIPPET_ELLIPSIS + "," + SNIPPET_MAX_TOKENS;

    public DefaultContactListAdapter(Context context) {
        super(context);
    }

    /** append Uri QueryParameter to filter contacts in SIM card */
    private void appendUriQueryParameterWithoutSim(CursorLoader loader,
            String key, String value) {
        if (null == loader || null == key || null == value) {
            return;
        }

        Uri uri = loader.getUri();
        if (null != uri) {
            uri = uri.buildUpon().appendQueryParameter(key, value)
                    .appendQueryParameter(WITHOUT_SIM_FLAG, "true").build();
            loader.setUri(uri);
        }
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        if (loader instanceof ProfileAndContactsLoader) {
            ((ProfileAndContactsLoader) loader).setLoadProfile(shouldIncludeProfile());
        }

        ContactListFilter filter = getFilter();
        if (isSearchMode()) {
            String query = getQueryString();
            if (query == null) {
                query = "";
            }
            query = query.trim();
            if (TextUtils.isEmpty(query)) {
                // Regardless of the directory, we don't want anything returned,
                // so let's just send a "nothing" query to the local directory.
                loader.setUri(Contacts.CONTENT_URI);
                loader.setProjection(getProjection(false));
                loader.setSelection("0");
            } else {
                Builder builder = Contacts.CONTENT_FILTER_URI.buildUpon();
                builder.appendPath(query);      // Builder will encode the query
                builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId));
                if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
                    builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                            String.valueOf(getDirectoryResultLimit(getDirectoryById(directoryId))));
                }
                builder.appendQueryParameter(SearchSnippetColumns.SNIPPET_ARGS_PARAM_KEY,
                        SNIPPET_ARGS);
                builder.appendQueryParameter(SearchSnippetColumns.DEFERRED_SNIPPETING_KEY,"1");
                loader.setUri(builder.build());
                loader.setProjection(getProjection(true));
            }

            // Do not show contacts in SIM card when airplane mode is on
            boolean isAirMode = MoreContactUtils.isAPMOnAndSIMPowerDown(getContext());

            if ((null != filter && filter.filterType ==
                    ContactListFilter.FILTER_TYPE_ALL_WITHOUT_SIM) || isAirMode) {
                appendUriQueryParameterWithoutSim(loader, RawContacts.ACCOUNT_TYPE,
                        SimAccountType.ACCOUNT_TYPE);
            } else {
                // Do not show contacts when SIM card is disabled
                String disabledSimFilter = MoreContactUtils.getDisabledSimFilter();
                if (!TextUtils.isEmpty(disabledSimFilter)) {
                    appendUriQueryParameterWithoutSim(
                            loader, RawContacts.ACCOUNT_NAME, disabledSimFilter);
                }
            }

        } else {
            configureUri(loader, directoryId, filter);
            loader.setProjection(getProjection(false));
            configureSelection(loader, directoryId, filter);
        }

        String sortOrder;
        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            sortOrder = Contacts.SORT_KEY_PRIMARY;
        } else {
            sortOrder = Contacts.SORT_KEY_ALTERNATIVE;
        }

        loader.setSortOrder(sortOrder);
    }

    protected void configureUri(CursorLoader loader, long directoryId, ContactListFilter filter) {
        Uri uri = Contacts.CONTENT_URI;
        if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            String lookupKey = getSelectedContactLookupKey();
            if (lookupKey != null) {
                uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
            } else {
                uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, getSelectedContactId());
            }
        }

        if (directoryId == Directory.DEFAULT && isSectionHeaderDisplayEnabled()) {
            uri = ContactListAdapter.buildSectionIndexerUri(uri);
        }

        // The "All accounts" filter is the same as the entire contents of Directory.DEFAULT
        if (filter != null
                && filter.filterType != ContactListFilter.FILTER_TYPE_CUSTOM
                && filter.filterType != ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            final Uri.Builder builder = uri.buildUpon();
            builder.appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT));
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                filter.addAccountQueryParameterToUrl(builder);
            }
            uri = builder.build();
        }

        loader.setUri(uri);
    }

    private void configureSelection(
            CursorLoader loader, long directoryId, ContactListFilter filter) {
        if (filter == null) {
            return;
        }

        if (directoryId != Directory.DEFAULT) {
            return;
        }

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();

        boolean isAirMode = MoreContactUtils.isAPMOnAndSIMPowerDown(getContext());
        String disabledSimFilter = MoreContactUtils.getDisabledSimFilter();

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                // We have already added directory=0 to the URI, which takes care of this
                // filter
                // Do not show contacts in SIM card when airplane mode is on
                if (isAirMode) {
                    appendUriQueryParameterWithoutSim(loader, RawContacts.ACCOUNT_TYPE,
                            SimAccountType.ACCOUNT_TYPE);
                } else if (!TextUtils.isEmpty(disabledSimFilter)) {
                    appendUriQueryParameterWithoutSim(loader, RawContacts.ACCOUNT_NAME,
                            disabledSimFilter);
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT: {
                // We have already added the lookup key to the URI, which takes care of this
                // filter
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                selection.append(Contacts.STARRED + "!=0");
                break;
            }
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
                selection.append(Contacts.HAS_PHONE_NUMBER + "=1");
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                selection.append(Contacts.IN_VISIBLE_GROUP + "=1");
                if (isCustomFilterForPhoneNumbersOnly()) {
                    selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                }
                // Do not show contacts in SIM card when airplane mode is on
                if (isAirMode) {
                    appendUriQueryParameterWithoutSim(loader, RawContacts.ACCOUNT_TYPE,
                            SimAccountType.ACCOUNT_TYPE);
                } else if (!TextUtils.isEmpty(disabledSimFilter)) {
                    appendUriQueryParameterWithoutSim(loader, RawContacts.ACCOUNT_NAME,
                            disabledSimFilter);
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_ALL_WITHOUT_SIM: {
                appendUriQueryParameterWithoutSim(loader, RawContacts.ACCOUNT_TYPE,
                        SimAccountType.ACCOUNT_TYPE);
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                break;
            }
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView)itemView;

        view.setHighlightedPrefix(isSearchMode() ? getUpperCaseQueryString() : null);

        if (isSelectionVisible()) {
            view.setActivated(isSelectedContact(partition, cursor));
        }

        bindSectionHeaderAndDivider(view, position, cursor);

        if (isQuickContactEnabled()) {
            bindQuickContact(view, partition, cursor, ContactQuery.CONTACT_PHOTO_ID,
                    ContactQuery.CONTACT_PHOTO_URI, ContactQuery.CONTACT_ID,
                    ContactQuery.CONTACT_LOOKUP_KEY, ContactQuery.CONTACT_DISPLAY_NAME);
        } else {
            if (getDisplayPhotos()) {
                bindPhoto(view, partition, cursor);
            }
        }

        bindName(view, cursor);
        bindPresenceAndStatusMessage(view, cursor);

        if (isSearchMode()) {
            bindSearchSnippet(view, cursor);
        } else {
            view.setSnippet(null);
        }
    }

    private boolean isCustomFilterForPhoneNumbersOnly() {
        // TODO: this flag should not be stored in shared prefs.  It needs to be in the db.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getBoolean(ContactsPreferences.PREF_DISPLAY_ONLY_PHONES,
                ContactsPreferences.PREF_DISPLAY_ONLY_PHONES_DEFAULT);
    }
}
