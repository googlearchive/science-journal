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

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** An object listening for sensor events. */
public interface SensorStatusListener {
  int STATUS_DISCONNECTED = 0;
  int STATUS_CONNECTING = 1;
  int STATUS_CONNECTED = 2;

  @IntDef({STATUS_CONNECTED, STATUS_CONNECTING, STATUS_DISCONNECTED})
  @Retention(RetentionPolicy.SOURCE)
  @interface Status {}

  /**
   * Called when the status of the source changes.
   *
   * @param id ID of the source.
   * @param status one of the {@link Status} values.
   */
  void onSourceStatus(String id, @Status int status);

  int ERROR_UNKNOWN = 0;
  int ERROR_FAILED_TO_CONNECT = 1;
  int ERROR_INVALID_PROTO = 2;

  @IntDef({ERROR_UNKNOWN, ERROR_FAILED_TO_CONNECT, ERROR_INVALID_PROTO})
  @Retention(RetentionPolicy.SOURCE)
  @interface Error {}

  /**
   * Called if there was an error in the source.
   *
   * @param id ID of the source.
   * @param error one of the {@link Error} values.
   * @param errorMessage human readable error message which will be displayed to the user
   */
  void onSourceError(String id, @Error int error, String errorMessage);
}
