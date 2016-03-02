/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.contacts.common.model;

import android.content.Context;
import android.text.TextUtils;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.contacts.common.util.PhoneNumberHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ADT used by {@link com.android.contacts.common.util.BlockContactHelper}
 */
public class BlockRequest {

    public static BlockRequest EMPTY = new BlockRequest();

    public List<String> phoneNumbers = Collections.EMPTY_LIST;

    public static BlockRequest createFrom(Context context, Contact contact) {
        ArrayList<String> numbersList = new ArrayList<String>();
        for (RawContact rawContact : contact.getRawContacts()) {
            List<DataItem> data = rawContact.getDataItems();
            for(DataItem item : data) {
                if (item instanceof PhoneDataItem) {
                    PhoneDataItem phoneDataItem = (PhoneDataItem) item;
                    String number = phoneDataItem.getNumber();
                    String formattedNumber = PhoneNumberHelper.formatPhoneNumber(context, number);
                    numbersList.add(formattedNumber);
                }
            }
        }

        if (numbersList.size() == 0) {
            return EMPTY;
        } else {
            BlockRequest blockRequest = new BlockRequest();
            blockRequest.phoneNumbers = numbersList;
            return blockRequest;
        }
    }

    public static BlockRequest createFrom(Context context, String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return EMPTY;
        }

        BlockRequest blockRequest = new BlockRequest();
        blockRequest.phoneNumbers = new ArrayList<String>();
        blockRequest.phoneNumbers.add(PhoneNumberHelper.formatPhoneNumber(context, phoneNumber));
        return blockRequest;
    }

}
