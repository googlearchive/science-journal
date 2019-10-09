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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData.ScalarSensorDataDump;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SensorDatabaseTest {
  private static final String TEST_DATABASE_NAME = "test.db";

  @Test
  public void testAddScalarReading() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    long timestamp = Arbitrary.integer();
    double value = Arbitrary.doubleFloat();
    db.addScalarReading("id", "tag", 0, timestamp, value);
    List<ScalarReading> readings =
        ScalarReading.slurp(
            db.getScalarReadings(
                "id", "tag", TimeRange.oldest(Range.closed(timestamp - 1, timestamp + 1)), 0, 0));
    assertEquals(Arrays.asList(new ScalarReading(timestamp, value)), readings);
  }

  @Test
  public void testAddScalarReadingLimits() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 1, 1.0);
    db.addScalarReading("id", "tag", 0, 2, 2.0);
    db.addScalarReading("id", "tag", 0, 3, 3.0);
    int limit = 2;
    List<ScalarReading> readings =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.closed(0L, 4L)), 0, limit));
    assertEquals(Arrays.asList(new ScalarReading(1, 1.0), new ScalarReading(2, 2.0)), readings);
  }

  @Test
  public void testAddScalarReadingNoLimits() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 1, 1.0);
    db.addScalarReading("id", "tag", 0, 2, 2.0);
    db.addScalarReading("id", "tag", 0, 3, 3.0);
    List<ScalarReading> readings =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.<Long>all()), 0, 0));
    assertEquals(
        Arrays.asList(
            new ScalarReading(1, 1.0), new ScalarReading(2, 2.0), new ScalarReading(3, 3.0)),
        readings);
  }

  @Test
  public void testAddScalarReadingTags() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 1, 1.0);
    db.addScalarReading("id", "tag", 0, 2, 2.0);
    db.addScalarReading("id", "other", 0, 3, 3.0);
    List<ScalarReading> readings =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.closed(0L, 4L)), 0, 0));
    assertEquals(Arrays.asList(new ScalarReading(1, 1.0), new ScalarReading(2, 2.0)), readings);
  }

  @Test
  public void testAddScalarReadingLimitsNewestFirst() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 1, 1.0);
    db.addScalarReading("id", "tag", 0, 2, 2.0);
    db.addScalarReading("id", "tag", 0, 3, 3.0);
    int limit = 2;
    List<ScalarReading> readings =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.newest(Range.closed(0L, 4L)), 0, limit));
    assertEquals(Arrays.asList(new ScalarReading(3, 3.0), new ScalarReading(2, 2.0)), readings);
  }

  @Test
  public void testAddScalarReadingRange() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 1, 1.0);
    db.addScalarReading("id", "tag", 0, 2, 2.0);
    db.addScalarReading("id", "tag", 0, 3, 3.0);
    db.addScalarReading("id", "tag", 0, 4, 4.0);
    List<ScalarReading> readings =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.closedOpen(2L, 4L)), 0, 0));
    assertEquals(Arrays.asList(new ScalarReading(2, 2.0), new ScalarReading(3, 3.0)), readings);

    readings =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.open(2L, 4L)), 0, 0));
    assertEquals(Arrays.asList(new ScalarReading(3, 3.0)), readings);

    readings =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.openClosed(2L, 4L)), 0, 0));
    assertEquals(Arrays.asList(new ScalarReading(3, 3.0), new ScalarReading(4, 4.0)), readings);
  }

  @Test
  public void testTiers() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 0, 0.0);
    db.addScalarReading("id", "tag", 1, 1, 1.0);

    List<ScalarReading> tier0 =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.<Long>all()), 0, 0));
    assertEquals(Arrays.asList(new ScalarReading(0, 0.0)), tier0);

    List<ScalarReading> tier1 =
        ScalarReading.slurp(
            db.getScalarReadings("id", "tag", TimeRange.oldest(Range.<Long>all()), 1, 0));
    assertEquals(Arrays.asList(new ScalarReading(1, 1.0)), tier1);
  }

  @Test
  public void testFirstTagAfter() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tagBefore", 0, 1, 1.0);
    db.addScalarReading("id", "tagAfter", 0, 3, 2.0);
    assertEquals("tagAfter", db.getFirstDatabaseTagAfter(2));
  }

  @Test
  public void testFirstTagAfterWithMultipleAfters() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tagBefore", 0, 1, 1.0);
    db.addScalarReading("id", "tagAfter", 0, 3, 2.0);
    db.addScalarReading("id", "tagFurtherAfter", 0, 5, 3.0);
    assertEquals("id", "tagAfter", db.getFirstDatabaseTagAfter(2));
  }

  @Test
  public void testDeleteReadings() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 0, 0.0);
    db.addScalarReading("id", "tag", 0, 1, 1.0);
    db.addScalarReading("id", "tag", 0, 101, 2.0);
    db.addScalarReading("id", "tag", 0, 102, 2.0);
    db.addScalarReading("id", "tag", 0, 103, 2.0);
    db.addScalarReading("id", "tag2", 0, 0, 1.0);

    assertEquals(
        2, db.getScalarReadings("id", "tag", TimeRange.oldest(Range.closed(0L, 1L)), 0, 0).size());

    assertEquals(
        3,
        db.getScalarReadings("id", "tag", TimeRange.oldest(Range.closed(101L, 103L)), 0, 0).size());

    assertEquals(
        1, db.getScalarReadings("id", "tag2", TimeRange.oldest(Range.closed(0L, 1L)), 0, 0).size());

    // Delete first set of readings.
    db.deleteScalarReadings("id", "tag", TimeRange.newest(Range.closed(0L, 1L)));

    assertEquals(
        0, db.getScalarReadings("id", "tag", TimeRange.oldest(Range.closed(0L, 1L)), 0, 0).size());

    // Make sure other records for that tag are unaffected.
    assertEquals(
        3,
        db.getScalarReadings("id", "tag", TimeRange.oldest(Range.closed(101L, 103L)), 0, 0).size());

    // Make sure tag 2 is unaffected.
    assertEquals(
        1, db.getScalarReadings("id", "tag2", TimeRange.oldest(Range.closed(0L, 1L)), 0, 0).size());
  }

  @Test
  public void testObservable_oneSensor() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 0, 0.0);
    db.addScalarReading("id", "tag", 0, 1, 1.5);
    db.addScalarReading("id", "tag", 0, 101, 2.0);
    db.addScalarReading("id", "tag", 0, 102, 2.0);
    db.addScalarReading("id", "tag", 0, 103, 2.0);
    db.addScalarReading("id", "tag2", 0, 0, 1.0);

    TestObserver<ScalarReading> testObserver = new TestObserver<>();
    Observable<ScalarReading> obs =
        db.createScalarObservable(
            "id", new String[] {"tag"}, TimeRange.oldest(Range.closed(0L, 1L)), 0);
    obs.subscribe(testObserver);
    testObserver.assertNoErrors();
    testObserver.assertValues(new ScalarReading(0, 0.0, "tag"), new ScalarReading(1, 1.5, "tag"));
  }

  @Test
  public void testObservable_multipleSensors() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 0, 0.0);
    db.addScalarReading("id", "tag", 0, 3, 1.0);
    db.addScalarReading("id", "tag", 0, 101, 2.0);
    db.addScalarReading("id", "tag", 0, 102, 2.0);
    db.addScalarReading("id", "tag", 0, 103, 2.0);
    db.addScalarReading("id", "tag2", 0, 1, 3.0);
    db.addScalarReading("id", "tag2", 0, 2, 4.0);

    TestObserver<ScalarReading> testObserver = new TestObserver<>();
    Observable<ScalarReading> obs =
        db.createScalarObservable(
            "id", new String[] {"tag", "tag2"}, TimeRange.oldest(Range.closed(0L, 3L)), 0);
    obs.subscribe(testObserver);
    testObserver.assertNoErrors();
    testObserver.assertValues(
        new ScalarReading(0, 0.0, "tag"),
        new ScalarReading(1, 3.0, "tag2"),
        new ScalarReading(2, 4.0, "tag2"),
        new ScalarReading(3, 1.0, "tag"));
  }

  @Test
  public void testObservable_mutipleRuns() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    db.addScalarReading("id", "tag", 0, 0, 0.0);
    db.addScalarReading("id", "tag", 0, 1, 1.0);
    db.addScalarReading("id", "tag", 0, 2, 0.0);
    db.addScalarReading("id", "tag", 0, 3, 1.0);
    db.addScalarReading("id", "tag", 0, 101, 2.0);
    db.addScalarReading("id", "tag", 0, 102, 2.0);
    db.addScalarReading("id2", "tag", 0, 103, 2.0);
    db.addScalarReading("id2", "tag", 0, 1, 3.0);
    db.addScalarReading("id2", "tag", 0, 2, 4.0);

    TestObserver<ScalarReading> testObserver = new TestObserver<>();
    Observable<ScalarReading> obs =
        db.createScalarObservable(
            "id", new String[] {"tag"}, TimeRange.oldest(Range.closed(0L, 3L)), 0);
    obs.subscribe(testObserver);
    testObserver.assertNoErrors();
    testObserver.assertValues(
        new ScalarReading(0, 0.0, "tag"),
        new ScalarReading(1, 1.0, "tag"),
        new ScalarReading(2, 0.0, "tag"),
        new ScalarReading(3, 1.0, "tag"));
  }

  @Test
  public void testObservable_paging() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);

    int pageSize = 10;
    int total = pageSize * 10;
    List<ScalarReading> expected = Lists.newArrayList();
    for (int index = 0; index < total; ++index) {
      db.addScalarReading("id", "tag", 0, index, 0.0);
      expected.add(new ScalarReading(index, 0.0, "tag"));
    }

    TestObserver<ScalarReading> testObserver = new TestObserver<>();
    Observable<ScalarReading> obs =
        db.createScalarObservable(
            "id",
            new String[] {"tag"},
            TimeRange.oldest(Range.closed(0L, (long) total)),
            0,
            pageSize);
    obs.subscribe(testObserver);
    testObserver.assertNoErrors();
    testObserver.assertValueSequence(expected);
  }

  @Test
  public void testGetScalarReadingProtos() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    long timestamp = Arbitrary.integer();
    double value = Arbitrary.doubleFloat();

    GoosciExperiment.Experiment.Builder experiment = GoosciExperiment.Experiment.newBuilder();
    GoosciTrial.Trial.Builder trial = GoosciTrial.Trial.newBuilder();
    SensorLayout sensorLayout =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("foo").build();
    trial
        .setRecordingRange(
            com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range.newBuilder()
                .setStartMs(timestamp - 1)
                .setEndMs(timestamp + 3))
        .addSensorLayouts(sensorLayout);
    experiment.addTrials(trial);

    db.addScalarReading(trial.getTrialId(), "foo", 0, timestamp, value);
    db.addScalarReading(trial.getTrialId(), "foo", 1, timestamp + 1, value);
    db.addScalarReading(trial.getTrialId(), "foo", 0, timestamp + 2, value);
    db.addScalarReading(trial.getTrialId(), "bar", 0, timestamp + 2, value);
    db.addScalarReading(trial.getTrialId(), "foo", 0, timestamp + 4, value);

    GoosciScalarSensorData.ScalarSensorData data = db.getScalarReadingProtos(experiment.build());
    assertEquals("foo", data.getSensors(0).getTag());
    assertEquals(2, data.getSensors(0).getRowsCount());
    assertEquals(trial.getTrialId(), data.getSensors(0).getTrialId());
  }

  @Test
  public void testGetScalarReadingProtosDefaultTrialId() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    long timestamp = Arbitrary.integer();
    double value = Arbitrary.doubleFloat();

    GoosciExperiment.Experiment.Builder experiment = GoosciExperiment.Experiment.newBuilder();
    GoosciTrial.Trial.Builder trial = GoosciTrial.Trial.newBuilder();
    SensorLayout sensorLayout =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("foo").build();
    trial
        .setRecordingRange(
            com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range.newBuilder()
                .setStartMs(timestamp - 1)
                .setEndMs(timestamp + 3))
        .addSensorLayouts(sensorLayout);
    experiment.addTrials(trial);

    db.addScalarReading("0", "foo", 0, timestamp, value);
    db.addScalarReading("0", "foo", 1, timestamp + 1, value);
    db.addScalarReading("0", "foo", 0, timestamp + 2, value);
    db.addScalarReading("0", "bar", 0, timestamp + 2, value);
    db.addScalarReading("0", "foo", 0, timestamp + 4, value);

    GoosciScalarSensorData.ScalarSensorData data = db.getScalarReadingProtos(experiment.build());
    assertEquals("foo", data.getSensors(0).getTag());
    assertEquals(2, data.getSensors(0).getRowsCount());
  }

  @Test
  public void testGetScalarReadingSensorProtos() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    long timestamp = Arbitrary.integer();
    double value = Arbitrary.doubleFloat();
    db.addScalarReading("id", "foo", 0, timestamp, value);
    db.addScalarReading("id", "foo", 1, timestamp + 1, value);
    db.addScalarReading("id", "foo", 0, timestamp + 2, value);
    db.addScalarReading("id", "bar", 0, timestamp + 2, value);
    db.addScalarReading("id", "foo", 0, timestamp + 4, value);
    ScalarSensorDataDump sensor =
        db.getScalarReadingSensorProtos(
            "id", "foo", TimeRange.oldest(Range.closed(timestamp - 1, timestamp + 3)));
    assertEquals("foo", sensor.getTag());
    assertEquals(2, sensor.getRowsCount());
  }

  @Test
  public void testGetScalarReadingSensorProtosEmptyResult() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    long timestamp = Arbitrary.integer();
    double value = Arbitrary.doubleFloat();
    db.addScalarReading("id", "foo", 0, timestamp, value);
    db.addScalarReading("id", "foo", 1, timestamp + 1, value);
    db.addScalarReading("id", "foo", 0, timestamp + 2, value);
    db.addScalarReading("id", "bar", 0, timestamp + 2, value);
    db.addScalarReading("id", "foo", 0, timestamp + 4, value);
    ScalarSensorDataDump sensor =
        db.getScalarReadingSensorProtos(
            "id", "foobar", TimeRange.oldest(Range.closed(timestamp - 1, timestamp + 3)));
    assertEquals("foobar", sensor.getTag());
    assertEquals(0, sensor.getRowsCount());
  }

  @Test
  public void testGetScalarReadingProtosAsList() {
    SensorDatabaseImpl db =
        new SensorDatabaseImpl(getContext(), getAppAccount(), TEST_DATABASE_NAME);
    long timestamp = Arbitrary.integer();
    double value = Arbitrary.doubleFloat();

    GoosciExperiment.Experiment.Builder experiment = GoosciExperiment.Experiment.newBuilder();
    GoosciTrial.Trial.Builder trial = GoosciTrial.Trial.newBuilder();
    SensorLayout sensorLayout =
        GoosciSensorLayout.SensorLayout.newBuilder().setSensorId("foo").build();
    trial
        .setRecordingRange(
            com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range.newBuilder()
                .setStartMs(timestamp - 1)
                .setEndMs(timestamp + 3))
        .addSensorLayouts(sensorLayout);
    experiment.addTrials(trial);
    ;

    db.addScalarReading(trial.getTrialId(), "foo", 0, timestamp, value);
    db.addScalarReading(trial.getTrialId(), "foo", 1, timestamp + 1, value);
    db.addScalarReading(trial.getTrialId(), "foo", 0, timestamp + 2, value);
    db.addScalarReading(trial.getTrialId(), "bar", 0, timestamp + 2, value);
    db.addScalarReading(trial.getTrialId(), "foo", 0, timestamp + 4, value);

    List<ScalarSensorDataDump> data = db.getScalarReadingProtosAsList(experiment.build());
    assertEquals("foo", data.get(0).getTag());
    assertEquals(2, data.get(0).getRowsCount());
    assertEquals(trial.getTrialId(), data.get(0).getTrialId());
  }

  @Before
  public void setUp() throws Exception {
    File dbtest = getContext().getDatabasePath(TEST_DATABASE_NAME);
    if (dbtest.exists()) {
      dbtest.delete();
      fail("Found DB at " + dbtest + " that should have been cleaned up last time.");
    }
  }

  @After
  public void tearDown() throws Exception {
    getContext().getDatabasePath(TEST_DATABASE_NAME).delete();
  }

  private Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
