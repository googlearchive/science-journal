package com.google.android.apps.forscience.whistlepunk.audiogen;

/**
 * An interface to generate audio from raw data.
 */
public interface AudioGenerator {

    /**
     * Start playing audio as long as data is available.
     */
    void startPlaying();

    /**
     * Stop playing audio.
     */
    void stopPlaying();

    /**
     * Reset the state of the AudioGenerator. Useful if a new
     * set of data will be coming soon.
     */
    void reset();

    /**
     * Add data at a particular timesetamp.
     * @param timestamp
     * @param data
     * @param min The minimum value shown on the graph.
     * @param max The maximum value shown on the graph.
     */
    void addData(long timestamp, double data, double min, double max);

    /**
     * Called when the AudioGenerator should be removed from memory.
     */
    void destroy();

    void setSonificationType(String sonificationType);
}
