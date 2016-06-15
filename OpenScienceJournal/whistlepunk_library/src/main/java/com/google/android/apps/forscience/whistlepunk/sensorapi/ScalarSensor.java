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
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.audiogen.AudioGenerator;
import com.google.android.apps.forscience.whistlepunk.audiogen.SimpleJsynAudioGenerator;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.scalarchart.LineGraphPresenter;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.ScalarDataLoader;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsListener;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FrequencyOptionsPresenter
        .FilterChangeListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.common.annotations.VisibleForTesting;

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
    protected static final double DENOMINATOR_FOR_RPMS = 60 * 1000.0;
    private static final String BUNDLE_KEY_SENSOR_VALUE = "key_sensor_value";

    /**
     * how many (minimum) screenfuls of data should we keep in memory?
     */
    private static final int KEEP_THIS_MANY_SCREENS = 2;

    /**
     * How long can the screen be off before we forget old data?
     */
    private static final long MAX_BLACKOUT_MILLIS_BEFORE_CLEARING = 5000;

    private final FailureListener mDataFailureListener;
    private final int mZoomLevelBetweenTiers;

    private final long mDefaultGraphRange;
    private Executor mUiThreadExecutor;
    private ValueFilter mValueFilter = null;
    private LineGraphPresenter mLineGraphPresenter;
    private AudioGenerator mAudioGenerator;

    public ScalarSensor(String id) {
        this(id, AppSingleton.getUiThreadExecutor());
    }

    @VisibleForTesting
    public ScalarSensor(String id, Executor uiThreadExecutor) {
        this(id, ExternalAxisController.DEFAULT_GRAPH_RANGE_IN_MILLIS, uiThreadExecutor,
                DEFAULT_ZOOM_LEVEL_BETWEEN_TIERS);
    }

    @VisibleForTesting
    public ScalarSensor(String id, long defaultGraphRange, Executor uiThreadExecutor,
            int zoomLevelBetweenTiers) {
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
    }

    @Override
    public SensorPresenter createPresenter(final DataViewOptions dataViewOptions,
            StatsListener statsListener,
            final ExternalAxisController.InteractionListener interactionListener) {
        final LineGraphPresenter lineGraphPresenter =
                getLineGraphPresenter(dataViewOptions, interactionListener);

        final AudioGenerator audioGenerator = getAudioGenerator();
        final SensorPresenter.OptionsPresenter optionsPresenter = createOptionsPresenter();
        final StatsAccumulator.StatsDisplay statsDisplay = new StatsAccumulator.StatsDisplay();
        statsDisplay.addStatsListener(statsListener);

        return new SensorPresenter() {
            private long mResetTime = -1;
            private long mRecordingStartTime = RecordingMetadata.NOT_RECORDING;
            private boolean mAnythingLoaded = false;
            private long mMinLoadedX = Long.MAX_VALUE;
            private long mMaxLoadedX = Long.MIN_VALUE;
            private boolean mAudioEnabled;

            @Override
            public void startShowing(ViewGroup contentView) {
                lineGraphPresenter.populateContentView(contentView);
                if (mAudioEnabled) {
                    audioGenerator.startPlaying();
                }
            }

            @Override
            public void onPause() {
                audioGenerator.stopPlaying();
            }

            @Override
            public void onResume(long resetTime) {
                mResetTime = resetTime;
                setAudioEnabled(mAudioEnabled);
                if (resetTime > mMaxLoadedX + MAX_BLACKOUT_MILLIS_BEFORE_CLEARING) {
                    // TODO: test this behavior
                    lineGraphPresenter.clearData(true);
                }
            }

            @Override
            public void onNewData(long timestamp, Bundle bundle) {
                // TODO: test this behavior
                if (mResetTime != -1) {
                    if (timestamp < mResetTime) {
                        // straggling datapoint from before the reset, ignore
                        return;
                    } else {
                        mResetTime = -1;
                    }
                }
                // TODO: extract as a testable object
                if (timestamp > mMaxLoadedX) {
                    mMaxLoadedX = timestamp;

                    // Get rid of data too old to be interesting for "now", but too new to be likely
                    // seen by scrolling from the current view.  If we're recording, we'll swap
                    // this data back in when we scroll to it.  If not, then we have no data
                    // retention guarantees.

                    long throwawayBefore =
                            timestamp - (KEEP_THIS_MANY_SCREENS * mDefaultGraphRange);
                    long throwawayAfter = lineGraphPresenter.getRenderedXMax() + mDefaultGraphRange;
                    lineGraphPresenter.throwAwayBetween(throwawayAfter, throwawayBefore);
                }
                if (timestamp < mMinLoadedX) {
                    mMinLoadedX = timestamp;
                }
                double value = getValue(bundle);
                lineGraphPresenter.addToGraph(timestamp, value);
                audioGenerator.addData(timestamp, value, lineGraphPresenter.getRenderedYMin(),
                        lineGraphPresenter.getRenderedYMax());
                statsDisplay.updateFromBundle(bundle);
            }

            @Override
            public void onRecordingStateChange(boolean isRecording, long recordingStart) {
                mRecordingStartTime = recordingStart;
                lineGraphPresenter.setRecordingState(
                        isRecording ? recordingStart : RecordingMetadata.NOT_RECORDING);
                statsDisplay.clear();
            }

            @Override
            public void onLabelsChanged(List<Label> labels) {
                lineGraphPresenter.setLabels(labels);
            }

            @Override
            public void onXAxisChanged(long xMin, long xMax, boolean isPinnedToNow,
                    DataController dataController) {
                if (mRecordingStartTime != RecordingMetadata.NOT_RECORDING) {
                    if (!mAnythingLoaded) {
                        // Don't load anything before the recording start time if we got here
                        // from resume.
                        xMin = Math.max(xMin, mRecordingStartTime);
                        loadReadings(dataController, xMin, xMax);
                        mMinLoadedX = xMin;
                        mMaxLoadedX = xMax;
                    }
                    // TODO: The check comparing xMin to mRecordingStartTime can mean that we loose
                    // a few data points just after the recording start time. This behavior is
                    // not great but better than the current alternative (b/26816719).
                    if (xMin < mMinLoadedX && xMin > mRecordingStartTime) {
                        loadReadings(dataController, xMin, mMinLoadedX);
                        mMinLoadedX = xMin;
                    }
                    if (xMax > mMaxLoadedX) {
                        if (!isPinnedToNow) {
                            // if it's pinned to now, then we don't expect to find data magically
                            // appearing in front of old data
                            loadReadings(dataController, mMaxLoadedX, xMax);
                        }
                        mMaxLoadedX = xMax;
                    }
                }
                mAnythingLoaded = true;
                mMinLoadedX = Math.min(xMin, mMinLoadedX);
                mMaxLoadedX = Math.max(xMax, mMaxLoadedX);

                lineGraphPresenter.updateIsPinned(isPinnedToNow);
                lineGraphPresenter.setXAxis(xMin, xMax);

                long throwawayThreshhold = xMin - (KEEP_THIS_MANY_SCREENS - 1) * mDefaultGraphRange;

                lineGraphPresenter.throwAwayBefore(throwawayThreshhold);

                mMinLoadedX = Math.max(throwawayThreshhold, mMinLoadedX);
            }

            private void loadReadings(DataController dataController, long minToLoad,
                    long maxToLoad) {
                ScalarDataLoader.loadSensorReadings(getId(), dataController, minToLoad, maxToLoad,
                        0, null, mDataFailureListener, lineGraphPresenter);
            }

            @Override
            public double getMinY() {
                return lineGraphPresenter.getRenderedYMin();
            }

            @Override
            public double getMaxY() {
                return lineGraphPresenter.getRenderedYMax();
            }

            @Override
            public void onStopObserving() {
                statsDisplay.clear();
                destroyLineGraphPresenter();
            }

            @Override
            public OptionsPresenter getOptionsPresenter() {
                return optionsPresenter;
            }

            @Override
            public boolean hasOptionsPresenter() {
                return optionsPresenter != null;
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
                lineGraphPresenter.setShowStatsOverlay(showStatsOverlay);
            }

            @Override
            public void updateStats(List<StreamStat> stats) {
                lineGraphPresenter.updateStats(stats);
            }

            @Override
            public void setYAxisRange(double minimumYAxisValue, double maximumYAxisValue) {
                lineGraphPresenter.setYAxisRange(minimumYAxisValue, maximumYAxisValue);
            }

            @Override
            public void resetView() {
                lineGraphPresenter.resetView();
            }
        };
    }

    private void destroyLineGraphPresenter() {
        if (mLineGraphPresenter != null) {
            // Destroy the presenter. This causes previous data to be destroyed on a rotate,
            // later we can add that data back via the background service.
            mLineGraphPresenter.onDestroy();
            mLineGraphPresenter = null;
        }
        if (mAudioGenerator != null) {
            mAudioGenerator.destroy();
            mAudioGenerator = null;
        }
    }

    // Returns the existing line graph presenter if available, or makes a new one if not.
    @NonNull
    protected LineGraphPresenter getLineGraphPresenter(DataViewOptions dataViewOptions,
            ExternalAxisController.InteractionListener interactionListener) {
        if (mLineGraphPresenter == null) {
            mLineGraphPresenter = createLineGraphPresenter(dataViewOptions, interactionListener);
        } else {
            mLineGraphPresenter.onResume(dataViewOptions.getGraphColor(),
                    dataViewOptions.getLineGraphOptions(), interactionListener);
        }
        return mLineGraphPresenter;
    }

    @NonNull
    protected AudioGenerator getAudioGenerator() {
        if (mAudioGenerator == null) {
            mAudioGenerator = new SimpleJsynAudioGenerator();
        }
        return mAudioGenerator;
    }

    @NonNull
    protected LineGraphPresenter createLineGraphPresenter(DataViewOptions dataViewOptions,
            ExternalAxisController.InteractionListener interactionListener) {
        return new LineGraphPresenter(/* smooth Y axis adjustment */ true,
        /* show the leading edge point */ true, dataViewOptions.getGraphColor(),
                dataViewOptions.getLineGraphOptions(), interactionListener);
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

    public static double getValue(Bundle bundle) {
        return bundle.getDouble(BUNDLE_KEY_SENSOR_VALUE);
    }

    public static boolean hasValue(Bundle bundle) {
        return bundle.containsKey(BUNDLE_KEY_SENSOR_VALUE);
    }

    @Override
    public SensorRecorder createRecorder(final Context context,
            final SensorObserver observer, SensorStatusListener listener,
            final SensorEnvironment environment) {
        final StatsAccumulator statsAccumulator = new StatsAccumulator();
        final RecordingDataController dataController = environment.getDataController();

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
            public void stopRecording() {
                super.stopRecording();

                RunStats runStats = statsAccumulator.makeSaveableStats();
                runStats.putStat(ZoomRecorder.STATS_KEY_TIER_COUNT, zoomRecorder.countTiers());
                runStats.putStat(ZoomRecorder.STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS,
                        mZoomLevelBetweenTiers);
                environment.getDataController().setStats(mRunId, getId(), runStats);
                consumer.stopRecording();
                statsAccumulator.clearStats();
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
        private final Bundle mBundle;
        private final StatsAccumulator mStatsAccumulator;
        private final SensorObserver mObserver;
        private final RecordingDataController mDataController;
        private final ZoomRecorder mZoomRecorder;
        private boolean mIsRecording = false;

        public ScalarStreamConsumer(StatsAccumulator statsAccumulator,
                SensorObserver observer, RecordingDataController dataController,
                ZoomRecorder zoomRecorder) {
            mStatsAccumulator = statsAccumulator;
            mObserver = observer;
            mDataController = dataController;
            mBundle = new Bundle();
            mZoomRecorder = zoomRecorder;
        }

        public void startRecording() {
            mIsRecording = true;
        }

        public void stopRecording() {
            mIsRecording = false;
            mZoomRecorder.flushAllTiers(mDataController);
        }

        @Override
        public void addData(final long timestampMillis, double value) {
            if (mValueFilter != null) {
                value = mValueFilter.filterValue(timestampMillis, value);
            }
            final Bundle data = makeBundle(value);
            mStatsAccumulator.updateRecordingStreamStats(timestampMillis, value);
            mStatsAccumulator.addStatsToBundle(data);

            // TODO: try to remove this allocation
            mUiThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    mObserver.onNewData(timestampMillis, data);
                }
            });

            if (mIsRecording) {
                mZoomRecorder.addData(timestampMillis, value, mDataController);
                mDataController.addScalarReading(getId(), 0, timestampMillis, value);
            }
        }

        // TODO: profile this to make sure Bundles don't add extravagant memory and
        // CPU overhead
        private Bundle makeBundle(double value) {
            mBundle.putDouble(BUNDLE_KEY_SENSOR_VALUE, value);
            return mBundle;
        }
    }

    public static SensorManager getSensorManager(Context context) {
        return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public static boolean isSensorAvailable(Context context, int sensorType) {
        return getSensorManager(context).getDefaultSensor(sensorType) != null;
    }
}
