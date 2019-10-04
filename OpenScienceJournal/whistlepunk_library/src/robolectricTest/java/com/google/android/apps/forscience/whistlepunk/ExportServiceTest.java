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

package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link ExportService}. */
@RunWith(RobolectricTestRunner.class)
public class ExportServiceTest {
  @Test
  public void testSanitizeFileName_normal() {
    String experimentName = "Untitled Experiment";
    String runName = "Run 1";

    assertEquals(
        "Untitled Experiment Run 1",
        ExportService.sanitizeFilename(experimentName + " " + runName));
  }

  @Test
  public void testSanitizeFileName_evil() {
    String experimentName = "//Untitled Experiment";
    String runName = "Run 1";

    assertEquals(
        "__Untitled Experiment Run 1",
        ExportService.sanitizeFilename(experimentName + " " + runName));

    experimentName = "U.nti\tled Ex. periment";
    runName = "Run 10";

    assertEquals(
        "U.nti_led Ex. periment Run 10",
        ExportService.sanitizeFilename(experimentName + " " + runName));
  }

  @Test
  public void makeCSVFilenameWhenShort() {
    Trial trial = makeTrial("runTitle");
    String filename = ExportService.makeCSVExportFilename("experiment", trial.getRawTitle());
    assertEquals("experiment runTitle.csv", filename);
  }

  @Test
  public void makeCSVFilenameWhenTooLong() {
    String veryLongString = "012345678901234567890123456789012345678901234567890123456789";
    Trial trial = makeTrial("runTitle" + veryLongString);

    String filename =
        ExportService.makeCSVExportFilename("experiment" + veryLongString, trial.getRawTitle());
    assertEquals(80, filename.length());
    assertEquals(
        "experiment01234567890123456789018953cd53 runTitle0123456789012345678a7c9ddcd.csv",
        filename);
  }

  @Test
  public void makeSJFilenameWhenUnsanitized() {
    String experimentName = "/Untitled_Experiment/0/";
    String filename = ExportService.makeSJExportFilename(experimentName);
    assertEquals("_Untitled_Experiment_0_.sj", filename);
  }

  private Trial makeTrial(String runTitle) {
    GoosciTrial.Trial trial =
        GoosciTrial.Trial.newBuilder().setTrialId("runId").setTitle(runTitle).build();
    return Trial.fromTrial(trial);
  }
}
