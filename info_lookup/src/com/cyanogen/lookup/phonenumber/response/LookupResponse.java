package com.cyanogen.lookup.phonenumber.response;

import android.graphics.drawable.Drawable;

/**
 * ADT to store the result of a phone number lookup
 */
public class LookupResponse {
    public String mProviderName;
    public String mName;
    public String mNumber;
    public String mCity;
    public String mCountry;
    public String mAddress;
    public String mPhotoUrl;
    public int mSpamCount;

    public Drawable mAttributionLogo;

    @Override
    public String toString() {
        return String.format("{ providerName = %s, name = %s, number = %s, city = %s, country = %s, address = %s, photo-url : %s, spam-count = %d}",
                mProviderName != null ? mProviderName : "null" ,
                mName != null ? mName : "null" ,
                mNumber != null ? mNumber : "null" ,
                mCity != null ? mCity : "null" ,
                mCountry != null ? mCountry : "null" ,
                mAddress != null ? mAddress : "null" ,
                mPhotoUrl != null ? mPhotoUrl : "null" ,
                mSpamCount );
    }
}