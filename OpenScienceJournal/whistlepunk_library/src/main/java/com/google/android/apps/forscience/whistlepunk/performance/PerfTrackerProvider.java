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

package com.google.android.apps.forscience.whistlepunk.performance;

import android.app.Application;
import android.content.Context;
import java.util.UUID;

public interface PerfTrackerProvider {

  PerfTrackerProvider STUB = new PerfTrackerProvider() {
    @Override
    public TimerToken startTimer() {
      return null;
    }

    @Override
    public void stopTimer(TimerToken token, String eventName) {
    }

    @Override
    public void startGlobalTimer(String eventName) {
    }

    @Override
    public void stopGlobalTimer(String eventName) {
    }

    @Override
    public void stopGlobalTimer(String eventName, String newEventName) {
    }

    @Override
    public boolean startPerfTrackerEventDebugActivity(Context context) {
      return false;
    }

    @Override
    public void startJankRecorder(String eventName) {
    }

    @Override
    public void stopJankRecorder(String eventName) {
    }

    @Override
    public void watchForMemoryLeak(Object object) {
    }

    @Override
    public void recordBatterySnapshotOnForegroundServiceStart() {
    }

    @Override
    public void recordBatterySnapshotOnForegroundServiceStop() {
    }

    @Override
    public void recordBatterySnapshot(String eventName) {
    }

    @Override
    public void onAppCreate(Application app) {
    }

    @Override
    public void onActivityInit() {
    }

    @Override
    public void onAppInteractive() {
    }
  };

  class TimerToken {
    private UUID uuid;

    public TimerToken() {
      uuid = UUID.randomUUID();
    }
  }

  /**
   * Start a local timer
   *
   * @return A TimerToken to pass to subsequent calls of {@link
   *     PerfTrackerProvider#stopTimer(TimerToken, String)}
   */
  TimerToken startTimer();

  /**
   * Stop a local timer and emit its duration
   *
   * @param token A TimerToken from a previous invocation of {@link
   *     PerfTrackerProvider#startTimer()}
   * @param eventName The name of the event associated with this timer
   */
  void stopTimer(TimerToken token, String eventName);

  /**
   * Start a global timer
   *
   * @param eventName The name of the event associated with this timer
   */
  void startGlobalTimer(String eventName);

  /**
   * Stop a global timer and emit its duration
   *
   * @param eventName The name of the event associated with this timer
   */
  void stopGlobalTimer(String eventName);

  /**
   * Stop a global timer with name eventName and emit its duration as newEventName
   *
   * @param eventName The name of the event associated with this timer
   * @param newEventName The name of the event the timer will be emitted as (replaces eventName)
   */
  void stopGlobalTimer(String eventName, String newEventName);

  /**
   * Start activity to show local database/debug data for events
   *
   * @return True if activity successfully shown
   */
  boolean startPerfTrackerEventDebugActivity(Context context);

  /**
   * Start recording framerate/jank
   *
   * @param eventName The name of the event associated with this recorder
   */
  void startJankRecorder(String eventName);

  /**
   * Stop recording framerate/jank
   *
   * @param eventName The name of the event associated with this recorder
   */
  void stopJankRecorder(String eventName);

  /**
   * Watch object for memory leak
   *
   * @param object
   */
  void watchForMemoryLeak(Object object);

  /** Record battery snapshot with hint that a foreground service is starting */
  void recordBatterySnapshotOnForegroundServiceStart();

  /** Record battery snapshot with hint that a foreground service is stopping */
  void recordBatterySnapshotOnForegroundServiceStop();

  /**
   * Record battery snapshot for specific event
   *
   * @param eventName
   */
  void recordBatterySnapshot(String eventName);

  /**
   * Record time at which application is first started for start-up latency
   *
   * @param app
   */
  void onAppCreate(Application app);

  /** Record time at which activity inits for start-up latency */
  void onActivityInit();

  /** Record time when user should be able to first interact with app for start-up latency */
  void onAppInteractive();
}
