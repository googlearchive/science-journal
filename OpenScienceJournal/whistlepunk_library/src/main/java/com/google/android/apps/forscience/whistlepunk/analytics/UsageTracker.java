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
