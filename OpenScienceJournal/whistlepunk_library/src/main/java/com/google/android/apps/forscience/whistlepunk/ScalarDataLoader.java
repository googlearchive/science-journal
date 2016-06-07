package com.google.android.apps.forscience.whistlepunk;

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.FallibleConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.scalarchart.LineGraphPresenter;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import java.util.List;

/**
 * A helper class to load scalar data into a LineGraphPresenter.
 */
// TODO: Consider moving stateful loading into this class to avoid redundancy
// in loading code in ExperimentDetailsFragment and RunReviewFragment.
public class ScalarDataLoader {

    // How many datapoints do we grab from the database at one time?
    private static final int MAX_DATAPOINTS_PER_SENSOR_LOAD = 100;

    private ScalarDataLoader() {

    }

    public static void loadSensorReadings(final String sensorId,
            final DataController dataController, final long firstTimestamp,
            final long lastTimestamp, final int resolutionTier, final Runnable onLoadFinish,
            final FailureListener failureListener, final LineGraphPresenter lineGraphPresenter) {
        final GraphPopulator.ObservationStore<ScalarReading> observationStore =
                makeObservationStore(sensorId, dataController, resolutionTier, failureListener);

        final GraphPopulator.ObservationDisplay<ScalarReading> observationDisplay =
                new GraphPopulator.ObservationDisplay<ScalarReading>() {
                    @Override
                    public void add(ScalarReading reading) {
                        long timestamp = reading.getCollectedTimeMillis();
                        double value = reading.getValue();

                        lineGraphPresenter.addToGraph(timestamp, value);
                    }

                    @Override
                    public void commit(Range<Long> range) {
                    }

                    @Override
                    public void onFinish() {
                        lineGraphPresenter.setShowProgress(false);
                        if (onLoadFinish != null) {
                            onLoadFinish.run();
                        }
                        lineGraphPresenter.refreshLabels();
                    }
                };

        final GraphPopulator.GraphStatus graphStatus = GraphPopulator.constantGraphStatus(
                firstTimestamp, lastTimestamp);

        final GraphPopulator<ScalarReading> populator = new GraphPopulator<>(observationStore,
                observationDisplay);
        populator.requestObservations(graphStatus);
    }

    @NonNull
    public static GraphPopulator.ObservationStore<ScalarReading> makeObservationStore(
            final String sensorId, final DataController dataController, final int resolutionTier,
            final FailureListener failureListener) {
        return new GraphPopulator.ObservationStore<ScalarReading>() {
            @Override
            public void getObservations(TimeRange r,
                    final FallibleConsumer<List<ScalarReading>> onSuccess) {
                dataController.getScalarReadings(sensorId, resolutionTier, r,
                        MAX_DATAPOINTS_PER_SENSOR_LOAD,
                        MaybeConsumers.chainFailure(failureListener,
                                new FallibleConsumer<ScalarReadingList>() {
                                    @Override
                                    public void take(ScalarReadingList list) throws Exception {
                                        // TODO(saff): more efficient than building a
                                        // list here?  GraphPopulator wants a List, maybe
                                        // generalize.
                                        onSuccess.take(ScalarReading.slurp(list));
                                    }
                                }));
            }
        };
    }

}
