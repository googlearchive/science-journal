package com.google.android.apps.forscience.whistlepunk;

public interface Clock {
    /**
     * @return timestamp in millis (see usage site for what the timestamp is in reference to.)
     */
    long getNow();
}
