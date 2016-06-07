package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.content.Context;
import android.os.Bundle;

import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.PrefsNewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.StatsListener;

/**
 * A choice of data to capture, both for storage in a database and (optionally) real-time display
 */
public abstract class SensorChoice {

    /**
     * Unique ID for this source.  Used for database lookup and communication with the (upcoming)
     * sensor service
     */
    private final String mId;

    public SensorChoice(String id) {
        mId = id;
    }

    public String getId() {
        return mId;
    }

    /**
     * Populate {@code contentView} with a child view that represents the current state of this
     * sensor.
     *
     * @param dataViewOptions represents options that may influence the way the collected data is
     *                        being displayed, including theme colors and line-graph display
     *                        options.
     * @param statsListener the sensor should report changes to summary stats here.
     * @return A SensorPresenter that can respond to further UI events and update the capture
     *         display, or null if the sensor doesn't expect to respond to any events from outside
     *         the content view.
     */
    public abstract SensorPresenter createPresenter(DataViewOptions dataViewOptions,
            StatsListener statsListener,
            final ExternalAxisController.InteractionListener interactionListener);

    /**
     * Create a SensorRecorder for starting and stopping this sensor
     *
     * @param observer     if non-null, must be provided with updates to the observed sensor
     * @param listener     receives events related to successfully starting and stopping the sensor,
     *                     as well as related errors.
     * @param environment  provides system-level services (database, clock, connectivity)
     */
    public abstract SensorRecorder createRecorder(Context context, SensorObserver observer,
            SensorStatusListener listener, SensorEnvironment environment);

    /**
     * Get the storage strategy for default options that are set on this sensor
     *
     * By default, this can store a Map of Strings mapped to Strings, stored in
     * SharedPreferences.  If you need something more sophisticated or structured, please override
     * with a new OptionsStorage subclass.
     *
     * These are the _default_ options for any new card opened with this sensor selected.  The user
     * may also be given a UI that will override these defaults on the current card.
     *
     * TODO: actually use and apply these defaults, create a UI to change them.
     */
    public NewOptionsStorage getStorageForSensorDefaultOptions(Context context) {
        return new PrefsNewOptionsStorage("sensor_options_" + getId(), context);
    }
}
