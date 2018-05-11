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

package com.google.android.apps.forscience.whistlepunk.review;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class ExportStreamConsumer implements StreamConsumer {
  private FailureListener failureListener;
  private OutputStreamWriter outputStreamWriter;
  private boolean startAtZero;
  private long firstTimeStampWritten = -1;
  private long lastTimeStampWritten = -1;

  /**
   * @param startAtZero if true, adjusts all timestamps so that the first timestamp is reported as
   *     0.
   */
  public ExportStreamConsumer(
      OutputStreamWriter outputStreamWriter, boolean startAtZero, FailureListener failureListener) {
    this.failureListener = failureListener;
    this.outputStreamWriter = outputStreamWriter;
    this.startAtZero = startAtZero;
  }

  @Override
  public boolean addData(final long timestampMillis, final double value) {
    if (firstTimeStampWritten < 0) {
      firstTimeStampWritten = timestampMillis;
    }
    try {
      if (outputStreamWriter == null) {
        failureListener.fail(new IllegalStateException("Output stream closed."));
        return false;
      }
      outputStreamWriter.write(getTimestampString(timestampMillis));
      outputStreamWriter.write(",");
      outputStreamWriter.write(Double.toString(value));
      outputStreamWriter.write("\n");
      lastTimeStampWritten = timestampMillis;

    } catch (IOException e) {
      failureListener.fail(e);
      return false;
    }
    return true;
  }

  private String getTimestampString(long time) {
    return Long.toString(startAtZero ? time - firstTimeStampWritten : time);
  }

  public long getLastTimeStampWritten() {
    return lastTimeStampWritten;
  }
}
