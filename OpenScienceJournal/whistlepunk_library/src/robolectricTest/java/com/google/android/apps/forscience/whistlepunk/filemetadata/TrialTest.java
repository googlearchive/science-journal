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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.ExperimentCreator;
import com.google.android.apps.forscience.whistlepunk.FakeAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for Trials */
@RunWith(RobolectricTestRunner.class)
public class TrialTest {
  @Test
  public void testTrialWithLabels() throws Exception {
    GoosciTrial.Trial.Builder trialProto = GoosciTrial.Trial.newBuilder();
    GoosciLabel.Label.Builder labelProto =
        GoosciLabel.Label.newBuilder().setLabelId("labelId").setTimestampMs(1);
    trialProto.addLabels(labelProto);
    Experiment experiment = ExperimentCreator.newExperimentForTesting(1, "id", 1);
    Trial trial = Trial.fromTrial(trialProto.build());
    experiment.addTrial(trial);

    assertEquals(1, trial.getLabelCount());

    Label label = trial.getLabels().get(0);
    label.setTimestamp(10);
    trial.updateLabel(label);
    assertEquals(10, trial.getLabels().get(0).getTimeStamp());

    Label second = Label.newLabel(20, ValueType.TEXT);
    trial.addLabel(second);
    assertEquals(2, trial.getLabelCount());

    trial.deleteLabelAndReturnAssetDeleter(experiment, label, getAppAccount()).accept(getContext());
    assertEquals(1, trial.getLabelCount());
  }

  @Test
  public void testLabelsStillSortedOnAdd() {
    Trial trial =
        Trial.newTrial(
            10, new GoosciSensorLayout.SensorLayout[0], new FakeAppearanceProvider(), getContext());
    trial.addLabel(Label.newLabel(20, ValueType.TEXT));
    trial.addLabel(Label.newLabel(30, ValueType.TEXT));
    trial.addLabel(Label.newLabel(10, ValueType.TEXT));
    assertEquals(3, trial.getLabels().size());
    assertEquals(10, trial.getLabels().get(0).getTimeStamp());
    assertEquals(20, trial.getLabels().get(1).getTimeStamp());
    assertEquals(30, trial.getLabels().get(2).getTimeStamp());
  }

  @Test
  public void testLabelsStillSortedOnUpdate() {
    Trial trial =
        Trial.newTrial(
            10, new GoosciSensorLayout.SensorLayout[0], new FakeAppearanceProvider(), getContext());
    trial.addLabel(Label.newLabel(10, ValueType.TEXT));
    trial.addLabel(Label.newLabel(20, ValueType.TEXT));
    trial.addLabel(Label.newLabel(30, ValueType.TEXT));
    Label second = trial.getLabels().get(1);
    second.setTimestamp(40);
    trial.updateLabel(second);
    assertEquals(40, trial.getLabels().get(2).getTimeStamp());
  }

  private static Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private static AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
