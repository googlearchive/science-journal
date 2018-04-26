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

package com.google.android.apps.forscience.whistlepunk.feedback;

import com.google.android.apps.forscience.javalib.MaybeConsumer;

/** An object which can build up feedback to be sent for collection. */
public interface FeedbackProvider {
  FeedbackProvider STUB = onSuccess -> onSuccess.success(true);

  /**
   * Called when user is requesting a feedback report get created.
   *
   * @param onSuccess {@code true} if feedback was successfully sent,
   */
  void sendFeedback(MaybeConsumer<Boolean> onSuccess);
}
