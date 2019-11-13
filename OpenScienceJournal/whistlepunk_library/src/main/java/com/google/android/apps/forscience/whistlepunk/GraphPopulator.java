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

package com.google.android.apps.forscience.whistlepunk;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.FallibleConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import java.util.List;

// TODO(saff): port tests from Weather
public class GraphPopulator {
  // How many datapoints do we grab from the database at one time?
  private static final int MAX_DATAPOINTS_PER_SENSOR_LOAD = 100;

  private Range<Long> requestedTimes = null;
  private ObservationDisplay observationDisplay;
  private boolean requestInFlight = false;
  private final long requestId;

  // TODO: can we pass in the request id, rather than generating it here?
  public GraphPopulator(ObservationDisplay observationDisplay, Clock clock) {
    this.observationDisplay = observationDisplay;
    requestId = clock.getNow();
  }

  /** GraphStatus for a graph that is not changing its x axis. */
  @NonNull
  public static GraphStatus constantGraphStatus(
      final long firstTimestamp, final long lastTimestamp) {
    return new GraphStatus() {
      @Override
      public long getMinTime() {
        return firstTimestamp;
      }

      @Override
      public long getMaxTime() {
        return lastTimestamp;
      }

      @Override
      public boolean graphIsStillValid() {
        // TODO(saff): should return false once the activity is disposed
        return true;
      }
    };
  }

  /**
   * If the graphStatus shows that there are still values that need to be fetched to fill the
   * currently-displayed graph, this method will begin fetching them.
   *
   * <p>Call only on the UI thread.
   */
  public void requestObservations(
      final GraphStatus graphStatus,
      final DataController dataController,
      final FailureListener failureListener,
      final int resolutionTier,
      final String trialId,
      final String sensorId) {
    if (requestInFlight) {
      return;
    }
    final TimeRange r = getRequestRange(graphStatus);
    if (r == null) {
      observationDisplay.onFinish(requestId);
    } else {
      requestInFlight = true;
      dataController.getScalarReadings(
          trialId,
          sensorId,
          resolutionTier,
          r,
          MAX_DATAPOINTS_PER_SENSOR_LOAD,
          MaybeConsumers.chainFailure(
              failureListener,
              new FallibleConsumer<ScalarReadingList>() {
                @Override
                public void take(ScalarReadingList observations) {
                  requestInFlight = false;
                  if (graphStatus.graphIsStillValid()) {
                    final Pair<Range<Long>, Range<Double>> received =
                        addObservationsToDisplay(observations);
                    if (received.first != null) {
                      observationDisplay.addRange(observations, received.second, requestId);
                    }
                    addToRequestedTimes(getEffectiveAddedRange(r, received.first));
                    requestObservations(
                        graphStatus,
                        dataController,
                        failureListener,
                        resolutionTier,
                        trialId,
                        sensorId);
                  }
                }

                public void addToRequestedTimes(Range<Long> effectiveAdded) {
                  requestedTimes = Ranges.span(requestedTimes, effectiveAdded);
                }

                public Pair<Range<Long>, Range<Double>> addObservationsToDisplay(
                    ScalarReadingList observations) {
                  List<ScalarReading> points = ScalarReading.slurp(observations);
                  long xMin = Long.MAX_VALUE;
                  long xMax = Long.MIN_VALUE;
                  double yMin = Double.MAX_VALUE;
                  double yMax = Double.MIN_VALUE;
                  Range<Long> timeRange = null;
                  Range<Double> valueRange = null;
                  for (ScalarReading point : points) {
                    if (point.getCollectedTimeMillis() < xMin) {
                      xMin = point.getCollectedTimeMillis();
                    }
                    if (point.getCollectedTimeMillis() > xMax) {
                      xMax = point.getCollectedTimeMillis();
                    }
                    if (point.getValue() < yMin) {
                      yMin = point.getValue();
                    }
                    if (point.getValue() > yMax) {
                      yMax = point.getValue();
                    }
                  }
                  if (xMin <= xMax) {
                    timeRange = Range.closed(xMin, xMax);
                  }
                  if (yMin <= yMax) {
                    valueRange = Range.closed(yMin, yMax);
                  }
                  return new Pair<>(timeRange, valueRange);
                }
              }));
    }
    return;
  }

  private TimeRange getRequestRange(GraphStatus graphStatus) {
    final long minTime = graphStatus.getMinTime();
    final long maxTime = graphStatus.getMaxTime();
    // TODO(saff): push more of this computation to be testable in NextRequestType
    return computeRequestRange(
        NextRequestType.compute(requestedTimes, minTime, maxTime), minTime, maxTime);
  }

  private TimeRange computeRequestRange(NextRequestType type, long minTime, long maxTime) {
    switch (type) {
      case NONE:
        return null;
      case FIRST:
        return TimeRange.oldest(Range.closed(minTime, maxTime));
      case NEXT_LOWER:
        return TimeRange.oldest(Range.closedOpen(minTime, requestedTimes.lowerEndpoint()));
      case NEXT_HIGHER:
        return TimeRange.oldest(Range.openClosed(requestedTimes.upperEndpoint(), maxTime));
      default:
        throw new IllegalStateException("Should never happen");
    }
  }

  private Range<Long> getEffectiveAddedRange(TimeRange requested, Range<Long> returned) {
    if (returned == null) {
      return requested.getTimes().canonical(DiscreteDomain.longs());
    }

    switch (requested.getOrder()) {
      case NEWEST_FIRST:
        return Range.closed(returned.lowerEndpoint(), requested.getTimes().upperEndpoint());
      case OLDEST_FIRST:
        return Range.closed(requested.getTimes().lowerEndpoint(), returned.upperEndpoint());
      default:
        throw new IllegalArgumentException("Unexpected value for enum: " + requested.getOrder());
    }
  }

  public long getRequestId() {
    return requestId;
  }

  public interface GraphStatus {
    long getMinTime();

    long getMaxTime();

    boolean graphIsStillValid();
  }

  public interface ObservationDisplay {
    void addRange(ScalarReadingList observations, Range<Double> valueRange, long requestId);

    void onFinish(long requestId);
  }
}
