/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import static org.junit.Assert.assertEquals;

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExplodingFactory;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.ApplicationLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.MonotonicClock;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        chartController.loadReadings(dc, 0, 50, false);
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
        chartController.loadRunData(erun, layout, dc, makeStatus(runId, layout),
                new TrialStats("foo"), null);

        // If loadRunData fails to clean out the pending loads (as was happening in a previous
        // version), then onGlobalXAxisChanged will _not_ trigger a second load.
        chartController.onGlobalXAxisChanged(1, 51, false, dc);
        assertEquals("[[0, 50], [1, 51]]", rc.loadCallbacks.toString());
    }

    @Test
    public void testGetClosestDataPoint() {
        ChartController chartController = makeChartController();
        addData(chartController, 0, 10, 2);
        assertEquals(chartController.getClosestDataPointToTimestamp(0).toString(),
                new ChartData.DataPoint(0, 0).toString());
        assertEquals(chartController.getClosestDataPointToTimestamp(4).toString(),
                new ChartData.DataPoint(4, 4).toString());
    }

    @Test
    public void testGetClosestDataPointAbove() {
        ChartController chartController = makeChartController();
        addData(chartController, 0, 10, 2);
        assertEquals(chartController.getClosestDataPointToTimestampAbove(2, 0).toString(),
                new ChartData.DataPoint(2, 2).toString());

        // Two is above or equal to 2.
        assertEquals(chartController.getClosestDataPointToTimestampAbove(2, 2).toString(),
                new ChartData.DataPoint(2, 2).toString());

        // The next point above 3 is 4.
        assertEquals(chartController.getClosestDataPointToTimestampAbove(2, 3).toString(),
                new ChartData.DataPoint(4, 4).toString());
    }

    @Test
    public void testGetClosestDataPointBelow() {
        ChartController chartController = makeChartController();
        addData(chartController, 0, 10, 2);

        // The next thing below 2 is 0.
        assertEquals(chartController.getClosestDataPointToTimestampBelow(2, 0).toString(),
                new ChartData.DataPoint(0, 0).toString());

        // The point at 2 is OK.
        assertEquals(chartController.getClosestDataPointToTimestampBelow(2, 2).toString(),
                new ChartData.DataPoint(2, 2).toString());

        // The point at 2 is safe.
        assertEquals(chartController.getClosestDataPointToTimestampBelow(2, 3).toString(),
                new ChartData.DataPoint(2, 2).toString());

        // We need a point smaller than 3, so it is 2.
        assertEquals(chartController.getClosestDataPointToTimestampBelow(4, 3).toString(),
                new ChartData.DataPoint(2, 2).toString());
    }

    private void addData(ChartController controller, long start, long end, long interval) {
        for (long i = start; i < end; i+= interval) {
            controller.addPoint(new ChartData.DataPoint(i, (double) i));
        }
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
            public String getSensorId() {
                return layout.sensorId;
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
        Experiment experiment = mmm.newExperiment();
        // Add the trial
        mmm.newTrial(experiment, runId, startTimestamp,
                new ArrayList<GoosciSensorLayout.SensorLayout>());
        List<ApplicationLabel> labels = Lists.<ApplicationLabel>newArrayList(
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "startLabelId",
                        "startLabelId", startTimestamp),
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "endLabelId",
                        "startLabelId", endTimestamp));
        return ExperimentRun.fromLabels(mmm.getTrial(runId, labels, Collections.<Label>emptyList()),
                experiment.getExperimentId(), Collections.<ApplicationLabel>emptyList());
    }

    private static class RecordingCallback implements ChartController.ChartDataLoadedCallback {
        public List<List<Long>> loadCallbacks = new ArrayList<>();

        @Override
        public void onChartDataLoaded(long firstTimestamp, long lastTimestamp) {
            loadCallbacks.add(Arrays.asList(firstTimestamp, lastTimestamp));
        }

        @Override
        public void onLoadAttemptStarted(boolean unused) {

        }
    }
}