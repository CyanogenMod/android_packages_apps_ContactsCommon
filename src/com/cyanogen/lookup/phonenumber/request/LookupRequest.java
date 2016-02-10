package com.cyanogen.lookup.phonenumber.request;

import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * Encapsulates the notion of a phone number lookup request
 */
public class LookupRequest {
    /**
     * phone number must be in an E164 format
     */
    public String mPhoneNumber;
    public Callback mCallback;

    public LookupRequest(String phoneNumber, Callback callback) {
        mPhoneNumber = phoneNumber;
        mCallback = callback;
    }

    @Override
    public int hashCode() {
        return mPhoneNumber.hashCode();
    }

    public interface Callback {
        void onNewInfo(LookupRequest lookupRequest, LookupResponse response);
    }
}
