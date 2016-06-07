package com.google.android.apps.forscience.whistlepunk.wireapi;

interface ISensorStatusListener {
    /**
     * Called when the status of the source changes.
     *
     * @param id ID of the source.
     * @param status one of the {@link Status} values.
     */
    void onSourceStatus(String id, int status) = 0;

    /**
     * Called if there was an error in the source.
     *
     * @param id ID of the source.
     * @param error one of the {@link Error} values.
     * @param errorMessage human readable error message which will be displayed to the user
     */
    void onSourceError(String id, int error, String errorMessage) = 1;
}
