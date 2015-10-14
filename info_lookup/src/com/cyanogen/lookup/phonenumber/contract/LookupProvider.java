package com.cyanogen.lookup.phonenumber.contract;

import com.cyanogen.lookup.phonenumber.request.LookupRequest;

/**
 * Notion of a phone number lookup provider
 */
public interface LookupProvider {

    /**
     * Explicit call to the provider to initialize itself. Decoupling it from provider construction
     * to enable explicit setup and tear down based on resource constraints.
     */
    boolean initialize();

    /**
     * Returns true if the provider is installed and enabled
     */
    boolean isEnabled();

    /**
     * Request to lookup contact info asynchronously. The callback is embedded
     * within {@link LookupRequest}
     */
    void fetchInfo(LookupRequest request);

    /**
     * Explicit call to disable provider and free resources
     */
    void disable();

    /**
     * Interface to provide ability to flag a phone number as spam
     *
     * @param phoneNumber {@link String}
     */
    void markAsSpam(String phoneNumber);

    /**
     * Check if the current provider supports spam reporting
     *
     * @return {@link Boolean} <code>true</code> if available, <code>false</code> if not
     */
    boolean hasSpamReporting();

    /**
     * Returns the name of the current provider
     *
     * @return {@link String}
     */
    String getDisplayName();

}

