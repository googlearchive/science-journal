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
import com.google.android.apps.forscience.whistlepunk.FakeAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for Trials */
@RunWith(RobolectricTestRunner.class)
public class TrialTest {
  @Test
  public void testTrialWithLabels() throws Exception {
    GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
    trialProto.labels = new GoosciLabel.Label[1];
    GoosciLabel.Label labelProto = new GoosciLabel.Label();
    labelProto.labelId = "labelId";
    labelProto.timestampMs = 1;
    trialProto.labels[0] = labelProto;
    Trial trial = Trial.fromTrial(trialProto);

    assertEquals(trial.getLabelCount(), 1);

    Label label = trial.getLabels().get(0);
    label.setTimestamp(10);
    trial.updateLabel(label);
    assertEquals(trial.getLabels().get(0).getTimeStamp(), 10);

    Label second = Label.newLabel(20, GoosciLabel.Label.ValueType.TEXT);
    trial.addLabel(second);
    assertEquals(trial.getLabelCount(), 2);

    trial.deleteLabelAndReturnAssetDeleter(label, "experimentId").accept(getContext());
    assertEquals(trial.getLabelCount(), 1);
  }

  @Test
  public void testLabelsStillSortedOnAdd() {
    Trial trial =
        Trial.newTrial(
            10, new GoosciSensorLayout.SensorLayout[0], new FakeAppearanceProvider(), getContext());
    trial.addLabel(Label.newLabel(20, GoosciLabel.Label.ValueType.TEXT));
    trial.addLabel(Label.newLabel(30, GoosciLabel.Label.ValueType.TEXT));
    trial.addLabel(Label.newLabel(10, GoosciLabel.Label.ValueType.TEXT));
    assertEquals(trial.getLabels().size(), 3);
    assertEquals(trial.getLabels().get(0).getTimeStamp(), 10);
    assertEquals(trial.getLabels().get(1).getTimeStamp(), 20);
    assertEquals(trial.getLabels().get(2).getTimeStamp(), 30);
  }

  @Test
  public void testLabelsStillSortedOnUpdate() {
    Trial trial =
        Trial.newTrial(
            10, new GoosciSensorLayout.SensorLayout[0], new FakeAppearanceProvider(), getContext());
    trial.addLabel(Label.newLabel(10, GoosciLabel.Label.ValueType.TEXT));
    trial.addLabel(Label.newLabel(20, GoosciLabel.Label.ValueType.TEXT));
    trial.addLabel(Label.newLabel(30, GoosciLabel.Label.ValueType.TEXT));
    Label second = trial.getLabels().get(1);
    second.setTimestamp(40);
    trial.updateLabel(second);
    assertEquals(trial.getLabels().get(2).getTimeStamp(), 40);
  }

  private Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }
}
