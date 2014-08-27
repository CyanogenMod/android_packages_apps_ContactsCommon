/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a list of all available accounts, letting the user select under which account to view
 * contacts.
 */
public class AccountFilterActivity extends Activity implements AdapterView.OnItemClickListener {

    private static final String TAG = AccountFilterActivity.class.getSimpleName();

    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 0;

    public static final String KEY_EXTRA_CONTACT_LIST_FILTER = "contactListFilter";
    public static final String KEY_EXTRA_CURRENT_FILTER = "currentFilter";

    private static final int FILTER_LOADER_ID = 0;

    private ListView mListView;

    private ContactListFilter mCurrentFilter;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.contact_list_filter);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mCurrentFilter = getIntent().getParcelableExtra(KEY_EXTRA_CURRENT_FILTER);

        getLoaderManager().initLoader(FILTER_LOADER_ID, null, new MyLoaderCallbacks());
    }

    private static class FilterLoader extends AsyncTaskLoader<List<ContactListFilter>> {
        private Context mContext;

        public FilterLoader(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public List<ContactListFilter> loadInBackground() {
            return loadAccountFilters(mContext);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
        }

        @Override
        protected void onReset() {
            onStopLoading();
        }
    }

    private static List<ContactListFilter> loadAccountFilters(Context context) {
        final ArrayList<ContactListFilter> result = Lists.newArrayList();
        final ArrayList<ContactListFilter> accountFilters = Lists.newArrayList();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(context);
        List<AccountWithDataSet> accounts = accountTypes.getAccounts(false);
        for (AccountWithDataSet account : accounts) {
            AccountType accountType = accountTypes.getAccountType(account.type, account.dataSet);
            if (accountType.isExtension() && !account.hasData(context)) {
                // Hide extensions with no raw_contacts.
                continue;
            }
            Drawable icon = accountType != null ? accountType.getDisplayIcon(context) : null;
            accountFilters.add(ContactListFilter.createAccountFilter(
                    account.type, account.name, account.dataSet, icon));
        }

        // Always show "All", even when there's no accounts.  (We may have local contacts)
        result.add(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));

        final int count = accountFilters.size();
        if (count >= 1) {
            // If we only have one account, don't show it as "account", instead show it as "all"
            if (count > 1) {
                result.addAll(accountFilters);
            }
            result.add(ContactListFilter.createFilterWithType(
                    ContactListFilter.FILTER_TYPE_CUSTOM));
        }
        return result;
    }

    private class MyLoaderCallbacks implements LoaderCallbacks<List<ContactListFilter>> {
        @Override
        public Loader<List<ContactListFilter>> onCreateLoader(int id, Bundle args) {
            return new FilterLoader(AccountFilterActivity.this);
        }

        @Override
        public void onLoadFinished(
                Loader<List<ContactListFilter>> loader, List<ContactListFilter> data) {
            if (data == null) { // Just in case...
                Log.e(TAG, "Failed to load filters");
                return;
            }
            mListView.setAdapter(
                    new FilterListAdapter(AccountFilterActivity.this, data, mCurrentFilter));
        }

        @Override
        public void onLoaderReset(Loader<List<ContactListFilter>> loader) {
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final ContactListFilter filter = (ContactListFilter) view.getTag();
        if (filter == null) return; // Just in case
        if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            final Intent intent = new Intent(this,
                    CustomContactListFilterActivity.class);
            startActivityForResult(intent, SUBACTIVITY_CUSTOMIZE_FILTER);
        } else {
            updateAccountVisibleByContactListFilter(filter);
            final Intent intent = new Intent();
            intent.putExtra(KEY_EXTRA_CONTACT_LIST_FILTER, filter);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    private void updateAccountVisibleByContactListFilter(final ContactListFilter filter) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver resolver = getContentResolver();
                if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                    updateAccountVisible(resolver, filter.accountName, filter.accountType,
                            filter.dataSet);
                } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
                    final ContentValues values = new ContentValues();
                    values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1);
                    try {
                        resolver.update(ContactsContract.Settings.CONTENT_URI, values, null, null);
                    } catch (Exception e) {
                        Log.e(TAG, "e=" + e.toString());
                    }
                }
            }
        }).start();

    }

    /**
     * Updating the account is visible When an account exists.
     *
     * @param resolver
     * @param accountName
     * @param accountType
     * @param accountDataSet
     */
    private static void updateAccountVisible(ContentResolver resolver, String accountName,
            String accountType, String accountDataSet) {
        final Uri settingsUri = ContactsContract.Settings.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.Settings.ACCOUNT_NAME, accountName)
                .appendQueryParameter(ContactsContract.Settings.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(ContactsContract.Settings.DATA_SET, accountDataSet).build();
        final Cursor cursor = resolver.query(settingsUri, new String[] {
                ContactsContract.Settings.SHOULD_SYNC, ContactsContract.Settings.UNGROUPED_VISIBLE
        }, null, null, null);

        if (null != cursor) {
            final ContentValues values = new ContentValues();
            final ArrayList<ContentProviderOperation> operationList
                    = new ArrayList<ContentProviderOperation>();

            if (0 == cursor.getCount()) {
                // this account has values in setting table.
                values.put(ContactsContract.Settings.ACCOUNT_NAME, accountName);
                values.put(ContactsContract.Settings.ACCOUNT_TYPE, accountType);
                values.put(ContactsContract.Settings.DATA_SET, accountDataSet);
                values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1);
                final ContentProviderOperation.Builder builder = ContentProviderOperation
                        .newInsert(ContactsContract.Settings.CONTENT_URI);
                builder.withValues(values);
                operationList.add(builder.build());
            } else {
                // this account has values in setting table.
                values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1);
                final ContentProviderOperation.Builder builder = ContentProviderOperation
                        .newUpdate(ContactsContract.Settings.CONTENT_URI);
                builder.withValues(values);
                builder.withSelection(ContactsContract.Settings.ACCOUNT_NAME + " =? AND "
                        + ContactsContract.Settings.ACCOUNT_TYPE + " =? AND "
                        + ContactsContract.Settings.DATA_SET + " =?", new String[] {
                        accountName, accountName, accountDataSet
                });
                operationList.add(builder.build());
            }

            // update other account's visible to false
            final ContentValues otherValues = new ContentValues();
            otherValues.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 0);
            final ContentProviderOperation.Builder otherBuilder = ContentProviderOperation
                    .newUpdate(ContactsContract.Settings.CONTENT_URI);
            otherBuilder.withValues(otherValues);

            String selection = ContactsContract.Settings.ACCOUNT_NAME
                    + (accountName == null ? " IS NULL" : " <>?") + " AND "
                    + ContactsContract.Settings.ACCOUNT_TYPE
                    + (accountType == null ? " IS NULL" : " <>?") + " AND "
                    + ContactsContract.Settings.DATA_SET
                    + (accountDataSet == null ? " IS NULL" : " <>?");

            ArrayList<String> args = new ArrayList<String>(3);
            if (accountName != null)
                args.add(accountName);
            if (accountType != null)
                args.add(accountType);
            if (accountDataSet != null)
                args.add(accountDataSet);
            String[] selectionArgs = (String[]) args.toArray(new String[args.size()]);

            otherBuilder.withSelection(selection, selectionArgs);
            operationList.add(otherBuilder.build());

            try {
                resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
            } catch (Exception e) {
                Log.e(TAG, "e=" + e.toString());
            }
            cursor.close();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case SUBACTIVITY_CUSTOMIZE_FILTER: {
                final Intent intent = new Intent();
                ContactListFilter filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_CUSTOM);
                intent.putExtra(KEY_EXTRA_CONTACT_LIST_FILTER, filter);
                setResult(Activity.RESULT_OK, intent);
                finish();
                break;
            }
        }
    }

    private static class FilterListAdapter extends BaseAdapter {
        private final List<ContactListFilter> mFilters;
        private final LayoutInflater mLayoutInflater;
        private final AccountTypeManager mAccountTypes;
        private final ContactListFilter mCurrentFilter;

        public FilterListAdapter(
                Context context, List<ContactListFilter> filters, ContactListFilter current) {
            mLayoutInflater = (LayoutInflater) context.getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            mFilters = filters;
            mCurrentFilter = current;
            mAccountTypes = AccountTypeManager.getInstance(context);
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public ContactListFilter getItem(int position) {
            return mFilters.get(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final ContactListFilterView view;
            if (convertView != null) {
                view = (ContactListFilterView) convertView;
            } else {
                view = (ContactListFilterView) mLayoutInflater.inflate(
                        R.layout.contact_list_filter_item, parent, false);
            }
            view.setSingleAccount(mFilters.size() == 1);
            final ContactListFilter filter = mFilters.get(position);
            view.setContactListFilter(filter);
            view.bindView(mAccountTypes);
            view.setTag(filter);
            view.setActivated(filter.equals(mCurrentFilter));
            return view;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // We have two logical "up" Activities: People and Phone.
                // Instead of having one static "up" direction, behave like back as an
                // exceptional case.
                onBackPressed();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
