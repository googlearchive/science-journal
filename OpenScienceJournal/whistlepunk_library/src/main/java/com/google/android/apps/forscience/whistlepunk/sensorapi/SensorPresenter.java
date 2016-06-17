package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;

import java.util.List;

/**
 * After a view is created to show the current value of a sensor, the app may call back methods
 * on this SensorPresenter.  Implementations should respond to these events by updating the
 * capture view, if applicable.
 * <p/>
 * All calls are on the main thread.
 *
 * @param <BG_DATA> Parcelable type containing the sensor data.
 */
public interface SensorPresenter extends SensorObserver {
    /**
     * Sub-presenter based around allowing the user to change options, and applying the
     * view-specific options to the current data display
     */
    interface OptionsPresenter {
        /**
         * @param activeBundle can be queried for current values, and changed to make live
         *                     changes to presentation.
         * @param context
         * @return a View for seeing the current options and making changes.  Caller
         *         is responsible for laying out and displaying this View
         */
        View buildOptionsView(ActiveBundle activeBundle, Context context);

        /**
         * Apply the given options to the presentation of the sensor data.  This should only
         * attempt to change the data _display_, not the underlying collection or data storage.
         * @param bundle
         */
        void applyOptions(ReadableSensorOptions bundle);
    }

    /**
     * The view is ready for the data to be displayed.
     *
     * @param view The view onto which to show the data.
     */
    void startShowing(ViewGroup contentView);

    /**
     * The SensorPresenter may update its UI to show whether it is currently
     * recording.
     *
     * @param isRecording Whether recording is currently in progress.
     * @param recordingStart The time at which recording last started.
     */
    void onRecordingStateChange(boolean isRecording, long recordingStart);

    /**
     * The list of labels has changed.
     *
     * @param labels The list of labels.
     */
    void onLabelsChanged(List<Label> labels);

    /**
     * The x axis range, min or max has changed.
     * Includes whether the user is pinned to "now" or not, since that may impact the graph UI
     * and interactions.
     * @param xMin The minimum X axis value.
     * @param xMax The maximum X axis value.
     * @param isPinnedToNow Whether the X axis is currently "pinned" to now.
     * @param dataController
     */
    void onXAxisChanged(long xMin, long xMax, boolean isPinnedToNow, DataController dataController);

    /**
     * Gets the minimum Y axis value currently displayed for this sensor.
     */
    double getMinY();

    /**
     * Gets the maximum Y axis value currently displayed for this sensor.
     */
    double getMaxY();

    void onPause();

    void onResume(long resetTime);

    /**
     * The view is about to be removed.  Clean up any unneeded resources.
     */
    void onStopObserving();

    OptionsPresenter getOptionsPresenter();

    boolean hasOptionsPresenter();

    void setAudioEnabled(boolean enableAudio);

    void setShowStatsOverlay(boolean showStatsOverlay);

    void updateStats(List<StreamStat> stats);

    void setYAxisRange(double minimumYAxisValue, double maximumYAxisValue);

    void resetView();
}
