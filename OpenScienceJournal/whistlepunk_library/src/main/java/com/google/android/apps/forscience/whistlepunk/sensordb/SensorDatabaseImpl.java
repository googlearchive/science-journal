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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import com.google.android.apps.forscience.whistlepunk.BatchInsertScalarReading;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

public class SensorDatabaseImpl implements SensorDatabase {
    private static class DbVersions {
        public static final int V1_START = 1;
        public static final int V2_INDEX = 2;
        public static final int V3_TIER = 3;
        public static final int V4_TRIALID = 4;
        public static final int CURRENT = V4_TRIALID;
    }

    private static class ScalarSensorsTable {
        public static final String NAME = "scalar_sensors";
        public static final String DEFAULT_TRIAL_ID = "0";

        public static class Column {
            public static final String TAG = "tag";
            public static final String RESOLUTION_TIER = "resolutionTier";
            public static final String TIMESTAMP_MILLIS = "timestampMillis";
            public static final String VALUE = "value";
            public static final String TRIAL_ID = "trialId";
        }

        public static final String CREATION_SQL = "CREATE TABLE " + NAME + " (" + Column.TAG + " " +
                " TEXT, " + Column.TIMESTAMP_MILLIS + " INTEGER, " + Column.VALUE + " REAL, "
                + Column.RESOLUTION_TIER + " INTEGER DEFAULT 0, " + Column.TRIAL_ID +
                " TEXT DEFAULT 0 NOT NULL);";

        public static final String INDEX_SQL =
                "CREATE INDEX timestamp ON " + NAME + "(" + Column.TIMESTAMP_MILLIS + ");";
    }

    private final SQLiteOpenHelper mOpenHelper;

    public SensorDatabaseImpl(Context context, String name) {
        mOpenHelper = new SQLiteOpenHelper(context, name, null, DbVersions.CURRENT) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(ScalarSensorsTable.CREATION_SQL);
                db.execSQL(ScalarSensorsTable.INDEX_SQL);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                while (oldVersion != newVersion) {
                    if (oldVersion == DbVersions.V1_START) {
                        db.execSQL(ScalarSensorsTable.INDEX_SQL);
                        oldVersion = DbVersions.V2_INDEX;
                    }
                    if (oldVersion == DbVersions.V2_INDEX) {
                        db.execSQL("ALTER TABLE " + ScalarSensorsTable.NAME + " ADD COLUMN "
                                + ScalarSensorsTable.Column.RESOLUTION_TIER
                                + " INTEGER DEFAULT 0;");
                        oldVersion = DbVersions.V3_TIER;
                    }
                    if (oldVersion == DbVersions.V3_TIER) {
                        db.execSQL("ALTER TABLE " + ScalarSensorsTable.NAME + " ADD COLUMN "
                                + ScalarSensorsTable.Column.TRIAL_ID + " TEXT DEFAULT 0 NOT NULL;");
                        oldVersion = DbVersions.V4_TRIALID;
                    }
                }
            }
        };
    }


    @Override
    public void addScalarReadings(List<BatchInsertScalarReading> readings) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try {
            db.beginTransaction();
            for (BatchInsertScalarReading r : readings) {
                ContentValues values = new ContentValues();
                values.put(ScalarSensorsTable.Column.TRIAL_ID, r.trialId);
                values.put(ScalarSensorsTable.Column.TAG, r.sensorId);
                values.put(ScalarSensorsTable.Column.TIMESTAMP_MILLIS, r.timestampMillis);
                values.put(ScalarSensorsTable.Column.VALUE, r.value);
                values.put(ScalarSensorsTable.Column.RESOLUTION_TIER, r.resolutionTier);
                db.insert(ScalarSensorsTable.NAME, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void addScalarReading(String trialId, String sourceTag, int resolutionTier,
            long timestampMillis, double value) {
        ContentValues values = new ContentValues();
        values.put(ScalarSensorsTable.Column.TRIAL_ID, trialId);
        values.put(ScalarSensorsTable.Column.TAG, sourceTag);
        values.put(ScalarSensorsTable.Column.TIMESTAMP_MILLIS, timestampMillis);
        values.put(ScalarSensorsTable.Column.VALUE, value);
        values.put(ScalarSensorsTable.Column.RESOLUTION_TIER, resolutionTier);
        mOpenHelper.getWritableDatabase().insert(ScalarSensorsTable.NAME, null, values);
    }

    /**
     * Gets the selection string and selectionArgs based on the tag, range and resolution tier.
     *
     * @return a pair where the first element is the selection string and the second element is the
     * array of selectionArgs.
     */
    private Pair<String, String[]> getSelectionAndArgs(String trialId, String[] sensorTags,
            TimeRange range, int resolutionTier) {
        List<String> clauses = new ArrayList<>();
        List<String> values = new ArrayList<>();

        if (sensorTags != null || sensorTags.length == 0) {
            if (sensorTags.length == 1) {
                clauses.add(ScalarSensorsTable.Column.TAG + " = ?");
                values.add(sensorTags[0]);
            } else {
                // Generate "(?,?...") for length.
                StringBuilder bindString = new StringBuilder();
                for (String sensorTag : sensorTags) {
                    values.add(sensorTag);
                    if (bindString.length() == 0) {
                        bindString.append("(?");
                    } else {
                        bindString.append(",?");
                    }
                }
                bindString.append(")");
                clauses.add(ScalarSensorsTable.Column.TAG + " IN " + bindString.toString());
            }
        }

        clauses.add(ScalarSensorsTable.Column.TRIAL_ID + " = ?");
        values.add(Preconditions.checkNotNull(trialId));

        if (resolutionTier >= 0) {
            clauses.add(ScalarSensorsTable.Column.RESOLUTION_TIER + " = ?");
            values.add(String.valueOf(resolutionTier));
        }

        Range<Long> times = range.getTimes();
        Range<Long> canonicalTimes = times.canonical(DiscreteDomain.longs());
        if (canonicalTimes.hasLowerBound()) {
            String comparator = (canonicalTimes.lowerBoundType() == BoundType.CLOSED) ?
                    " >= ?" : " > ?";
            clauses.add(ScalarSensorsTable.Column.TIMESTAMP_MILLIS + comparator);
            values.add(String.valueOf(canonicalTimes.lowerEndpoint()));
        }
        if (canonicalTimes.hasUpperBound()) {
            String comparator = (canonicalTimes.upperBoundType() == BoundType.CLOSED) ?
                    " <= ?" : " < ?";
            clauses.add(ScalarSensorsTable.Column.TIMESTAMP_MILLIS + comparator);
            values.add(String.valueOf(canonicalTimes.upperEndpoint()));
        }

        return new Pair<>(Joiner.on(" AND ").join(clauses),
                values.toArray(new String[values.size()]));
    }

    @Override
    public ScalarReadingList getScalarReadings(String trialId, String sensorTag, TimeRange range,
            int resolutionTier, int maxRecords) {
        try (Cursor cursor = getCursor(trialId, new String[]{sensorTag}, range, resolutionTier,
                maxRecords)) {
            if (cursor.getCount() == 0) {
                // Database returned no results with Trial Id; Attempt to use default Trial Id
                try (Cursor fallbackCursor = getCursor(
                        ScalarSensorsTable.DEFAULT_TRIAL_ID, new String[]{sensorTag},
                        range, resolutionTier, maxRecords)) {
                    return cursorAsScalarReadingList(fallbackCursor, maxRecords);

                }
            } else {
                return cursorAsScalarReadingList(cursor, maxRecords);
            }
        }
    }

    private ScalarReadingList cursorAsScalarReadingList(Cursor cursor, int maxRecords) {
        final int max = maxRecords <= 0 ? cursor.getCount() : maxRecords;
        final long[] readTimestamps = new long[max];
        final double[] readValues = new double[max];
        int i = 0;
        while (cursor.moveToNext()) {
            readTimestamps[i] = cursor.getLong(0);
            readValues[i] = cursor.getDouble(1);
            i++;
        }
        final int actualCount = i;
        return new ScalarReadingList() {
            @Override
            public void deliver(StreamConsumer c) {
                for (int i = 0; i < actualCount; i++) {
                    c.addData(readTimestamps[i], readValues[i]);
                }
            }

            @Override
            public int size() {
                return actualCount;
            }

            @Override
            public List<ChartData.DataPoint> asDataPoints() {
                List<ChartData.DataPoint> result = new ArrayList<>();
                for (int i = 0; i < actualCount; i++) {
                    result.add(new ChartData.DataPoint(readTimestamps[i], readValues[i]));
                }
                return result;
            }
        };
    }

    @Override
    public Observable<ScalarReading> createScalarObservable(String trialId, String[] sensorTags,
            final TimeRange range, int resolutionTier) {
        return createScalarObservable(trialId, sensorTags, range, resolutionTier, 500);
    }

    @VisibleForTesting
    Observable<ScalarReading> createScalarObservable(String trialId, String[] sensorTags,
            final TimeRange range, int resolutionTier,
            int pageSize) {
        return Observable.create(new ObservableOnSubscribe<ScalarReading>() {

            private long mLastTimeStampWritten = -1;

            @Override
            public void subscribe(ObservableEmitter<ScalarReading> observableEmitter)
                    throws Exception {
                // Start by opening the cursor.
                TimeRange searchRange = range;
                while (true) {
                    Cursor c = null;
                    try {
                        c = getCursor(trialId, sensorTags, searchRange, resolutionTier, pageSize);
                        if (c != null) {
                            int count = 0;
                            while (c.moveToNext()) {
                                long timeStamp = c.getLong(0);
                                String sensorTag = c.getString(2);
                                observableEmitter.onNext(new ScalarReading(
                                        timeStamp, c.getDouble(1), sensorTag));
                                mLastTimeStampWritten = timeStamp;
                                count++;
                            }
                            if (count == 0 || observableEmitter.isDisposed()) {
                                break;
                            }
                        }
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                    if (mLastTimeStampWritten >= range.getTimes().upperEndpoint()) {
                        break;
                    } else {
                        Range<Long> times = Range.openClosed(mLastTimeStampWritten,
                                range.getTimes().upperEndpoint());
                        searchRange = TimeRange.oldest(times);
                    }
                    if (observableEmitter.isDisposed()) {
                        break;
                    }
                }
                observableEmitter.onComplete();
            }
        });
    }

    private Cursor getCursor(String trialId, String[] sensorTags, TimeRange range,
            int resolutionTier, int maxRecords) {
        String[] columns = new String[]{
                ScalarSensorsTable.Column.TIMESTAMP_MILLIS, ScalarSensorsTable.Column.VALUE,
                ScalarSensorsTable.Column.TAG, ScalarSensorsTable.Column.TRIAL_ID};
        Pair<String, String[]> selectionAndArgs = getSelectionAndArgs(trialId, sensorTags,
                range, resolutionTier);
        String selection = selectionAndArgs.first;
        String[] selectionArgs = selectionAndArgs.second;
        String orderBy = ScalarSensorsTable.Column.TIMESTAMP_MILLIS + (range.getOrder().equals(
                TimeRange.ObservationOrder.OLDEST_FIRST) ? " ASC" : " DESC");
        String limit = maxRecords <= 0 ? null : String.valueOf(maxRecords);

        return mOpenHelper.getReadableDatabase().query(ScalarSensorsTable.NAME,
                columns, selection, selectionArgs, null, null, orderBy,
                limit);
    }

    @Override
    public GoosciScalarSensorData.ScalarSensorData getScalarReadingProtos(
            GoosciExperiment.Experiment experiment) {
        GoosciScalarSensorData.ScalarSensorData data =
                new GoosciScalarSensorData.ScalarSensorData();
        ArrayList<GoosciScalarSensorData.ScalarSensorDataDump> sensorDataList = new ArrayList<>();
        for (GoosciTrial.Trial trial : experiment.trials) {
            GoosciTrial.Range range = trial.recordingRange;
            // This protects against corrupted trials with invalid range end times.
            if (range.endMs > range.startMs) {
                TimeRange timeRange = TimeRange.oldest(Range.closed(range.startMs, range.endMs));
                for (GoosciSensorLayout.SensorLayout sensor : trial.sensorLayouts) {
                    String tag = sensor.sensorId;
                    sensorDataList.add(getScalarReadingSensorProtos(trial.trialId, tag, timeRange));
                }
            }
        }
        data.sensors = sensorDataList.toArray(
                GoosciScalarSensorData.ScalarSensorDataDump.emptyArray());
        return data;
    }

    // This method gets the protos for a single sensor/trialID combination in the given
    // TimeRange.
    public GoosciScalarSensorData.ScalarSensorDataDump getScalarReadingSensorProtos(
            String trialId, String sensorTag, TimeRange range) {

        Cursor cursor = getCursor(trialId, new String[]{sensorTag}, range, 0, 0);
        if (cursor.getCount() == 0) {
            // No results for the TrialId. Assume this is a pre-export trial, so query again
            // with the default trial id.
            cursor = getCursor(ScalarSensorsTable.DEFAULT_TRIAL_ID, new String[]{sensorTag}, range,
                    0, 0);
        }
        GoosciScalarSensorData.ScalarSensorDataDump sensor =
                new GoosciScalarSensorData.ScalarSensorDataDump();
        try {
            sensor.tag = sensorTag;
            sensor.trialId = trialId;
            ArrayList<GoosciScalarSensorData.ScalarSensorDataRow> rowsList = new ArrayList<>();
            while (cursor.moveToNext()) {
                GoosciScalarSensorData.ScalarSensorDataRow row =
                        new GoosciScalarSensorData.ScalarSensorDataRow();
                row.timestampMillis = cursor.getLong(0);
                row.value = cursor.getDouble(1);
                rowsList.add(row);
            }
            sensor.rows = rowsList.toArray(GoosciScalarSensorData.ScalarSensorDataRow.emptyArray());
        } finally {
            cursor.close();
        }
        return sensor;
    }


    // TODO: test
    @Override
    public String getFirstDatabaseTagAfter(long timestamp) {
        final String timestampString = String.valueOf(timestamp);
        final Cursor cursor = mOpenHelper.getReadableDatabase().query(ScalarSensorsTable.NAME,
                new String[]{ScalarSensorsTable.Column.TAG},
                ScalarSensorsTable.Column.TIMESTAMP_MILLIS + ">?", new String[]{timestampString},
                null, null, ScalarSensorsTable.Column.TIMESTAMP_MILLIS + " ASC", "1");
        try {
            if (cursor.moveToNext()) {
                return cursor.getString(0);
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    public void deleteScalarReadings(String trialId, String sensorTag, TimeRange range) {
        Pair<String, String[]> selectionAndArgs = getSelectionAndArgs(trialId,
                new String[]{sensorTag}, range, -1 /* delete all resolutions */);
        String selection = selectionAndArgs.first;
        String[] selectionArgs = selectionAndArgs.second;
        mOpenHelper.getWritableDatabase().delete(ScalarSensorsTable.NAME, selection, selectionArgs);
    }
}
