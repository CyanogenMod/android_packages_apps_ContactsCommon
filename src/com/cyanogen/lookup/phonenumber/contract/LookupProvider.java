/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.cyanogen.lookup.phonenumber.contract;

import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * Notion of a phone number lookup provider
 */
public interface LookupProvider {

    /**
     * Callback for clients that are interested in changes to the Provider's status
     */
    interface StatusCallback {
        void onStatusChanged(boolean isEnabled);
    }

    /**
     * Register a callback to be notified when the provider's status changes
     *
     * @param callback listener to be called if provider is changed
     */
    void registerStatusCallback(StatusCallback callback);

    /**
     * Unregister a previously registered callback
     *
     * @param callback listener that was previously registered
     */
    void unregisterStatusCallback(StatusCallback callback);

    /**
     * Returns true if the provider is installed and enabled.
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

