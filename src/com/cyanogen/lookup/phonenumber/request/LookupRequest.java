package com.cyanogen.lookup.phonenumber.request;

import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * Encapsulates the notion of a phone number lookup request
 */
public class LookupRequest {
    /**
     * Used to identify the reason behind the request
     */
    public enum RequestOrigin {
        UNSPECIFIED,
        INCOMING_CALL,
        OUTGOING_CALL,
        INCOMING_SMS,
        OUTGOING_SMS,
        OTHER
    }

    /**
     * phone number must be in an E164 format
     */
    public String mPhoneNumber;
    public Callback mCallback;
    public RequestOrigin mRequestOrigin;

    public LookupRequest(String phoneNumber, Callback callback) {
        this(phoneNumber, callback, RequestOrigin.UNSPECIFIED);
    }

    public LookupRequest(String phoneNumber, Callback callback, RequestOrigin origin) {
        mPhoneNumber = phoneNumber;
        mCallback = callback;
        mRequestOrigin = origin;
    }

    public void setRequestOrigin(RequestOrigin type) {
        mRequestOrigin = type;
    }

    @Override
    public int hashCode() {
        return mPhoneNumber.hashCode();
    }

    public interface Callback {
        void onNewInfo(LookupRequest lookupRequest, LookupResponse response);
    }
}
