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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;

class StubInteractionListener implements ExternalAxisController.InteractionListener {
  @Override
  public void onStartInteracting() {}

  @Override
  public void onStopInteracting() {}

  @Override
  public void onPan(double xMin, double xMax) {}

  @Override
  public void onZoom(long xMin, long xMax) {}

  @Override
  public void requestResetPinnedState() {}

  @Override
  public void requestResetZoom() {}
}
