package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;

/**
 * Tests for Trials
 */
public class TrialTest extends AndroidTestCase {
    public void testTrialWithLabels() {
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

        Label second = Label.newLabel(20);
        trial.addLabel(second);
        assertEquals(trial.getLabelCount(), 2);

        trial.deleteLabel(label, getContext());
        assertEquals(trial.getLabelCount(), 1);
    }

    public void testLabelsStillSortedOnAdd() {
        Trial trial = Trial.newTrial(10, new GoosciSensorLayout.SensorLayout[0]);
        trial.addLabel(Label.newLabel(20));
        trial.addLabel(Label.newLabel(30));
        trial.addLabel(Label.newLabel(10));
        assertEquals(trial.getLabels().size(), 3);
        assertEquals(trial.getLabels().get(0).getTimeStamp(), 10);
        assertEquals(trial.getLabels().get(1).getTimeStamp(), 20);
        assertEquals(trial.getLabels().get(2).getTimeStamp(), 30);
    }

    public void testLabelsStillSortedOnUpdate() {
        Trial trial = Trial.newTrial(10, new GoosciSensorLayout.SensorLayout[0]);
        trial.addLabel(Label.newLabel(10));
        trial.addLabel(Label.newLabel(20));
        trial.addLabel(Label.newLabel(30));
        Label second = trial.getLabels().get(1);
        second.setTimestamp(40);
        trial.updateLabel(second);
        assertEquals(trial.getLabels().get(2).getTimeStamp(), 40);
    }
}
