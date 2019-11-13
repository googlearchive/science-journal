/*
 *  Copyright 2019 Google LLC. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.analytics;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Throwables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the TrackerConstants class. */
@RunWith(RobolectricTestRunner.class)
public class TrackerConstantsTest {

  @Test
  public void createLabelFromStackTrace() throws Exception {
    int minStackFrameCount = 1;
    while (true) {
      try {
        buildStackAndThrow(minStackFrameCount);
        fail("Should have thrown an exception!");
      } catch (Exception e) {
        assertThat(e).hasMessageThat().isEqualTo("test exception for buildStackAndThrow");
        String label = TrackerConstants.createLabelFromStackTrace(e);
        assertThat(label.length()).isAtMost(TrackerConstants.MAX_LABEL_LENGTH);

        // If we reached the point where createLabelFromStackTrace truncated the string to
        // MAX_LABEL_LENGTH, we are done testing.
        if (label.length() == TrackerConstants.MAX_LABEL_LENGTH
            && Throwables.getStackTraceAsString(e).length() > TrackerConstants.MAX_LABEL_LENGTH) {
          return;
        }
      }
      minStackFrameCount++;
    }
  }

  private void buildStackAndThrow(int minStackFrameCount) throws Exception {
    Exception e = new Exception("test exception for buildStackAndThrow");
    if (e.getStackTrace().length > minStackFrameCount) {
      throw e;
    } else {
      buildStackAndThrow(minStackFrameCount);
    }
  }
}
