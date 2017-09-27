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

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.ArrayMap;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.IRecorderController;
import com.google.android.apps.forscience.whistlepunk.wireapi.IRecordingStateListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.wireapi.ISensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.apps.forscience.whistlepunk.wireapi.TransportableSensorOptions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.reactivex.disposables.Disposable;

public class ProxyRecorderController extends IRecorderController.Stub {
    public static interface BindingPolicy {
        public void checkBinderAllowed();
    }

    private final BindingPolicy mBindingPolicy;
    private final RecorderController mDelegate;
    private final FailureListener mFailureListener;
    private SensorRegistry mRegistry;
    private String mSensorId = null;
    private String mObserverId = null;
    private Map<String, Disposable> mRecordListeners = new ArrayMap<>();

    public ProxyRecorderController(RecorderController delegate, BindingPolicy bindingPolicy,
            FailureListener failureListener, SensorRegistry registry) {
        mBindingPolicy = bindingPolicy;
        mDelegate = delegate;
        mFailureListener = failureListener;
        mRegistry = registry;
    }

    @Override
    public void startObserving(String sensorId, ISensorObserver observer,
            ISensorStatusListener listener, TransportableSensorOptions initialOptions)
            throws RemoteException {
        mBindingPolicy.checkBinderAllowed();

        if (mObserverId != null) {
            throw new IllegalStateException("Already observing one sensor!");
        }
        mSensorId = sensorId;
        // TODO: enable more than one observer through the service
        mObserverId = mDelegate.startObserving(sensorId, Collections.EMPTY_LIST,
                proxyObserver(observer), proxyStatusListener(listener, mFailureListener), 
                initialOptions, mRegistry);
    }

    @Override
    public void stopObserving(String sensorId) {
        if (!sensorId.equals(mSensorId)) {
            throw new IllegalArgumentException("Didn't start this sensor!");
        }
        mDelegate.stopObserving(sensorId, mObserverId);
        mObserverId = null;
    }

    private SensorObserver proxyObserver(final ISensorObserver observer) {
        return new SensorObserver() {
            boolean mValid = true;

            @Override
            public void onNewData(long timestamp, Data data) {
                if (!mValid) {
                    return;
                }
                try {
                    observer.onNewData(timestamp, data.asBundle());
                } catch (RemoteException e) {
                    mFailureListener.fail(e);
                    mValid = false;
                }
            }
        };
    }

    // TODO: implement the rest

    @Override
    public void pauseObservingAll() throws RemoteException {

    }

    @Override
    public void resumeObservingAll() throws RemoteException {

    }

    @Override
    public List<String> getObservedSensorIds() throws RemoteException {
        return null;
    }

    @Override
    public List<String> getMostRecentObservedSensorIds() throws RemoteException {
        return mDelegate.getMostRecentObservedSensorIds();
    }

    @Override
    public void applyOptions(String sensorId, TransportableSensorOptions settings)
            throws RemoteException {

    }

    @Override
    public void startRecording(Intent resumeIntent, RecordingMetadata recording)
            throws RemoteException {

    }

    @Override
    public void stopRecording() throws RemoteException {

    }

    @Override
    public RecordingMetadata getCurrentRecording() throws RemoteException {
        return null;
    }

    @Override
    public void addRecordingStateListener(String listenerId, IRecordingStateListener listener)
            throws RemoteException {
        if (mRecordListeners.containsKey(listenerId)) {
            throw new IllegalStateException("Already listening to id: " + listenerId);
        }
        mRecordListeners.put(listenerId,
                mDelegate.watchRecordingStatus().subscribe(currentRecording -> {
                    try {
                        listener.onRecordingStateChanged(currentRecording.isRecording());
                    } catch (RemoteException e) {
                        mFailureListener.fail(e);
                    }
                }));
        mDelegate.addObservedIdsListener(listenerId,
                proxyObservedIdsListener(listener, mFailureListener));
    }

    private RecorderController.ObservedIdsListener proxyObservedIdsListener(
            final IRecordingStateListener listener,
            final FailureListener failureListener) {
        return new RecorderController.ObservedIdsListener() {
            @Override
            public void onObservedIdsChanged(List<String> observedSensorIds) {
                try {
                    listener.onObservedIdsChanged(observedSensorIds);
                } catch (RemoteException e) {
                    failureListener.fail(e);
                }
            }
        };
    }

    @Override
    public void removeRecordingStateListener(String listenerId) throws RemoteException {
        if (mRecordListeners.containsKey(listenerId)) {
            mRecordListeners.get(listenerId).dispose();
            mRecordListeners.remove(listenerId);
        }
        mDelegate.removeObservedIdsListener(listenerId);
    }

    private static SensorStatusListener proxyStatusListener(final ISensorStatusListener listener,
            final FailureListener failureListener) {
        return new SensorStatusListener() {
            @Override
            public void onSourceStatus(String id, @Status int status) {
                try {
                    listener.onSourceStatus(id, status);
                } catch (RemoteException e) {
                    failureListener.fail(e);
                }
            }

            @Override
            public void onSourceError(String id, @Error int error, String errorMessage) {
                try {
                    listener.onSourceError(id, error, errorMessage);
                } catch (RemoteException e) {
                    failureListener.fail(e);
                }
            }
        };
    }
}
