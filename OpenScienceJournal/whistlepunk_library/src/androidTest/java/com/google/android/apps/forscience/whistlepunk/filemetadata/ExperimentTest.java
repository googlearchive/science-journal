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

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.protobuf.nano.MessageNano;

import java.util.Collections;
import java.util.List;

/**
 * Tests for the Experiment class which involve labels, which use Parcelable.
 */
public class ExperimentTest extends AndroidTestCase {

    private GoosciExperiment.Experiment makeExperimentWithLabels(long[] labelTimes) {
        GoosciExperiment.Experiment result = new GoosciExperiment.Experiment();
        result.labels = new GoosciLabel.Label[labelTimes.length];
        for (int i = 0; i < labelTimes.length; i++) {
            Label label = Label.newLabel(labelTimes[i]);
            result.labels[i] = label.getLabelProto();
        }
        return result;
    }

    public void testLabels() {
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();

        // No labels on creation
        Experiment experiment = new Experiment(proto, false);
        assertEquals(experiment.getLabelCount(), 0);
        assertEquals(experiment.getLabels(), Collections.emptyList());

        // Add a label manually, outside of the proto
        GoosciLabelValue.LabelValue labelValueProto = new GoosciLabelValue.LabelValue();
        PictureLabelValue.populateLabelValue(labelValueProto, "path", "caption");
        GoosciLabel.Label labelProto = new GoosciLabel.Label();
        labelProto.values = new GoosciLabelValue.LabelValue[1];
        labelProto.values[0] = labelValueProto;
        experiment.getLabels().add(Label.fromLabel(labelProto));
        assertEquals(experiment.getLabelCount(), 1);

        // Make sure the proto gets updated properly
        proto.labels = new GoosciLabel.Label[1];
        proto.labels[0] = labelProto;
        assertTrue(MessageNano.messageNanoEquals(experiment.getExperimentProto(), proto));

        // Try constructing an experiment from a proto that already has these fields.
        Experiment experiment2 = new Experiment(proto, false);
        assertTrue(MessageNano.messageNanoEquals(experiment2.getLabels().get(0)
                .getLabelValue(GoosciLabelValue.LabelValue.PICTURE).getValue(), labelValueProto));
        assertEquals(experiment2.getLabelCount(), 1);
        List<Label> labels = experiment2.getLabels();
        labels.add(Label.newLabel(20));
        assertEquals(experiment2.getLabelCount(), 2);

        assertEquals(experiment2.getExperimentProto().labels.length, 2);
    }

    public void testGetLabelsForRange() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{99, 100, 125, 201});
        Experiment experiment = new Experiment(proto, false);

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

    public void testTrials() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{99, 100, 125, 201});

        // No trials on creation
        Experiment experiment = new Experiment(proto, false);
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

        Experiment experiment1 = new Experiment(proto, false);
        assertEquals(experiment1.getTrialCount(), 1);
        assertEquals(experiment1.getTrials().get(0).getLabels().size(), 2);

        // Adding a new trial should work as expected.
        GoosciTrial.Trial trialProto2 = new GoosciTrial.Trial();
        trialProto2.title = "more cats";
        GoosciTrial.Range range2 = new GoosciTrial.Range();
        range.startMs = 200;
        range.endMs = 500;
        trialProto2.recordingRange = range2;
        Trial trial2 = Trial.fromTrial(trialProto2, experiment1.getLabelsForRange(range2));
        experiment1.getTrials().add(trial2);

        assertEquals(experiment1.getTrialCount(), 2);

        // Getting the proto includes trial updates
        assertEquals(experiment1.getExperimentProto().trials.length, 2);
    }

    public void testUpdatesProtoOnlyWhenNeeded() {
        GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[]{99, 100, 125, 201});
        GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
        trialProto.title = "title";
        proto.trials = new GoosciTrial.Trial[]{trialProto};

        Experiment experiment = new Experiment(proto, false);

        // Try to get the proto *before* converting the objects into lists.
        GoosciExperiment.Experiment result = experiment.getExperimentProto();
        assertEquals(result.labels.length, 4);
        assertEquals(result.trials.length, 1);
    }
}
