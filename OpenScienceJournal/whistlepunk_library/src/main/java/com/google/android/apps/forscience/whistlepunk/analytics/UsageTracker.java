/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.analytics;

/**
 * Tracks usage for aggregate statistics.
 */
public interface UsageTracker {

    /**
     * If {@code true}, disables the tracker. If {@code false}, enables the tracker.
     */
    public void setOptOut(boolean optOut);

    /**
     * Tracks that the user has visited a certain screen.
     *
     * @param screenName Name of the screen viewed
     */
    public void trackScreenView(String screenName);

    /**
     * Tracks a user event.
     *
     * @param category Category of the event
     * @param action   Action tag of the event
     * @param label    Optional label
     * @param value    Optional value
     */
    public void trackEvent(String category, String action, String label, long value);
}
