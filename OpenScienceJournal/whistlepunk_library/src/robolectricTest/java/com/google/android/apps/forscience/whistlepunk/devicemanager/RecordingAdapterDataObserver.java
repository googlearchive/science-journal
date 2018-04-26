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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import static org.junit.Assert.assertEquals;

import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

class RecordingAdapterDataObserver extends RecyclerView.AdapterDataObserver {
  private List<String> mostRecentNotifications = new ArrayList<>();

  @Override
  public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
    mostRecentNotifications.add(
        0, String.format("Moved %s from %s to %s", itemCount, fromPosition, toPosition));
  }

  @Override
  public void onChanged() {
    mostRecentNotifications.add(0, String.format("Changed"));
  }

  @Override
  public void onItemRangeChanged(int positionStart, int itemCount) {
    mostRecentNotifications.add(0, String.format("Changed %s at %s", itemCount, positionStart));
  }

  @Override
  public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
    mostRecentNotifications.add(
        0, String.format("Changed %s at %s [%s]", itemCount, positionStart, payload));
  }

  @Override
  public void onItemRangeInserted(int positionStart, int itemCount) {
    mostRecentNotifications.add(0, String.format("Inserted %s at %s", itemCount, positionStart));
  }

  @Override
  public void onItemRangeRemoved(int positionStart, int itemCount) {
    mostRecentNotifications.add(0, String.format("Removed %s from %s", itemCount, positionStart));
  }

  void assertMostRecentNotification(String string) {
    assertEquals(mostRecentNotifications.toString(), string, mostRecentNotifications.get(0));
  }
}
