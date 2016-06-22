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

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.apps.forscience.javalib.FallibleConsumer;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.base.Joiner;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.List;

// TODO(saff): port tests from Weather
public class GraphPopulator<O extends TimedEvent> {
    private ObservationStore<O> mObservationStore;
    private Range<Long> mRequestedTimes = null;
    private ObservationDisplay<O> mObservationDisplay;
    private boolean mRequestInFlight = false;

    public GraphPopulator(ObservationStore<O> observationStore,
            ObservationDisplay<O> observationDisplay) {
        mObservationStore = observationStore;
        mObservationDisplay = observationDisplay;
    }

    /**
     * GraphStatus for a graph that is not changing its x axis.
     */
    @NonNull
    public static GraphStatus constantGraphStatus(final long firstTimestamp,
            final long lastTimestamp) {
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
     * <p/>
     * Call only on the UI thread.
     */
    public void requestObservations(final GraphStatus graphStatus) {
        if (mRequestInFlight) {
            return;
        }
        // Note: The time range needs to be oldest-first or the graph data smoothing gets messed
        // up because it assumes points are added chronologically.
        // TODO: add some function that allows points to be added newest first and all the
        // averaging / calculations to happen after adding is completed.
        final TimeRange r = getRequestRange(graphStatus);
        if (r == null) {
            mObservationDisplay.onFinish();
        } else {
            mRequestInFlight = true;
            mObservationStore.getObservations(r, new FallibleConsumer<List<O>>() {
                @Override
                public void take(List<O> observations) {
                    mRequestInFlight = false;
                    if (graphStatus.graphIsStillValid()) {
                        final Range<Long> received = addObservationsToDisplay(observations);

                        if (received != null) {
                            mObservationDisplay.commit(received);
                        }

                        addToRequestedTimes(getEffectiveAddedRange(r, received));

                        requestObservations(graphStatus);
                    }
                }

                public void addToRequestedTimes(Range<Long> effectiveAdded) {
                    mRequestedTimes = Ranges.span(mRequestedTimes, effectiveAdded);
                }

                public Range<Long> addObservationsToDisplay(List<O> observations) {
                    Range<Long> range = null;
                    for (O observation : observations) {
                        mObservationDisplay.add(observation);
                        range = Ranges.span(range, Range.singleton(
                                observation.getCollectedTimeMillis()));
                    }
                    return range;
                }
            });
        }
    }

    private TimeRange getRequestRange(GraphStatus graphStatus) {
        final long minTime = graphStatus.getMinTime();
        final long maxTime = graphStatus.getMaxTime();
        // TODO(saff): push more of this computation to be testable in NextRequestType
        return computeRequestRange(NextRequestType.compute(mRequestedTimes, minTime, maxTime),
                minTime, maxTime);
    }

    private TimeRange computeRequestRange(NextRequestType type, long minTime, long maxTime) {
        switch (type) {
            case NONE:
                return null;
            case FIRST:
                return TimeRange.oldest(Range.closed(minTime, maxTime));
            case NEXT_LOWER:
                return TimeRange.oldest(Range.closedOpen(minTime, mRequestedTimes.lowerEndpoint()));
            case NEXT_HIGHER:
                return TimeRange.oldest(Range.openClosed(mRequestedTimes.upperEndpoint(), maxTime));
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
                throw new IllegalArgumentException(
                        "Unexpected value for enum: " + requested.getOrder());
        }
    }

    public interface GraphStatus {
        long getMinTime();

        long getMaxTime();

        boolean graphIsStillValid();
    }

    public interface ObservationStore<O> {
        /**
         * Request the data in the given time range.  Must be called on the UI thread.
         *
         * @param onSuccess callback, also executed on the UI thread.
         */
        void getObservations(TimeRange r, FallibleConsumer<List<O>> onSuccess);
    }

    public interface ObservationDisplay<O> {
        void add(O observation);

        void commit(Range<Long> range);

        void onFinish();
    }
}