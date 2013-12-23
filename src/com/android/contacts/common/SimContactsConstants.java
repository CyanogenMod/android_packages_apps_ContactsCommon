/*
 * Copyright (C) 2013, The Linux Foundation. All Rights Reserved.

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

import com.android.internal.telephony.MSimConstants;

public interface SimContactsConstants {

    public static final String SIM_NAME_1 = "SIM1";
    public static final String SIM_NAME_2 = "SIM2";
    public static final String SIM_NAME = "SIM";
    public static final String PHONE_NAME = "PHONE";
    public static final String PASSWORD = "";
    public static final String ACCOUNT_TYPE_SIM = "com.android.sim";
    public static final String ACCOUNT_TYPE_PHONE = "com.android.localphone";
    public static final String SUB = MSimConstants.SUBSCRIPTION_KEY;
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
    public static final String STR_PIN2 = "pin2";
    public static final String USIM = "USIM";
    public static final String CSIM = "CSIM";
    public static final String SIM  = "SIM";
    public static final String RUIM = "RUIM";
    public static final String INTENT_EXPORT_COMPLETE =
        "com.android.sim.INTENT_EXPORT_COMPLETE";
    public static final int SUB_1 = MSimConstants.SUB1;
    public static final int SUB_2 = MSimConstants.SUB2;
    public static final int SUB_INVALID = -1;
    public static final int SIM_STATE_READY = 1;
    public static final int SIM_STATE_NOT_READY = 2;


}


