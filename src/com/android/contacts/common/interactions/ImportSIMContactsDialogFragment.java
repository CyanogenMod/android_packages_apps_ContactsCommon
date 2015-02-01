package com.android.contacts.common.interactions;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import com.android.contacts.common.R;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportSIMContactsDialogFragment extends DialogFragment {
    public static final String TAG = "ImportSIMContactsDialogFragment";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.import_contacts_sim)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ImportExportDialogFragment.show(getFragmentManager(),
                                false, ImportSIMContactsDialogFragment.class);
                    }})
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Nothing to do
                        dismiss();
                    }});
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
