package com.cyanogen.lookup.phonenumber.response;

/**
 * Status code indicating the result of a lookup query
 */
public enum StatusCode {

    /**
     * Default value
     */
    NULL ("null - default value"),

    /**
     * Error state wherein the lookup provider isn't configured properly
     */
    CONFIG_ERROR ("configuration error"),

    /**
     * Lookup request failed, maybe due to a network error
     */
    FAIL ("request failed"),

    /**
     * Lookup provider didn't find any results
     */
    NO_RESULT ("no results found"),

    /**
     * Lookup request succeeded
     */
    SUCCESS ("request succeeded");

    private final String mDescription;

    StatusCode(String description) {
        mDescription = description;
    }

    @Override
    public String toString() {
        return mDescription;
    }
}
