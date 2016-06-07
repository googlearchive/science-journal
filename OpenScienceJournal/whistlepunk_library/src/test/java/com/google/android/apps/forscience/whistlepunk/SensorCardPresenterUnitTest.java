package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.BlankReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.OptionsListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SensorCardPresenterUnitTest {
    @Test public void rememberIdPassToStop() {
        DataViewOptions dataViewOptions = new DataViewOptions(Color.BLACK,
                new ScalarDisplayOptions());
        SensorSettingsController ssc = new SensorSettingsController() {
            @Override
            public void launchOptionsDialog(SensorChoice source, SensorPresenter presenter,
                    NewOptionsStorage storage, OptionsListener commitListener,
                    FailureListener failureListener) {

            }
        };
        MemoryRecorderController rc = new MemoryRecorderController();
        SensorCardPresenter scp = new SensorCardPresenter(dataViewOptions, ssc, rc,
                new GoosciSensorLayout.SensorLayout());
        SensorPresenter presenter = new MemorySensorPresenter();
        SensorObserver so = new SensorObserver() {
            @Override
            public void onNewData(long timestamp, Bundle data) {

            }
        };
        ManualSensor ds = new ManualSensor("sensorId", 100, 100);
        scp.startObserving(ds, presenter, new BlankReadableSensorOptions(), so);
        scp.setAppearanceProvider(new MemoryAppearanceProvider());
        scp.setUiForConnectingNewSensor(ds.getId(), "Display Name", "units", false);
        assertEquals(Arrays.asList(ds.getId()), rc.getCurrentObservedIds());
        scp.stopObserving();
        assertEquals(Arrays.asList(), rc.getCurrentObservedIds());
    }

    private static class MemorySensorPresenter implements SensorPresenter {
        @Override
        public void startShowing(ViewGroup contentView) {

        }

        @Override
        public void onRecordingStateChange(boolean isRecording, long recordingStart) {

        }

        @Override
        public void onLabelsChanged(List<Label> labels) {

        }

        @Override
        public void onXAxisChanged(long xMin, long xMax, boolean isPinnedToNow,
                DataController dataController) {

        }

        @Override
        public double getMinY() {
            return 0;
        }

        @Override
        public double getMaxY() {
            return 0;
        }

        @Override
        public void onPause() {

        }

        @Override
        public void onResume(long resetTime) {

        }

        @Override
        public void onStopObserving() {

        }

        @Override
        public OptionsPresenter getOptionsPresenter() {
            return null;
        }

        @Override
        public boolean hasOptionsPresenter() {
            return false;
        }

        @Override
        public void setAudioEnabled(boolean enableAudio) {

        }

        @Override
        public void setShowStatsOverlay(boolean showStatsOverlay) {

        }

        @Override
        public void updateStats(List<StreamStat> stats) {

        }

        @Override
        public void setYAxisRange(double minimumYAxisValue, double maximumYAxisValue) {

        }

        @Override
        public void resetYAxisRange() {

        }

        @Override
        public void onNewData(long timestamp, Bundle data) {

        }
    }

    private static class MemoryAppearanceProvider implements SensorAppearanceProvider {
        @Override
        public void loadAppearances(MaybeConsumer<Success> onSuccess) {

        }

        @Override
        public SensorAppearance getAppearance(String sensorId) {
            return new SensorAppearance(0, 0);
        }
    }
}