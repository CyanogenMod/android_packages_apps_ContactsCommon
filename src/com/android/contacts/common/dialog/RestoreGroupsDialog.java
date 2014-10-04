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
 * limitations under the License
 */

package com.android.contacts.common.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.LocalGroups;

import com.android.contacts.common.R;

public class RestoreGroupsDialog extends DialogFragment {

    public static interface OnRestoreCompleteListener {
        void onRestoreComplete();
    }

    public static void showDialogForLocalGroups(FragmentManager fragmentManager,
            OnRestoreCompleteListener callback) {
        RestoreGroupsDialog dialog = new RestoreGroupsDialog(callback);
        dialog.show(fragmentManager, "restoreLocalGroups");
    }

    private final boolean mIsLocalGroups;
    private final OnRestoreCompleteListener mCallback;

    public RestoreGroupsDialog(OnRestoreCompleteListener callback) {
        super();
        // We only have local groups logic for now
        mIsLocalGroups = true;
        mCallback = callback;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final OnClickListener okListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final IndeterminateProgressDialog progressDialog = IndeterminateProgressDialog.show(
                        getFragmentManager(), getString(R.string.restoreGroupsProgress_title),
                        null, 500);
                final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        if (mIsLocalGroups) {
                            LocalGroups.restoreDefaultLocalGroups(getActivity());
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        progressDialog.dismiss();
                        if (mCallback != null) {
                            mCallback.onRestoreComplete();
                        }
                    }
                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        };
        return new AlertDialog.Builder(getActivity())
            .setTitle(R.string.restoreGroupsConfirmation_title)
            .setMessage(R.string.restoreGroupsConfirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, okListener)
            .setCancelable(true)
            .create();
    }
}
