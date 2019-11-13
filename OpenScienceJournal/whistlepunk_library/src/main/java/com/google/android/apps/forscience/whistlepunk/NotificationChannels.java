/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

/**
 * Channel Names for notifications. Android docs say that this value can be truncated if it is "too
 * long", but does not specify a limit. These values are namespaced to our package.
 */
public class NotificationChannels {
  public static final String NOTIFICATION_CHANNEL = "science_journal_notification_channel";
  public static final String SAVE_TO_DEVICE_CHANNEL = "science_journal_save_to_device_channel";
}
