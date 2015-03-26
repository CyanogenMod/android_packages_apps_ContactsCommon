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
 * limitations under the License.
 */

package com.android.contacts.common.model.dataitem;

import android.content.ContentValues;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;

import com.android.contacts.common.GroupMetaData;

/**
 * Represents a group memebership data item, wrapping the columns in
 * {@link ContactsContract.CommonDataKinds.GroupMembership}.
 */
public class GroupMembershipDataItem extends DataItem {
    public static final String GROUP_TITLE = "group_title";
    private static final String GROUP_IS_FAVORITES = "group_is_favorites";
    private static final String GROUP_IS_DEFAULT = "group_is_default";

    /* package */ GroupMembershipDataItem(ContentValues values) {
        super(values);
    }

    public Long getGroupRowId() {
        return getContentValues().getAsLong(GroupMembership.GROUP_ROW_ID);
    }

    public String getGroupSourceId() {
        return getContentValues().getAsString(GroupMembership.GROUP_SOURCE_ID);
    }

    public void setGroupMetaData(GroupMetaData metaData) {
        final ContentValues values = getContentValues();
        values.put(GROUP_TITLE, metaData.getTitle());
        values.put(GROUP_IS_FAVORITES, metaData.isFavorites());
        values.put(GROUP_IS_DEFAULT, metaData.isDefaultGroup());
    }

    public String getGroupTitle() {
        return getContentValues().getAsString(GROUP_TITLE);
    }

    public boolean isFavoritesGroup() {
        return getContentValues().getAsBoolean(GROUP_IS_FAVORITES);
    }

    public boolean isDefaultGroup() {
        return getContentValues().getAsBoolean(GROUP_IS_DEFAULT);
    }
}
