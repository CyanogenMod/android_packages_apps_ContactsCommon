package com.cyanogen.lookup.phonenumber.contract;

import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

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
     * Request the Provider for contact info. This call will block the current thread till
     * the request completes.
     */
    LookupResponse blockingFetchInfo(LookupRequest lookupRequest);

    /**
     * Explicit call to disable provider and free resources
     */
    void disable();

    /**
     * flag a phone number as spam
     *
     * @param phoneNumber {@link String}
     */
    void markAsSpam(String phoneNumber);

    /**
     * un-flag a phone number as spam
     *
     * @param phoneNumber {@link String}
     */
    void unmarkAsSpam(String phoneNumber);

    /**
     * Check if the current provider supports spam reporting
     *
     * @return {@link Boolean} <code>true</code> if available, <code>false</code> if not
     */
    boolean supportsSpamReporting();

    /**
     * Returns the name of the current provider
     *
     * @return {@link String}
     */
    String getDisplayName();

    /**
     * Unique identifier for this provider
     *
     * The identifier could be the package name or a service name for the purpose of uniquely
     * identifying this provider.
     */
    String getUniqueIdentifier();

}

