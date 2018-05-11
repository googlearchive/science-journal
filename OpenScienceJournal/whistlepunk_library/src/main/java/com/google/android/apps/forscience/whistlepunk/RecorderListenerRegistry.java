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

import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Routes information flowing back from the recorders to the listeners (if any) in the foreground
 * activity that are interested in those events.
 *
 * <p>Is itself a SensorStatusListener, and makes routing SensorObservers that can be handed to
 * recorders so that recorders don't have to care about UI elements coming and going.
 *
 * <p>These methods should all only be called on the service's main thread.
 */
public class RecorderListenerRegistry implements SensorStatusListener {
  private Multimap<String, ListenerSet> listeners = HashMultimap.create();

  private static class ListenerSet {
    public final String observerId;
    public final SensorStatusListener statusListener;
    public final SensorObserver observer;

    private ListenerSet(
        String observerId, SensorStatusListener statusListener, SensorObserver observer) {
      this.observerId = observerId;
      this.statusListener = statusListener;
      this.observer = observer;
    }
  }

  private Map<String, Integer> currentStatus = new HashMap<>();
  private Map<String, Boolean> currentErrors = new HashMap<>();
  private int observerCount = 0;

  @Override
  public void onSourceStatus(String id, @Status int status) {
    currentStatus.put(id, status);
    currentErrors.put(id, false);
    for (ListenerSet set : listeners.get(id)) {
      if (set.statusListener != null) {
        set.statusListener.onSourceStatus(id, status);
      }
    }
  }

  @Override
  public void onSourceError(String id, @Error int error, String errorMessage) {
    currentErrors.put(id, true);
    // Since onSourceError can call a disconnect and remove a listener, need to keep this safe
    // for concurrent modification by copying it elsewhere.
    List<ListenerSet> listenerList = new ArrayList<>();
    listenerList.addAll(listeners.get(id));
    for (ListenerSet set : listenerList) {
      if (set.statusListener != null) {
        set.statusListener.onSourceError(id, error, errorMessage);
      }
    }
  }

  public int getSourceStatus(String id) {
    if (currentStatus.containsKey(id)) {
      return currentStatus.get(id);
    }
    return STATUS_DISCONNECTED;
  }

  public boolean getSourceHasError(String id) {
    if (currentErrors.containsKey(id)) {
      return currentErrors.get(id);
    }
    return false;
  }

  public boolean isSourceConnectedWithoutError(String id) {
    return !getSourceHasError(id) && getSourceStatus(id) == SensorStatusListener.STATUS_CONNECTED;
  }

  public String putListeners(
      String sensorId, SensorObserver observer, SensorStatusListener listener) {
    String observerId = sensorId + (++observerCount);

    listeners.put(sensorId, new ListenerSet(observerId, listener, observer));

    Integer status = currentStatus.get(sensorId);
    if (status != null && listener != null) {
      listener.onSourceStatus(sensorId, status);
    }
    return observerId;
  }

  public void remove(String sensorId, String observerId) {
    Collection<ListenerSet> sensorListeners = listeners.get(sensorId);
    Iterator<ListenerSet> iterator = sensorListeners.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().observerId.equals(observerId)) {
        iterator.remove();
      }
    }
    if (sensorListeners.isEmpty()) {
      // Then we've just removed the last listener for this sensorID.
      // Remove the status and errors state too.
      if (currentErrors.containsKey(sensorId)) {
        currentErrors.remove(sensorId);
      }
    }
  }

  public int countListeners(String sensorId) {
    Collection<ListenerSet> sensorListeners = listeners.get(sensorId);
    if (sensorListeners == null) {
      return 0;
    }
    return sensorListeners.size();
  }

  /**
   * Returns a new SensorObserver that routes new data to the currently-active UI listener, if any,
   * for the given sensor.
   */
  public SensorObserver makeObserverForRecorder(final String sensorId) {
    return new SensorObserver() {
      @Override
      public void onNewData(long timestamp, Data bundle) {
        for (ListenerSet set : listeners.get(sensorId)) {
          set.observer.onNewData(timestamp, bundle);
        }
      }
    };
  }
}
