package com.google.android.apps.forscience.whistlepunk.scalarchart;

import static org.junit.Assert.assertEquals;

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExplodingFactory;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.MonotonicClock;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChartControllerTest {
    @Test
    public void dontLeaveStaleLoadIds() {
        ChartController chartController = makeChartController();
        RecordingCallback rc = new RecordingCallback();
        chartController.addChartDataLoadedCallback(rc);
        chartController.setSensorId("foo");
        DataController dc = new InMemorySensorDatabase().makeSimpleController(
                new MemoryMetadataManager());
        chartController.loadReadings(dc, 0, 50);
        // If loadReadings fails to clean out the pending loads (as was happening in a previous
        // version), then onGlobalXAxisChanged will _not_ trigger a second load.
        chartController.onGlobalXAxisChanged(1, 51, false, dc);
        assertEquals("[[0, 50], [1, 51]]", rc.loadCallbacks.toString());
    }

    @Test
    public void dontLeaveStaleLoadIdsWhenLoadingRunData() {
        ChartController chartController = makeChartController();
        RecordingCallback rc = new RecordingCallback();
        chartController.addChartDataLoadedCallback(rc);
        chartController.setSensorId("foo");
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        DataController dc = new InMemorySensorDatabase().makeSimpleController(mmm);
        final String runId = "runId";
        ExperimentRun erun = experimentRunBetween(mmm, 0, 50, runId);
        final GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = "foo";
        chartController.loadRunData(erun, layout, dc, makeStatus(runId, layout), new RunStats(),
                rc);

        // If loadRunData fails to clean out the pending loads (as was happening in a previous
        // version), then onGlobalXAxisChanged will _not_ trigger a second load.
        chartController.onGlobalXAxisChanged(1, 51, false, dc);
        assertEquals("[[0, 50], [1, 51]]", rc.loadCallbacks.toString());
    }

    @NonNull
    private ChartController.ChartLoadingStatus makeStatus(final String runId,
            final GoosciSensorLayout.SensorLayout layout) {
        return new ChartController.ChartLoadingStatus() {
            @Override
            public int getGraphLoadStatus() {
                return 0;
            }

            @Override
            public void setGraphLoadStatus(int graphLoadStatus) {

            }

            @Override
            public String getRunId() {
                return runId;
            }

            @Override
            public GoosciSensorLayout.SensorLayout getSensorLayout() {
                return layout;
            }
        };
    }

    @NonNull
    private ChartController makeChartController() {
        return new ChartController(
                ChartOptions.ChartPlacementType.TYPE_OBSERVE, new ScalarDisplayOptions(), 100, 100L,
                new MonotonicClock(), new ExplodingFactory().makeListenerForOperation("load"));
    }

    private ExperimentRun experimentRunBetween(MemoryMetadataManager mmm, int startTimestamp,
            int endTimestamp, String runId) {
        return ExperimentRun.fromLabels(
                mmm.newRun(mmm.newExperiment(mmm.newProject()), runId,
                        new ArrayList<GoosciSensorLayout.SensorLayout>()),
                Lists.<Label>newArrayList(
                        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "startLabelId",
                                "startLabelId", startTimestamp),
                        new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "endLabelId",
                                "startLabelId", endTimestamp)));
    }

    private static class RecordingCallback implements ChartController.ChartDataLoadedCallback {
        public List<List<Long>> loadCallbacks = new ArrayList<>();

        @Override
        public void onChartDataLoaded(long firstTimestamp, long lastTimestamp) {
            loadCallbacks.add(Arrays.asList(firstTimestamp, lastTimestamp));
        }

        @Override
        public void onLoadAttemptStarted() {

        }
    }
}