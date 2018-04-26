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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

/**
 * Mostly for testing's sake, there's an onion's worth of layers here.
 *
 * <p>ScalarInputDiscoverer outsources the actual construction of ISensorDiscoverers to this
 * interface, so that we can run automated tests against ScalarInputDiscoverer without having to
 * guarantee that particular apps are actually installed on the test device.
 */
public interface AppDiscoveryCallbacks {
  // Called with each service found
  public void onServiceFound(String serviceId, ISensorDiscoverer service);

  // Called after all services have been found
  public void onDiscoveryDone();
}
