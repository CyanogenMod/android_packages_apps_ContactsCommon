package com.cyanogen.lookup.phonenumber.response;

import android.graphics.drawable.Drawable;

/**
 * ADT to store the result of a phone number lookup
 */
public class LookupResponse {
    public String mName;
    public String mNumber;
    public String mCity;
    public String mCountry;
    public String mAddress;
    public String mPhotoUrl;
    public int mSpamCount;

    public Drawable mAttributionLogo;
}