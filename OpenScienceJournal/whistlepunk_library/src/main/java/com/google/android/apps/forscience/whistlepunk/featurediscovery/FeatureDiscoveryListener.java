package com.google.android.apps.forscience.whistlepunk.featurediscovery;

/**
 * Object receiving events from the feature discovery mechanism.
 */
public interface FeatureDiscoveryListener {

    /**
     * Called when user clicks on the action for a given feature.
     */
    public void onClick(String feature);
}
