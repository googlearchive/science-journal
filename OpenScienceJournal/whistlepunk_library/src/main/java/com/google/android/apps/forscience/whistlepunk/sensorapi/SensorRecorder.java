package com.google.android.apps.forscience.whistlepunk.sensorapi;

/**
 * An object for controlling when this sensor starts and stops gathering data.
 *
 * Expects for start and stop to each be called once, in that order.
 */
public interface SensorRecorder extends OptionsListener {
    /**
     * Start observing the data from this sensor.  Optionally, the sensor _may_ begin to persist
     * data, if it is needed for real-time capture display.
     */
    void startObserving();


    /**
     * The user has requested that recording begin.
     * <p>
     * Subclasses _must_ record and persist data between calls to onStartRecording and
     * onStopRecording.  They also _may_ persist data between startObserving and stopObserving,
     * if it is needed for real-time capture display, but data recorded during observation should
     * be periodically pruned to save storage
     *
     * @param runId runId that will identify this run in the database. (For now, the id of the
     *              startLabel)
     */
    void startRecording(String runId);

    /**
     * The user has requested that recording stop.
     *
     * @see {@link #onStartRecording} for semantics
     */
    void stopRecording();

    // TODO: update spec to allow stopObserving to be called when recording should continue?
    // (context: before sensor-as-a-service, nothing could be recorded if it weren't also being
    // observed.  Now we can stop observing something, but still record it, but the
    // currently-implemented sensors still stop recording when stopObserving is called.  We should
    // probably either change some method names, or change the design).
    /**
     * Stop observing the data from this sensor.  No listeners should be updated any longer, and
     * no data should be persisted.
     */
    void stopObserving();

}
