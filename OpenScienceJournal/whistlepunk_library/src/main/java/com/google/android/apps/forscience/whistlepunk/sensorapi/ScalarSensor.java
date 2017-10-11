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

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsListener;
import com.google.android.apps.forscience.whistlepunk.audiogen.AudioGenerator;
import com.google.android.apps.forscience.whistlepunk.audiogen.SimpleJsynAudioGenerator;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.UptimeClock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FrequencyOptionsPresenter
        .FilterChangeListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A SensorChoice that records a scalar value to the database and shows it onscreen as a linegraph.
 */
public abstract class ScalarSensor extends SensorChoice implements FilterChangeListener {
    /**
     * The default zoom level available between sensor-data "tiers".  This means that each tier will
     * contain 2 data points for every 20 in the tier below, so each tier has 10% of the data of
     * the next-lower tier.
     */
    public static final int DEFAULT_ZOOM_LEVEL_BETWEEN_TIERS = 20;

    private static final String TAG = "ScalarSensor";
    private static final double DENOMINATOR_FOR_RPMS = 60 * 1000.0;
    public static final String BUNDLE_KEY_SENSOR_VALUE = "key_sensor_value";

    private final FailureListener mDataFailureListener;
    private final int mZoomLevelBetweenTiers;

    private final long mDefaultGraphRange;
    private Executor mUiThreadExecutor;
    private ValueFilter mValueFilter = null;
    private ChartController mChartController;
    private AudioGenerator mAudioGenerator;
    private final Clock mClock;

    public ScalarSensor(String id) {
        this(id, AppSingleton.getUiThreadExecutor());
    }

    @VisibleForTesting
    public ScalarSensor(String id, Executor uiThreadExecutor) {
        this(id, ExternalAxisController.DEFAULT_GRAPH_RANGE_IN_MILLIS, uiThreadExecutor,
                DEFAULT_ZOOM_LEVEL_BETWEEN_TIERS, new UptimeClock());
    }

    @VisibleForTesting
    ScalarSensor(String id, long defaultGraphRange, Executor uiThreadExecutor,
            int zoomLevelBetweenTiers, Clock clock) {
        super(id);
        mDefaultGraphRange = defaultGraphRange;
        mUiThreadExecutor = uiThreadExecutor;
        mZoomLevelBetweenTiers = zoomLevelBetweenTiers;
        mDataFailureListener = new FailureListener() {
            @Override
            public void fail(Exception e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Exception storing sensor value " + getId(), e);
                }
            }
        };
        mClock = clock;
    }

    @Override
    public SensorPresenter createPresenter(final DataViewOptions dataViewOptions,
            NumberFormat statsNumberFormat, StatsListener statsListener) {
        final ChartController chartController =
                getChartController(dataViewOptions, getId(), mDefaultGraphRange);

        final AudioGenerator audioGenerator = getAudioGenerator();
        final SensorPresenter.OptionsPresenter optionsPresenter = createOptionsPresenter();
        final StatsAccumulator.StatsDisplay statsDisplay =
                new StatsAccumulator.StatsDisplay(statsNumberFormat);
        statsDisplay.addStatsListener(statsListener);

        return new SensorPresenter() {
            private boolean mAudioEnabled;

            @Override
            public void startShowing(View contentView,
                    ExternalAxisController.InteractionListener listener) {
                chartController.setInteractionListener(listener);
                chartController.setChartView((ChartView) contentView);
                if (mAudioEnabled) {
                    audioGenerator.startPlaying();
                }
            }

            @Override
            public void onPause() {
                audioGenerator.stopPlaying();
                chartController.onPause();
            }

            @Override
            public void onResume(long resetTime) {
                chartController.onResume(resetTime);
            }

            @Override
            public void onNewData(long timestamp, Data bundle) {
                double value = bundle.getValue();
                chartController.addPoint(new ChartData.DataPoint(timestamp, value));
                if (mAudioEnabled) {
                    audioGenerator.addData(timestamp, value, chartController.getRenderedYMin(),
                            chartController.getRenderedYMax());
                }
                statsDisplay.updateFromBundle(bundle);
            }

            @Override
            public void onRecordingStateChange(boolean isRecording, long recordingStart) {
                chartController.setRecordingStartTime(recordingStart);
                statsDisplay.clear();
            }

            @Override
            public void onLabelsChanged(List<Label> labels) {
                chartController.setLabels(labels);
            }

            @Override
            public void onGlobalXAxisChanged(long xMin, long xMax, boolean isPinnedToNow,
                    DataController dataController) {
                chartController.onGlobalXAxisChanged(xMin, xMax, isPinnedToNow, dataController);
            }

            @Override
            public double getMinY() {
                return chartController.getRenderedYMin();
            }

            @Override
            public double getMaxY() {
                return chartController.getRenderedYMax();
            }

            @Override
            public void onStopObserving() {
                statsDisplay.clear();
                setAudioEnabled(false);
                destroyChartController();
            }

            @Override
            public void onViewRecycled() {
                chartController.onViewRecycled();
            }

            @Override
            public OptionsPresenter getOptionsPresenter() {
                return optionsPresenter;
            }

            @Override
            public void updateAudioSettings(boolean audioEnabled, String sonificationType) {
                setAudioEnabled(audioEnabled);
                mAudioGenerator.setSonificationType(sonificationType);
            }

            private void setAudioEnabled(boolean enableAudio) {
                mAudioEnabled = enableAudio;
                if (mAudioEnabled) {
                    audioGenerator.startPlaying();
                } else {
                    audioGenerator.stopPlaying();
                }
            }

            @Override
            public void setShowStatsOverlay(boolean showStatsOverlay) {
                chartController.setShowStatsOverlay(showStatsOverlay);
            }

            @Override
            public void updateStats(List<StreamStat> stats) {
                chartController.updateStats(stats);
            }

            @Override
            public void setYAxisRange(double minimumYAxisValue, double maximumYAxisValue) {
                chartController.setYAxis(minimumYAxisValue, maximumYAxisValue);
            }

            @Override
            public void resetView() {
                chartController.clearData();
            }

            @Override
            public void setTriggers(List<SensorTrigger> triggers) {
                chartController.setTriggers(triggers);
            }
        };
    }

    private void destroyChartController() {
        if (mChartController != null) {
            // Destroy the controller. This causes previous data to be destroyed on a rotate,
            // later we can add that data back via the background service.
            mChartController.onDestroy();
            mChartController = null;
        }
        if (mAudioGenerator != null) {
            mAudioGenerator.destroy();
            mAudioGenerator = null;
        }
    }

    // Returns the existing chartController if available, or makes a new one if not.
    @NonNull
    private ChartController getChartController(DataViewOptions dataViewOptions, String id,
            long defaultGraphRange) {
        if (mChartController == null) {
            mChartController = createChartController(dataViewOptions, id, defaultGraphRange);
        } else {
            mChartController.updateOptions(dataViewOptions.getGraphColor(),
                    dataViewOptions.getLineGraphOptions(), id);
        }
        return mChartController;
    }

    @NonNull
    private AudioGenerator getAudioGenerator() {
        if (mAudioGenerator == null) {
            mAudioGenerator = new SimpleJsynAudioGenerator();
        }
        return mAudioGenerator;
    }

    @NonNull
    protected ChartController createChartController(DataViewOptions dataViewOptions, String id,
            long defaultGraphRange) {
        ChartController chartController = new ChartController(
                ChartOptions.ChartPlacementType.TYPE_OBSERVE, dataViewOptions.getLineGraphOptions(),
                mClock);
        chartController.updateColor(dataViewOptions.getGraphColor());
        chartController.setDefaultGraphRange(defaultGraphRange);
        chartController.setSensorId(id);
        return chartController;
    }

    /**
     * If this is overridden to return a non-null options presenter, then that presenter will
     * have its options shown, as well as the frequency options.
     */
    protected SensorPresenter.OptionsPresenter createAdditionalScalarOptionsPresenter() {
        return null;
    }

    protected final SensorPresenter.OptionsPresenter createOptionsPresenter() {
        final SensorPresenter.OptionsPresenter additionalPresenter =
                createAdditionalScalarOptionsPresenter();

        // TODO: this is very convoluted
        final FrequencyOptionsPresenter frequencyPresenter = new FrequencyOptionsPresenter(this,
                additionalPresenter) {
            @Override
            protected boolean getDefaultFrequencyChecked() {
                return ScalarSensor.this.getDefaultFrequencyChecked();
            }

            @Override
            protected GoosciSensorConfig.BleSensorConfig.ScaleTransform getDefaultScaleTransform() {
                return ScalarSensor.this.getDefaultScaleTransform();
            }
        };

        return new SensorPresenter.OptionsPresenter() {
            @Override
            public View buildOptionsView(final ActiveBundle activeBundle, Context context) {
                @SuppressLint("InflateParams") final ViewGroup inflated =
                        (ViewGroup) LayoutInflater.from(context).inflate(
                                R.layout.scalar_sensor_options, null);
                inflated.addView(frequencyPresenter.buildOptionsView(activeBundle, context));
                return inflated;
            }

            @Override
            public void applyOptions(ReadableSensorOptions bundle) {
            }
        };
    }

    protected boolean getDefaultFrequencyChecked() {
        return false;
    }

    protected GoosciSensorConfig.BleSensorConfig.ScaleTransform getDefaultScaleTransform() {
        return null;
    }

    public static double getValue(SensorObserver.Data bundle) {
        return bundle.getValue();
    }

    public static boolean hasValue(SensorObserver.Data bundle) {
        return bundle.hasValidValue();
    }

    @Override
    public SensorRecorder createRecorder(final Context context,
            final SensorObserver observer, SensorStatusListener listener,
            final SensorEnvironment environment) {
        final StatsAccumulator statsAccumulator = new StatsAccumulator(getId());
        final RecordingDataController dataController = Preconditions.checkNotNull(
                environment.getDataController());

        // We need twice the buffer as the zoom level, because in this implementation of zoom, we
        // decided to store min and max data points at each level.
        // TODO: make this configurable?
        int zoomBufferSize = mZoomLevelBetweenTiers * 2;
        final ZoomRecorder zoomRecorder = new ZoomRecorder(getId(), zoomBufferSize, 1);
        final ScalarStreamConsumer consumer = new ScalarStreamConsumer(statsAccumulator, observer,
                dataController, zoomRecorder);
        final SensorRecorder recorder = makeScalarControl(consumer, environment, context, listener);
        return new DelegatingSensorRecorder(recorder) {
            private String mRunId;

            @Override
            public void startObserving() {
                dataController.setDataErrorListenerForSensor(getId(), mDataFailureListener);
                super.startObserving();
            }

            @Override
            public void stopObserving() {
                super.stopObserving();
                dataController.clearDataErrorListenerForSensor(getId());
            }

            @Override
            public void startRecording(String runId) {
                mRunId = runId;
                statsAccumulator.clearStats();
                zoomRecorder.clear();
                consumer.startRecording();
                super.startRecording(runId);
            }

            @Override
            public void stopRecording(Trial trialToUpdate) {
                super.stopRecording(trialToUpdate);

                TrialStats trialStats = statsAccumulator.makeSaveableStats();
                trialStats.putStat(GoosciTrial.SensorStat.ZOOM_PRESENTER_TIER_COUNT,
                        zoomRecorder.countTiers());
                trialStats.putStat(GoosciTrial.SensorStat.ZOOM_PRESENTER_ZOOM_LEVEL_BETWEEN_TIERS,
                        mZoomLevelBetweenTiers);
                if (trialToUpdate != null) {
                    trialToUpdate.setStats(trialStats);
                }
                consumer.stopRecording();
                statsAccumulator.clearStats();
            }

            @Override
            public boolean hasRecordedData() {
                return consumer.hasRecordedData();
            }
        };
    }

    public static ValueFilter computeValueFilter(long newWindow, double newFilter,
            boolean newEnabled, GoosciSensorConfig.BleSensorConfig.ScaleTransform scaleTransform) {
        ValueFilter valueFilter = computeFrequencyFilter(newWindow, newFilter, newEnabled);
        if (scaleTransform == null) {
            return valueFilter;
        } else {
            return ComposeFilter.applyInOrder(valueFilter, new ScaleFilter(scaleTransform));
        }
    }

    private static ValueFilter computeFrequencyFilter(long newWindow, double newFilter,
            boolean newEnabled) {
        if (newEnabled) {
            return new FrequencyBuffer(newWindow, DENOMINATOR_FOR_RPMS, newFilter);
        } else {
            return ValueFilter.IDENTITY;
        }
    }

    @Override
    public void setScalarFilter(ValueFilter filter) {
        mValueFilter = filter;
    }

    /**
     * @param c           whenever you have a new value, call c.addData(long timestampMillis, double
     *                    value).
     *                    Note that timestampMillis need _not_ be “now”, if you have any latency or
     *                    skew
     *                    in your sensor (see {@link SensorEnvironment#getDefaultClock()}
     * @param environment system-level services for gathering, especially the default clock.
     *                    Note that subclasses should _not_ record scalar values to the
     *                    datacontroller; that is handled in this superclass already.
     * @param listener    You must call listener.onSourceStatus to indicate the sensor’s connection
     *                    status. You may call listener.onSourceError to indicate errors connecting
     *                    to the sensor.
     * @return a recorder that generates values for this sensor
     */
    protected abstract SensorRecorder makeScalarControl(StreamConsumer c,
            SensorEnvironment environment, Context context, SensorStatusListener listener);

    private class ScalarStreamConsumer implements StreamConsumer {
        private static final int NO_DATA_RECORDED = -1;

        private final StatsAccumulator mStatsAccumulator;
        private final RecordingDataController mDataController;
        private final ZoomRecorder mZoomRecorder;
        private boolean mIsRecording = false;
        private long mLastDataTimestampMillis = NO_DATA_RECORDED;
        private long mTimestampBeforeRecordingStart = NO_DATA_RECORDED;
        private SensorMessage.Pool mMessagePool;

        public ScalarStreamConsumer(StatsAccumulator statsAccumulator,
                SensorObserver observer, RecordingDataController dataController,
                ZoomRecorder zoomRecorder) {
            mStatsAccumulator = statsAccumulator;
            mDataController = dataController;
            mZoomRecorder = zoomRecorder;
            mMessagePool = new SensorMessage.Pool(observer);
        }

        public void startRecording() {
            mIsRecording = true;
            mTimestampBeforeRecordingStart = mLastDataTimestampMillis;
        }

        public void stopRecording() {
            mIsRecording = false;
            mZoomRecorder.flushAllTiers(mDataController);
        }

        public boolean maintainsTimeSeries(final long timestampMillis) {
            if (timestampMillis > mLastDataTimestampMillis) {
                return true;
            }
            return false;
        }

        @Override
        public boolean addData(final long timestampMillis, double value) {
            // TODO: would inlining here gain performance?
            if (!maintainsTimeSeries(timestampMillis)) {
                return false;
            }
            value = maybeFilter(timestampMillis, value);
            observeData(timestampMillis, value);
            recordData(timestampMillis, value);
            mLastDataTimestampMillis = timestampMillis;
            return true;
        }

        public void observeData(final long timestampMillis, double value) {
            // Each call to obtain is guaranteed to retrieve a currently-unused message...
            SensorMessage message = mMessagePool.obtain();

            // ...which is set up with the correct values here...
            message.setTimestamp(timestampMillis);
            message.getData().setValue(value);
            mStatsAccumulator.updateRecordingStreamStats(timestampMillis, value);
            mStatsAccumulator.addStatsToBundle(message.getData());

            // ..and will be cleared and released back to the message pool when getRunnable is run.
            runOnMainThread(message.getRunnable());
        }

        public void recordData(long timestampMillis, double value) {
            if (mIsRecording) {
                mZoomRecorder.addData(timestampMillis, value, mDataController);
                mDataController.addScalarReading(getId(), 0, timestampMillis, value);
            }
        }

        public double maybeFilter(long timestampMillis, double value) {
            if (mValueFilter != null) {
                value = mValueFilter.filterValue(timestampMillis, value);
            }
            return value;
        }

        public boolean hasRecordedData() {
            return mLastDataTimestampMillis > mTimestampBeforeRecordingStart;
        }
    }

    protected void runOnMainThread(Runnable runnable) {
        mUiThreadExecutor.execute(runnable);
    }

    public static SensorManager getSensorManager(Context context) {
        return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

}
