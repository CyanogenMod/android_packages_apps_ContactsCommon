/*
 * Copyright (C) 2015, The Linux Foundation. All Rights Reserved.

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

import android.telephony.SubscriptionManager;
import com.android.internal.telephony.PhoneConstants;

public interface SimContactsConstants {

    public static final String SIM_NAME_1 = "SIM1";
    public static final String SIM_NAME_2 = "SIM2";
    public static final String SIM_NAME = "SIM";
    public static final String PHONE_NAME = "PHONE";
    public static final String ACCOUNT_TYPE_SIM = "com.android.sim";
    public static final String ACCOUNT_TYPE_PHONE = "com.android.localphone";
    public static final String ACCOUNT_TYPE = "account_type";
    public static final String ACCOUNT_NAME = "account_name";
    public static final String ACCOUNT_DATA = "data_set";
    public static final String STR_TAG = "tag";
    public static final String STR_NUMBER = "number";
    public static final String STR_EMAILS = "emails";
    public static final String STR_ANRS = "anrs";
    public static final String STR_NEW_TAG = "newTag";
    public static final String STR_NEW_NUMBER = "newNumber";
    public static final String STR_NEW_EMAILS = "newEmails";
    public static final String STR_NEW_ANRS = "newAnrs";
    public static final String INTENT_EXPORT_COMPLETE =
        "com.android.sim.INTENT_EXPORT_COMPLETE";
    public static final String ANR_SEP = ":";
    public static final String EMAIL_SEP = ",";
    public static final String SIM_URI = "content://icc/adn";
    public static final String SIM_SUB_URI = "content://icc/adn/subId/";
    public static final String WITHOUT_SIM_FLAG ="no_sim";
    public static final String IS_CONTACT = "is_contact";
    public static final String RESULT_KEY = "result";
    public static final String ACTION_MULTI_PICK =
            "com.android.contacts.action.MULTI_PICK";
    public static final String ACTION_MULTI_PICK_EMAIL =
            "com.android.contacts.action.MULTI_PICK_EMAIL";
    public static final String ACTION_MULTI_PICK_CALL =
            "com.android.contacts.action.MULTI_PICK_CALL";
    public static final String ACTION_MULTI_PICK_SIM =
            "com.android.contacts.action.MULTI_PICK_SIM";
    public static final String SUB = PhoneConstants.SLOT_KEY;

    public static final int SUB_1 = PhoneConstants.SUB1;
    public static final int SUB_2 = PhoneConstants.SUB2;
    public static final int SUB_INVALID = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
}