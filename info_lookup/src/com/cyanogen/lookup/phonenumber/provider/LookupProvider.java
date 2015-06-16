package com.cyanogen.lookup.phonenumber.provider;

import android.content.Context;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * Notion of a phone number lookup provider
 */
public class LookupProvider {

    private Context mContext;

    public LookupProvider(Context context) {
        mContext = context;
    }

    public void initialize() {
        /* NOT IMPLEMENTED */
    }

    public LookupResponse fetchInfo(LookupRequest request) {
        /* NOT IMPLEMENTED */
        return null;
    }

    public void disable() {
        /* NOT IMPLEMENTED */
    }

}