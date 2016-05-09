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

package com.android.contacts.common.activity;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;

import com.android.contacts.common.activity.fragment.BlockContactDialogFragment;
import com.android.contacts.common.util.BlockContactHelper;
import com.cyanogen.lookup.phonenumber.provider.LookupProviderImpl;

/**
 * Wrapper activity to display a block contact dialog
 */
public class BlockContactActivity extends Activity implements BlockContactDialogFragment.Callbacks {
    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";

    private BlockContactHelper mBlockContactHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        String phoneNumber = getIntent().getStringExtra(EXTRA_PHONE_NUMBER);
        if (TextUtils.isEmpty(phoneNumber)) {
            finish();
            return;
        }

        mBlockContactHelper = new BlockContactHelper(this);
        mBlockContactHelper.setContactInfo(phoneNumber);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DialogFragment f = mBlockContactHelper.getBlockContactDialog(
                mBlockContactHelper.isContactBlacklisted() ?
                        BlockContactHelper.BlockOperation.UNBLOCK :
                        BlockContactHelper.BlockOperation.BLOCK
        );
        f.show(getFragmentManager(), "block_contact");
    }

    @Override
    public void onBlockSelected(boolean notifyLookupProvider) {
        mBlockContactHelper.blockContactAsync(notifyLookupProvider);
    }

    @Override
    public void onUnblockSelected(boolean notifyLookupProvider) {
        mBlockContactHelper.unblockContactAsync(notifyLookupProvider);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBlockContactHelper.destroy();
    }
}
