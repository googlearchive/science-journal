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
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.FakeAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.protobuf.nano.MessageNano;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

/**
 * Tests for the Experiment class which involve labels, which use Parcelable.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class ExperimentTest {

    private GoosciExperiment.Experiment makeExperimentWithLabels(long[] labelTimes) {
        GoosciExperiment.Experiment result = new GoosciExperiment.Experiment();
        result.labels = new GoosciLabel.Label[labelTimes.length];
        for (int i = 0; i < labelTimes.length; i++) {
            Label label = Label.newLabel(labelTimes[i], GoosciLabel.Label.TEXT);
            result.labels[i] = label.getLabelProto();
        }
        return result;
    }

    @Test
    public void testLabels() {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
        GoosciUserMetadata.ExperimentOverview overview = new GoosciUserMetadata
                .ExperimentOverview();

        // No labels on creation
        Experiment experiment = Experiment.fromExperiment(proto, overview);
        assertEquals(experiment.getLabelCount(), 0);
        assertEquals(experiment.getLabels(), Collections.emptyList());

        // Add a label manually, outside of the proto
        GoosciPictureLabelValue.PictureLabelValue labelValueProto = new GoosciPictureLabelValue
                .PictureLabelValue();
        GoosciLabel.Label labelProto = new GoosciLabel.Label();
        labelProto.protoData = MessageNano.toByteArray(labelValueProto);
        labelProto.type = GoosciLabel.Label.PICTURE;
        experiment.getLabels().add(Label.fromLabel(labelProto));
        assertEquals(experiment.getLabelCount(), 1);

        // Make sure the proto gets updated properly
        proto.labels = new GoosciLabel.Label[1];
        proto.labels[0] = labelProto;
        assertTrue(MessageNano.messageNanoEquals(experiment.getExperimentProto(), proto));

        // Try constructing an experiment from a proto that already has these fields.
        Experiment experiment2 = Experiment.fromExperiment(proto, overview);
        assertTrue(MessageNano.messageNanoEquals(
                experiment2.getLabels().get(0).getPictureLabelValue(), labelValueProto));
        assertEquals(experiment2.getLabelCount(), 1);
        List<Label> labels = experiment2.getLabels();
        labels.add(Label.newLabel(20, GoosciLabel.Label.TEXT));
        assertEquals(experiment2.getLabelCount(), 2);

        assertEquals(experiment2.getExperimentProto().labels.length, 2);
    }

    @Test
    public void testGetLabelsForRange() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{99, 100, 125, 201});
        Experiment experiment = Experiment.fromExperiment(proto,
                new GoosciUserMetadata.ExperimentOverview());

        GoosciTrial.Range range = new GoosciTrial.Range();
        range.startMs = 0;
        range.endMs = 10;
        assertEquals(experiment.getLabelsForRange(range), Collections.<Label>emptyList());

        range.endMs = 200;
        assertEquals(experiment.getLabelsForRange(range).size(), 3);

        range.endMs = 300;
        assertEquals(experiment.getLabelsForRange(range).size(), 4);

        range.startMs = 100;
        assertEquals(experiment.getLabelsForRange(range).size(), 3);
    }

    @Test
    public void testTrials() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{});

        // No trials on creation
        Experiment experiment = Experiment.fromExperiment(proto,
                new GoosciUserMetadata.ExperimentOverview());
        assertEquals(experiment.getTrialCount(), 0);
        assertEquals(experiment.getTrials(), Collections.emptyList());

        // Trials on creation that overlap with notes should get those notes added properly.
        GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
        trialProto.title = "cats";
        GoosciTrial.Range range = new GoosciTrial.Range();
        range.startMs = 100;
        range.endMs = 200;
        trialProto.recordingRange = range;
        proto.trials = new GoosciTrial.Trial[1];
        proto.trials[0] = trialProto;

        Experiment experiment1 = Experiment.fromExperiment(proto,
                new GoosciUserMetadata.ExperimentOverview());
        assertEquals(experiment1.getTrialCount(), 1);

        // Adding a new trial should work as expected.
        GoosciTrial.Trial trialProto2 = new GoosciTrial.Trial();
        trialProto2.title = "more cats";
        GoosciTrial.Range range2 = new GoosciTrial.Range();
        range.startMs = 200;
        range.endMs = 500;
        trialProto2.recordingRange = range2;
        Trial trial2 = Trial.fromTrial(trialProto2);
        experiment1.getTrials().add(trial2);

        assertEquals(experiment1.getTrialCount(), 2);

        // Getting the proto includes trial updates
        assertEquals(experiment1.getExperimentProto().trials.length, 2);
    }

    @Test
    public void testGetTrialsWithFilters() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{});
        Experiment experiment = Experiment.fromExperiment(proto,
                new GoosciUserMetadata.ExperimentOverview());

        // New trials are invalid -- no end time.
        GoosciSensorLayout.SensorLayout[] noLayouts = new GoosciSensorLayout.SensorLayout[0];
        experiment.addTrial(
                Trial.newTrial(10, noLayouts, new FakeAppearanceProvider(), getContext()));
        experiment.addTrial(
                Trial.newTrial(20, noLayouts, new FakeAppearanceProvider(), getContext()));

        assertEquals(experiment.getTrials().size(), 2);
        assertEquals(experiment.getTrials(true,  /* include invalid */ true).size(), 2);
        assertEquals(experiment.getTrials(false, true).size(), 2);
        assertEquals(experiment.getTrials(true, false).size(), 0);

        GoosciTrial.Trial validProto = new GoosciTrial.Trial();
        GoosciTrial.Range range = new GoosciTrial.Range();
        range.startMs = 100;
        range.endMs = 200;
        validProto.recordingRange = range;
        validProto.trialId = "valid";
        Trial valid = Trial.fromTrial(validProto);
        experiment.addTrial(valid);

        assertEquals(experiment.getTrials(false, true).size(), 3);
        assertEquals(experiment.getTrials(true, false).size(), 1);
        assertEquals(experiment.getTrials(false, false).size(), 1);

        GoosciTrial.Trial archivedProto = new GoosciTrial.Trial();
        GoosciTrial.Range archivedRange = new GoosciTrial.Range();
        archivedRange.startMs = 300;
        archivedRange.endMs = 400;
        archivedProto.recordingRange = archivedRange;
        archivedProto.archived = true;
        archivedProto.trialId = "archived";
        Trial archived = Trial.fromTrial(archivedProto);
        experiment.addTrial(archived);

        assertEquals(experiment.getTrials(true, true).size(), 4);
        assertEquals(experiment.getTrials(/* include archived */ false, true).size(), 3);
        assertEquals(experiment.getTrials(true, false).size(), 2);
        assertEquals(experiment.getTrials(false, false).size(), 1);

    }

    @Test
    public void testUpdatesProtoOnlyWhenNeeded() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{99, 100, 125, 201});
        GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
        trialProto.title = "title";
        proto.trials = new GoosciTrial.Trial[]{trialProto};

        Experiment experiment = Experiment.fromExperiment(proto,
                new GoosciUserMetadata.ExperimentOverview());

        // Try to get the proto *before* converting the objects into lists.
        GoosciExperiment.Experiment result = experiment.getExperimentProto();
        assertEquals(result.labels.length, 4);
        assertEquals(result.trials.length, 1);
    }

    @Test
    public void testTrialNameOrdering() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{});
        Experiment experiment = Experiment.fromExperiment(proto,
                new GoosciUserMetadata.ExperimentOverview());
        GoosciTrial.Trial trialProto1 = new GoosciTrial.Trial();
        GoosciTrial.Trial trialProto2 = new GoosciTrial.Trial();
        GoosciTrial.Trial trialProto3 = new GoosciTrial.Trial();
        trialProto1.trialId = "trial1";
        trialProto2.trialId = "trial2";
        trialProto3.trialId = "trial3";
        GoosciTrial.Range range1 = new GoosciTrial.Range();
        range1.startMs = 0;
        trialProto1.recordingRange = range1;
        GoosciTrial.Range range2 = new GoosciTrial.Range();
        range2.startMs = 10;
        trialProto2.recordingRange = range2;
        GoosciTrial.Range range3 = new GoosciTrial.Range();
        range3.startMs = 20;
        trialProto3.recordingRange = range3;
        Trial trial1 = Trial.fromTrial(trialProto1);
        Trial trial2 = Trial.fromTrial(trialProto2);
        Trial trial3 = Trial.fromTrial(trialProto3);

        Context context = getContext();
        experiment.addTrial(trial1);
        assertEquals("Recording 1", experiment.getTrials().get(0).getTitle(context));
        experiment.addTrial(trial2);
        assertEquals("Recording 1", experiment.getTrial(trial1.getTrialId()).getTitle(context));
        assertEquals("Recording 2", experiment.getTrial(trial2.getTrialId()).getTitle(context));

        // Deleting the trial with the real delete function causes a crash because of the context
        // class type. All we actually want is to remove the trial from the list.
        experiment.deleteTrialOnlyForTesting(trial2);
        experiment.addTrial(trial3);
        assertEquals("Recording 1", experiment.getTrial(trial1.getTrialId()).getTitle(context));
        assertEquals("Recording 3", experiment.getTrial(trial3.getTrialId()).getTitle(context));
    }

    private Context getContext() {
        return RuntimeEnvironment.application.getApplicationContext();
    }
}
