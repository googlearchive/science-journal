package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphData;
import com.google.android.apps.forscience.whistlepunk.scalarchart.LineGraphPresenter;
import com.google.android.apps.forscience.whistlepunk.MemorySensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.RecordingStatusListener;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.StatsListener;
import com.google.common.util.concurrent.MoreExecutors;

import org.achartengine.model.XYSeries;

import java.util.List;

public class ManualSensor extends ScalarSensor {
    private StreamConsumer mConsumer;
    private LineGraphPresenter mLineGraphPresenter;
    private GraphData mGraphData;

    // By default, run tests with a minimum throwaway size threshold.
    private int mGraphDataThrowawaySizeThreshold = 0;
    private MemorySensorHistoryStorage mStorage = new MemorySensorHistoryStorage();

    public ManualSensor(String sensorId, long defaultGraphRange,
            int zoomLevelBetweenResolutionTiers) {
        super(sensorId, defaultGraphRange, MoreExecutors.directExecutor(),
                zoomLevelBetweenResolutionTiers);
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
            public void applyOptions(ReadableSensorOptions settings) {

            }
        };
    }

    @NonNull
    @Override
    protected LineGraphPresenter createLineGraphPresenter(DataViewOptions dataViewOptions,
            ExternalAxisController.InteractionListener interactionListener) {
        mLineGraphPresenter = new LineGraphPresenter(true, true,
                dataViewOptions.getGraphColor(),
                dataViewOptions.getLineGraphOptions(), interactionListener) {
            @Override
            protected GraphData createGraphData(ScalarDisplayOptions scalarDisplayOptions) {
                mGraphData = new GraphData(scalarDisplayOptions.getWindow(),
                        scalarDisplayOptions.getBlurType(), scalarDisplayOptions.getGaussianSigma(),
                        mGraphDataThrowawaySizeThreshold);
                return mGraphData;
            }
        };
        return mLineGraphPresenter;
    }

    public List<GraphData.ReadonlyDataPoint> getRawData() {
        return mLineGraphPresenter.getRawData();
    }

    public XYSeries getLineData() {
        return mGraphData.getDataset().getSeriesAt(mGraphData.getLineDataIndex());
    }

    public void pushValue(long timestampMillis, double value) {
        if (mConsumer != null) {
            mConsumer.addData(timestampMillis, value);
        }
    }

    public SensorPresenter createPresenter() {
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
    public MemorySensorEnvironment makeSensorEnvironment(Context context,
            RecordingDataController rc) {
        return new MemorySensorEnvironment(rc, new FakeBleClient(context), mStorage);
    }

    @NonNull
    public SensorPresenter createRecordingPresenter(Context context, RecordingDataController rc,
            String runId, int graphDataThrowawaySizeThreshold) {
        mGraphDataThrowawaySizeThreshold = graphDataThrowawaySizeThreshold;
        return createRecordingPresenter(context, rc, runId);
    }

    public LineGraphPresenter getLineGraphPresenter() {
        return mLineGraphPresenter;
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
