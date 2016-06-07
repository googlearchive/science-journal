package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.FallibleConsumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.junit.Test;

import java.util.Arrays;

public class ProxyRecorderControllerTest {
    private static final TransportableSensorOptions BLANK_OPTIONS =
            new TransportableSensorOptions(Maps.<String, String>newHashMap());
    private final AlwaysAllowedPolicy mPolicy = new AlwaysAllowedPolicy();
    private final FailureListener mFailureListener =
            new ExplodingFactory().makeListenerForOperation("test");
    private final RecordingSensorObserver mObserver = new RecordingSensorObserver();
    private final RecordingStatusListener mStatusListener = new RecordingStatusListener();
    private SensorRegistry mRegistry = new AllSensorsRegistry();
    private final MemorySensorEnvironment mEnvironment = new MemorySensorEnvironment(
            new InMemorySensorDatabase().makeSimpleRecordingController(new MemoryMetadataManager()),
            new FakeBleClient(null), new MemorySensorHistoryStorage());

    @Test
    public void mostRecentSensorIds() throws RemoteException {
        final String id1 = "id1";
        final String id2 = "id2";
        RecorderControllerImpl rc = makeRecorderController();
        ProxyRecorderController prc = new ProxyRecorderController(rc, mPolicy, mFailureListener);

        // Blank only at the beginning of time
        assertEquals(Lists.newArrayList(), prc.getMostRecentObservedSensorIds());
        String observerId1 = rc.startObserving(id1, mObserver, mStatusListener, BLANK_OPTIONS);
        assertEquals(Lists.newArrayList(id1), prc.getMostRecentObservedSensorIds());
        String observerId2 = rc.startObserving(id2, mObserver, mStatusListener, BLANK_OPTIONS);
        assertEquals(Lists.newArrayList(id1, id2), prc.getMostRecentObservedSensorIds());

        // Pausing doesn't clear most recent list
        String pauseId = rc.pauseObservingAll();
        assertEquals(Lists.newArrayList(id1, id2), prc.getMostRecentObservedSensorIds());
        rc.resumeObservingAll(pauseId);
        assertEquals(Lists.newArrayList(id1, id2), prc.getMostRecentObservedSensorIds());

        // Stopping one of several does change list
        rc.stopObserving(id1, observerId1);
        assertEquals(Lists.newArrayList(id2), prc.getMostRecentObservedSensorIds());

        // But stopping the last leaves it in the list
        rc.stopObserving(id2, observerId2);
        assertEquals(Lists.newArrayList(id2), prc.getMostRecentObservedSensorIds());

        RecorderControllerImpl rc2 = makeRecorderController();
        ProxyRecorderController prc2 = new ProxyRecorderController(rc2, mPolicy, mFailureListener);
        assertEquals(Lists.newArrayList(id2), prc2.getMostRecentObservedSensorIds());
    }

    @Test
    public void gettingRecentIdsThroughListener() throws RemoteException {
        final String id1 = "id1";
        final String id2 = "id2";
        RecorderControllerImpl rc = makeRecorderController();

        ProxyRecorderController prc = new ProxyRecorderController(rc, mPolicy, mFailureListener);

        RecordingStateListener stateListener = new RecordingStateListener();
        prc.addRecordingStateListener("listenerId", stateListener);
        assertEquals(Lists.newArrayList(), stateListener.recentObservedSensorIds);
        String observerId1 = rc.startObserving(id1, mObserver, mStatusListener, BLANK_OPTIONS);
        assertEquals(Lists.newArrayList(id1), stateListener.recentObservedSensorIds);
        String observerId2 = rc.startObserving(id2, mObserver, mStatusListener, BLANK_OPTIONS);
        assertEquals(Lists.newArrayList(id1, id2), stateListener.recentObservedSensorIds);

        // Pausing doesn't clear most recent list
        String pauseId = rc.pauseObservingAll();
        assertEquals(Lists.newArrayList(id1, id2), stateListener.recentObservedSensorIds);
        rc.resumeObservingAll(pauseId);
        assertEquals(Lists.newArrayList(id1, id2), stateListener.recentObservedSensorIds);

        // Stopping one of several does change list
        rc.stopObserving(id1, observerId1);
        assertEquals(Lists.newArrayList(id2), stateListener.recentObservedSensorIds);

        // But stopping the last removes from the list
        rc.stopObserving(id2, observerId2);
        assertEquals(Lists.newArrayList(), stateListener.recentObservedSensorIds);

        rc.startObserving(id1, mObserver, mStatusListener, BLANK_OPTIONS);
        assertEquals(Lists.newArrayList(id1), stateListener.recentObservedSensorIds);

        // Connecting another proxy finds the same value
        ProxyRecorderController prc2 = new ProxyRecorderController(rc, mPolicy, mFailureListener);

        RecordingStateListener stateListener2 = new RecordingStateListener();
        prc2.addRecordingStateListener("listenerId2", stateListener2);
        assertEquals(Lists.newArrayList(id1), stateListener2.recentObservedSensorIds);
    }

    @Test
    public void preventMultipleListenersSameId() throws RemoteException {
        RecorderControllerImpl rc = makeRecorderController();

        ProxyRecorderController prc = new ProxyRecorderController(rc, mPolicy, mFailureListener);

        prc.addRecordingStateListener("id1", new RecordingStateListener());
        try {
            prc.addRecordingStateListener("id1", new RecordingStateListener());
            fail("Should have thrown, because only one listener at a time right now!");
        } catch (IllegalStateException expected) {
            // success!
        }
    }

    @Test
    public void addAndRemoveListeners() throws RemoteException {
        RecorderControllerImpl rc = makeRecorderController();
        ProxyRecorderController prc = new ProxyRecorderController(rc, mPolicy, mFailureListener);

        RecordingStateListener stateListener = new RecordingStateListener();
        prc.addRecordingStateListener("listenerId", stateListener);
        assertEquals(Lists.newArrayList(), stateListener.recentObservedSensorIds);
        String observerId1 = rc.startObserving("id1", mObserver, mStatusListener, BLANK_OPTIONS);
        assertEquals(Arrays.asList("id1"), stateListener.recentObservedSensorIds);

        assertFalse(stateListener.recentIsRecording);
        rc.startRecording(null, new RecordingMetadata(0, "runId", "experimentName"));
        assertTrue(stateListener.recentIsRecording);
        rc.stopRecording();
        assertFalse(stateListener.recentIsRecording);
        prc.removeRecordingStateListener("listenerId");
        assertFalse(stateListener.recentIsRecording);
        rc.startRecording(null, new RecordingMetadata(0, "runId", "experimentName"));
        // Still false, because we've stopped listening
        assertFalse(stateListener.recentIsRecording);
        rc.stopRecording();

        rc.stopObserving("id1", observerId1);

        // Still stale, because we've stopped listening
        assertEquals(Arrays.asList("id1"), stateListener.recentObservedSensorIds);
    }

    @NonNull
    private RecorderControllerImpl makeRecorderController() {
        return new RecorderControllerImpl(null, mRegistry, mEnvironment) {
            @Override
            protected void withBoundRecorderService(FallibleConsumer<RecorderService> c) {
                // do nothing
            }
        };
    }
}