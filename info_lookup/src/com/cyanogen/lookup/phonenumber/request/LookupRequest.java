package com.cyanogen.lookup.phonenumber.request;

import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * Encapsulates the notion of a phone number lookup request
 */
public class LookupRequest {
    public String mPhoneNumber;
    public Callback mCallback;

    public LookupRequest(String phoneNumber, Callback callback) {
        mPhoneNumber = phoneNumber;
        mCallback = callback;
    }

    public interface Callback {
        void onNewInfo(LookupRequest lookupRequest, LookupResponse response);
    }

}
