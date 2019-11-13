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

package com.google.android.apps.forscience.whistlepunk.wireapi;

import android.os.Parcel;
import android.os.Parcelable;

public class RecordingMetadata implements Parcelable {
  /** "Timestamp" that indicates that recording has not started. */
  public static final int NOT_RECORDING = -1;

  protected RecordingMetadata(Parcel in) {
    startTime = in.readLong();
    runId = in.readString();
    experimentName = in.readString();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(startTime);
    dest.writeString(runId);
    dest.writeString(experimentName);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<RecordingMetadata> CREATOR =
      new Creator<RecordingMetadata>() {
        @Override
        public RecordingMetadata createFromParcel(Parcel in) {
          return new RecordingMetadata(in);
        }

        @Override
        public RecordingMetadata[] newArray(int size) {
          return new RecordingMetadata[size];
        }
      };

  public static long getStartTime(RecordingMetadata recording) {
    return recording != null ? recording.getStartTime() : NOT_RECORDING;
  }

  private long startTime;
  private String runId;
  private String experimentName;

  public RecordingMetadata(long startTime, String runId, String experimentName) {
    this.startTime = startTime;
    this.runId = runId;
    this.experimentName = experimentName;
  }

  public long getStartTime() {
    return startTime;
  }

  public String getRunId() {
    return runId;
  }

  public String getExperimentName() {
    return experimentName;
  }

  @Override
  public String toString() {
    return "RecordingMetadata{"
        + "mStartTime="
        + startTime
        + ", mRunId='"
        + runId
        + '\''
        + ", mExperimentName='"
        + experimentName
        + '\''
        + '}';
  }
}
