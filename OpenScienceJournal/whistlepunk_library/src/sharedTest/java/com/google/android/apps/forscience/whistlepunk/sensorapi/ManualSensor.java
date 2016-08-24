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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.MemorySensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.RecordingStatusListener;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.StatsListener;
import com.google.android.apps.forscience.whistlepunk.scalarchart.UptimeClock;
import com.google.android.apps.forscience.whistlepunk.sensordb.MonotonicClock;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;

public class ManualSensor extends ScalarSensor {
    private StreamConsumer mConsumer;
    private ChartController mChartController;

    private MemorySensorHistoryStorage mStorage = new MemorySensorHistoryStorage();
    private int mThrowawayThreshold = 1;

    public ManualSensor(String sensorId, long defaultGraphRange,
            int zoomLevelBetweenResolutionTiers) {
        super(sensorId, defaultGraphRange, MoreExecutors.directExecutor(),
                zoomLevelBetweenResolutionTiers, new UptimeClock());
    }

    public SensorRecorder createRecorder(Context context, RecordingDataController rdc,
            RecordingSensorObserver observer) {
        return createRecorder(context, observer, new StubStatusListener(),
                makeSensorEnvironment(context, rdc));
    }

    @Override
    protected SensorRecorder makeScalarControl(final StreamConsumer c, SensorEnvironment environment,
            Context context, SensorStatusListener listener) {
        return new SensorRecorder() {
            @Override
            public void startObserving() {
                mConsumer = c;
            }

            @Override
            public void startRecording(String runId) {
                startObserving();
            }

            @Override
            public void stopRecording() {

            }

            @Override
            public void stopObserving() {
                mConsumer = null;
            }

            @Override
            public boolean hasRecordedData() {
                return true;
            }

            @Override
            public void applyOptions(ReadableSensorOptions settings) {

            }
        };
    }

    @NonNull
    @Override
    protected ChartController createChartController(DataViewOptions dataViewOptions,
            ExternalAxisController.InteractionListener interactionListener, String sensorId,
            long defaultGraphRange) {
        mChartController = new ChartController(
                ChartOptions.ChartPlacementType.TYPE_OBSERVE,
                dataViewOptions.getLineGraphOptions(), mThrowawayThreshold,
                /* no data loading buffer */ 0, new MonotonicClock());
        mChartController.setInteractionListener(interactionListener);
        mChartController.setSensorId(sensorId);
        mChartController.setDefaultGraphRange(defaultGraphRange);
        return mChartController;
    }

    public List<ChartData.DataPoint> getRawData() {
        return mChartController.getData();
    }

    public List<ChartData.DataPoint> getLineData() {
        return mChartController.getData();
    }

    public void pushValue(long timestampMillis, double value) {
        if (mConsumer != null) {
            mConsumer.addData(timestampMillis, value);
        }
    }

    private SensorPresenter createPresenter() {
        StatsListener statsListener = new StubStatsListener();
        ExternalAxisController.InteractionListener interactionListener =
                new StubInteractionListener();
        return createPresenter(new DataViewOptions(0, new ScalarDisplayOptions()), statsListener,
                interactionListener);
    }

    @NonNull
    public SensorPresenter createRecordingPresenter(Context context, RecordingDataController rc,
            String runId) {
        SensorPresenter presenter = createPresenter();
        createRecorder(context, presenter, new RecordingStatusListener(),
                makeSensorEnvironment(context, rc)).startRecording(runId);
        presenter.onRecordingStateChange(true, 0);
        return presenter;
    }

    @NonNull
    private MemorySensorEnvironment makeSensorEnvironment(Context context,
            RecordingDataController rc) {
        return new MemorySensorEnvironment(rc, new FakeBleClient(context), mStorage);
    }

    @NonNull
    public SensorPresenter createRecordingPresenter(Context context, RecordingDataController rc,
            String runId, int graphDataThrowawaySizeThreshold) {
        mThrowawayThreshold = graphDataThrowawaySizeThreshold;
        return createRecordingPresenter(context, rc, runId);
    }

    public ChartController getChartController() {
        return mChartController;
    }

    public void pushDataPoints(SensorRecorder recorder, int howMany) {
        recorder.startRecording("runId");
        createPresenter().onRecordingStateChange(true, 0);

        // tier threshhold 10 and 100 data points means:
        // tier 0: 100 points
        // tier 1: 20 points
        // tier 2: 4 points
        for (int i = 0; i < howMany; i++) {
            pushValue(i, i);
        }

        recorder.stopRecording();
    }
}
