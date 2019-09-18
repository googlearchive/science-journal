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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.ExperimentCreator;
import com.google.android.apps.forscience.whistlepunk.FakeAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs.Destination;
import com.google.protobuf.nano.MessageNano;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for the Experiment class which involve labels, which use Parcelable. */
@RunWith(RobolectricTestRunner.class)
public class ExperimentTest {

  private static Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private static AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }

  private GoosciExperiment.Experiment makeExperimentWithLabels(long[] labelTimes) {
    GoosciExperiment.Experiment result = new GoosciExperiment.Experiment();
    result.labels = new GoosciLabel.Label[labelTimes.length];
    for (int i = 0; i < labelTimes.length; i++) {
      Label label = Label.newLabel(labelTimes[i], ValueType.TEXT);
      result.labels[i] = label.getLabelProto();
    }
    return result;
  }

  @Test
  public void testLabels() {
    GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
    GoosciUserMetadata.ExperimentOverview overview = new GoosciUserMetadata.ExperimentOverview();

    // No labels on creation
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(getContext(), proto, overview);
    assertEquals(0, experiment.getLabelCount());
    assertEquals(experiment.getLabels(), Collections.emptyList());

    // Add a label manually, outside of the proto
    GoosciPictureLabelValue.PictureLabelValue labelValueProto =
        GoosciPictureLabelValue.PictureLabelValue.getDefaultInstance();
    @MigrateAs(Destination.BUILDER)
    GoosciLabel.Label labelProto = new GoosciLabel.Label();
    labelProto.protoData = labelValueProto.toByteArray();
    labelProto.type = ValueType.PICTURE;
    experiment.getLabels().add(Label.fromLabel(labelProto));
    assertEquals(1, experiment.getLabelCount());

    // Make sure the proto gets updated properly
    proto.labels = new GoosciLabel.Label[1];
    proto.labels[0] = labelProto;
    assertTrue(MessageNano.messageNanoEquals(experiment.getExperimentProto(), proto));

    // Try constructing an experiment from a proto that already has these fields.
    Experiment experiment2 =
        ExperimentCreator.newExperimentForTesting(getContext(), proto, overview);
    assertEquals(labelValueProto, experiment2.getLabels().get(0).getPictureLabelValue());
    assertEquals(1, experiment2.getLabelCount());
    List<Label> labels = experiment2.getLabels();
    labels.add(Label.newLabel(20, ValueType.TEXT));
    assertEquals(2, experiment2.getLabelCount());

    assertEquals(2, experiment2.getExperimentProto().labels.length);
  }

  @Test
  public void testGetLabelsForRange() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {99, 100, 125, 201});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, new GoosciUserMetadata.ExperimentOverview());

    com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range.Builder range =
        Range.newBuilder().setStartMs(0).setEndMs(10);
    assertEquals(experiment.getLabelsForRange(range.build()), Collections.<Label>emptyList());

    range.setEndMs(200);
    assertEquals(3, experiment.getLabelsForRange(range.build()).size());

    range.setEndMs(300);
    assertEquals(4, experiment.getLabelsForRange(range.build()).size());

    range.setStartMs(100);
    assertEquals(3, experiment.getLabelsForRange(range.build()).size());
  }

  @Test
  public void testChangesConstructedProperly() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});

    // No changes on creation
    Experiment experiment = Experiment.fromExperiment(proto, new ExperimentOverviewPojo());
    assertEquals(0, experiment.getChanges().size());

    experiment.addChange(new Change());
    experiment.addChange(new Change());
    assertEquals(2, experiment.getChanges().size());

    Experiment experiment2 =
        Experiment.fromExperiment(experiment.getExperimentProto(), new ExperimentOverviewPojo());
    assertEquals(2, experiment2.getChanges().size());
  }

  @Test
  public void testTrials() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});

    // No trials on creation
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, new GoosciUserMetadata.ExperimentOverview());
    assertEquals(0, experiment.getTrialCount());
    assertEquals(experiment.getTrials(), Collections.emptyList());

    // Trials on creation that overlap with notes should get those notes added properly.
    GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
    trialProto.title = "cats";
    trialProto.recordingRange = Range.newBuilder().setStartMs(100).setEndMs(200).build();
    proto.trials = new GoosciTrial.Trial[1];
    proto.trials[0] = trialProto;

    Experiment experiment1 =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, new GoosciUserMetadata.ExperimentOverview());
    assertEquals(1, experiment1.getTrialCount());

    // Adding a new trial should work as expected.
    GoosciTrial.Trial trialProto2 = new GoosciTrial.Trial();
    trialProto2.title = "more cats";
    trialProto2.recordingRange = Range.newBuilder().setStartMs(200).setEndMs(500).build();
    Trial trial2 = Trial.fromTrial(trialProto2);
    experiment1.getTrials().add(trial2);

    assertEquals(experiment1.getTrialCount(), 2);

    // Getting the proto includes trial updates
    assertEquals(2, experiment1.getExperimentProto().trials.length);
  }

  @Test
  public void testGetTrialsWithFilters() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, new GoosciUserMetadata.ExperimentOverview());

    // New trials are invalid -- no end time.
    GoosciSensorLayout.SensorLayout[] noLayouts = new GoosciSensorLayout.SensorLayout[0];
    experiment.addTrial(Trial.newTrial(10, noLayouts, new FakeAppearanceProvider(), getContext()));
    experiment.addTrial(Trial.newTrial(20, noLayouts, new FakeAppearanceProvider(), getContext()));

    assertEquals(2, experiment.getTrials().size());
    assertEquals(2, experiment.getTrials(true, /* include invalid */ true).size());
    assertEquals(2, experiment.getTrials(false, true).size());
    assertEquals(0, experiment.getTrials(true, false).size());

    GoosciTrial.Trial validProto = new GoosciTrial.Trial();
    validProto.recordingRange = Range.newBuilder().setStartMs(100).setEndMs(200).build();
    validProto.trialId = "valid";
    Trial valid = Trial.fromTrial(validProto);
    experiment.addTrial(valid);

    assertEquals(3, experiment.getTrials(false, true).size());
    assertEquals(1, experiment.getTrials(true, false).size());
    assertEquals(1, experiment.getTrials(false, false).size());

    GoosciTrial.Trial archivedProto = new GoosciTrial.Trial();
    archivedProto.recordingRange = Range.newBuilder().setStartMs(300).setEndMs(400).build();
    archivedProto.archived = true;
    archivedProto.trialId = "archived";
    Trial archived = Trial.fromTrial(archivedProto);
    experiment.addTrial(archived);

    assertEquals(4, experiment.getTrials(true, true).size());
    assertEquals(3, experiment.getTrials(/* include archived */ false, true).size());
    assertEquals(2, experiment.getTrials(true, false).size());
    assertEquals(1, experiment.getTrials(false, false).size());
  }

  @Test
  public void testCleanInvalidTrials() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, new GoosciUserMetadata.ExperimentOverview());

    // Trials are valid.
    GoosciTrial.Trial validProto = new GoosciTrial.Trial();
    validProto.recordingRange = Range.newBuilder().setStartMs(100).setEndMs(200).build();
    validProto.trialId = "valid";
    experiment.addTrial(Trial.fromTrial(validProto));
    experiment.addTrial(Trial.fromTrial(validProto));

    assertEquals(2, experiment.getTrials().size());
    assertEquals(2, experiment.getTrials(true, /* include invalid */ true).size());
    assertEquals(2, experiment.getTrials(false, true).size());
    assertEquals(2, experiment.getTrials(true, false).size());

    // Trials are invalid -- no end time.
    GoosciSensorLayout.SensorLayout[] noLayouts = new GoosciSensorLayout.SensorLayout[0];
    experiment.addTrial(Trial.newTrial(10, noLayouts, new FakeAppearanceProvider(), getContext()));
    experiment.addTrial(Trial.newTrial(20, noLayouts, new FakeAppearanceProvider(), getContext()));

    assertEquals(4, experiment.getTrials().size());
    assertEquals(4, experiment.getTrials(true, /* include invalid */ true).size());
    assertEquals(4, experiment.getTrials(false, true).size());
    assertEquals(2, experiment.getTrials(true, false).size());

    GoosciTrial.Trial archivedProto = new GoosciTrial.Trial();
    archivedProto.recordingRange = Range.newBuilder().setStartMs(300).setEndMs(400).build();
    archivedProto.archived = true;
    archivedProto.trialId = "archived";
    Trial archived = Trial.fromTrial(archivedProto);
    experiment.addTrial(archived);

    assertEquals(5, experiment.getTrials(true, true).size());
    assertEquals(4, experiment.getTrials(/* include archived */ false, true).size());
    assertEquals(3, experiment.getTrials(true, false).size());
    assertEquals(2, experiment.getTrials(false, false).size());

    // Deleting the trial with the real delete function causes a crash because of the context
    // class type. All we actually want is to remove the trial from the list.
    experiment.cleanTrialsOnlyForTesting();

    assertEquals(3, experiment.getTrials(true, true).size());
    assertEquals(2, experiment.getTrials(/* include archived */ false, true).size());
    assertEquals(3, experiment.getTrials(true, /* include invalid */ false).size());
    assertEquals(2, experiment.getTrials(false, false).size());
  }

  @Test
  public void testUpdatesProtoOnlyWhenNeeded() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {99, 100, 125, 201});
    GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
    trialProto.title = "title";
    proto.trials = new GoosciTrial.Trial[] {trialProto};

    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, new GoosciUserMetadata.ExperimentOverview());

    // Try to get the proto *before* converting the objects into lists.
    GoosciExperiment.Experiment result = experiment.getExperimentProto();
    assertEquals(4, result.labels.length);
    assertEquals(1, result.trials.length);
  }

  @Test
  public void testTrialNameOrdering() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, new GoosciUserMetadata.ExperimentOverview());
    GoosciTrial.Trial trialProto1 = new GoosciTrial.Trial();
    GoosciTrial.Trial trialProto2 = new GoosciTrial.Trial();
    GoosciTrial.Trial trialProto3 = new GoosciTrial.Trial();
    trialProto1.trialId = "trial1";
    trialProto2.trialId = "trial2";
    trialProto3.trialId = "trial3";
    trialProto1.recordingRange = Range.newBuilder().setStartMs(0).build();
    trialProto2.recordingRange = Range.newBuilder().setStartMs(10).build();
    trialProto3.recordingRange = Range.newBuilder().setStartMs(20).build();
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

  @Test
  public void testSetGetImagePath() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    @MigrateAs(Destination.BUILDER)
    GoosciUserMetadata.ExperimentOverview overview = new GoosciUserMetadata.ExperimentOverview();
    overview.experimentId = "experimentId";
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(getContext(), proto, overview);
    experiment.setImagePath("test.jpg");
    String overviewImagePath = experiment.getExperimentOverview().getImagePath();
    String experimentImagePath = experiment.getExperimentProto().imagePath;

    assertEquals("experiments/experimentId/test.jpg", overviewImagePath);
    assertEquals("test.jpg", experimentImagePath);

    experiment.setImagePath("path.jpg");
    overviewImagePath = experiment.getExperimentOverview().getImagePath();
    assertEquals("experiments/experimentId/path.jpg", overviewImagePath);

    experimentImagePath = experiment.getExperimentProto().imagePath;
    assertEquals("path.jpg", experimentImagePath);
  }

  @Test
  public void testChangesAdded() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    ExperimentOverviewPojo pojo = new ExperimentOverviewPojo();
    pojo.setExperimentId("id");
    Experiment experiment = Experiment.fromExperiment(proto, pojo);

    assertEquals(0, experiment.getChanges().size());
    experiment.setTitle("foo");
    assertEquals(1, experiment.getChanges().size());

    Label label = Label.newLabel(1000, ValueType.TEXT);
    experiment.addLabel(experiment, label);
    assertEquals(2, experiment.getChanges().size());

    label.setTimestamp(2000);
    experiment.updateLabel(experiment, label);
    assertEquals(3, experiment.getChanges().size());

    label.setTimestamp(3000);
    experiment.updateLabelWithoutSorting(experiment, label);
    assertEquals(4, experiment.getChanges().size());

    GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
    trialProto.labels = new GoosciLabel.Label[1];
    @MigrateAs(Destination.BUILDER)
    GoosciLabel.Label labelProto = new GoosciLabel.Label();
    labelProto.labelId = "labelId";
    labelProto.timestampMs = 1;
    trialProto.labels[0] = labelProto;
    Trial trial = Trial.fromTrial(trialProto);

    experiment.addTrial(trial);

    assertEquals(trial.getLabelCount(), 1);
    assertEquals(5, experiment.getChanges().size());

    trial.setTitle("foo");
    experiment.updateTrial(trial);

    assertEquals(trial.getLabelCount(), 1);
    assertEquals(6, experiment.getChanges().size());

    Label label2 = trial.getLabels().get(0);
    label2.setTimestamp(10);
    trial.updateLabel(experiment, label2);
    assertEquals(10, trial.getLabels().get(0).getTimeStamp());
    assertEquals(7, experiment.getChanges().size());

    label2.setTimestamp(20);
    trial.updateLabelWithoutSorting(experiment, label2);
    assertEquals(20, trial.getLabels().get(0).getTimeStamp());
    assertEquals(8, experiment.getChanges().size());
  }

  @Test
  public void testMergeIdenticalExperiments() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);
    experimentServer.setTitle("Title");

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(0, sync.getImageDownloads().size());
    assertEquals(0, sync.getImageUploads().size());

    assertEquals(0, sync.getTrialDownloads().size());
    assertEquals(0, sync.getTrialUploads().size());

    assertEquals("Title", experimentServer.getTitle());
    assertEquals(1, experimentServer.getChanges().size());
  }

  @Test
  public void testMergeIdenticalExperimentsWithTwoChanges() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);
    experimentServer.setTitle("Title");
    experimentServer.setTitle("Title2");

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(0, sync.getImageDownloads().size());
    assertEquals(0, sync.getImageUploads().size());

    assertEquals(0, sync.getTrialDownloads().size());
    assertEquals(0, sync.getTrialUploads().size());

    assertEquals("Title2", experimentServer.getTitle());
    assertEquals(2, experimentServer.getChanges().size());
  }

  @Test
  public void testMergeChangedExperimentTitleChange() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);
    experimentServer.setTitle("Title");

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentClient.setTitle("Title2");

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals("Title2", experimentServer.getTitle());
    assertEquals(2, experimentServer.getChanges().size());
  }

  @Test
  public void testMergeChangedExperimentTitleChangeTwice() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);
    experimentServer.setTitle("Title");

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentClient.setTitle("Title2");
    experimentClient.setTitle("Title3");
    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals("Title3", experimentServer.getTitle());
    assertEquals(3, experimentServer.getChanges().size());
  }

  @Test
  public void testMergeChangedExperimentTitleChangeConflict() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);
    experimentServer.setTitle("Title");

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentServer.setTitle("Title2");

    experimentClient.setTitle("Title3");

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals("Title2 / Title3", experimentServer.getTitle());
    assertEquals(3, experimentServer.getChanges().size());
  }

  @Test
  public void testMergeExperimentsImageLabelAddOnly() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    PictureLabelValue pictureLabelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("foo").build();
    Label label = Label.newLabel(1, ValueType.PICTURE);
    label.setLabelProtoData(pictureLabelValue);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentClient.addLabel(experimentClient, label);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(0, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(0, experimentServer.getLabelCount());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(1, sync.getImageDownloads().size());

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());
  }

  @Test
  public void testMergeExperimentsLabelAddOnly() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentClient.addLabel(experimentClient, label);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(0, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(0, experimentServer.getLabelCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());
  }

  @Test
  public void testMergeExperimentsLabelAddAndEdit() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    PictureLabelValue pictureLabelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("foo").build();
    Label label = Label.newLabel(1, ValueType.PICTURE);
    label.setLabelProtoData(pictureLabelValue);

    experimentServer.addLabel(experimentServer, label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    Label label2 = Label.fromLabel(label.getLabelProto());
    PictureLabelValue pictureLabelValue2 =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("bar").build();
    label2.setLabelProtoData(pictureLabelValue2);
    experimentClient.updateLabel(experimentClient, label2);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(1, sync.getImageDownloads().size());

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    assertEquals(
        experimentClient.getLabel(label2.getLabelId()).getCaptionText(),
        experimentServer.getLabel(label2.getLabelId()).getCaptionText());
  }

  @Test
  public void testMergeExperimentsLabelAddAndDelete() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    PictureLabelValue pictureLabelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("foo").build();
    Label label = Label.newLabel(1, ValueType.PICTURE);
    label.setLabelProtoData(pictureLabelValue);

    experimentServer.addLabel(experimentServer, label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentClient.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(0, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(0, sync.getImageDownloads().size());
    assertEquals(0, sync.getImageUploads().size());

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(0, experimentClient.getLabelCount());
    assertEquals(0, experimentServer.getLabelCount());
  }

  @Test
  public void testMergeExperimentsLabelAddAndEditDelete() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    PictureLabelValue pictureLabelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("foo").build();
    Label label = Label.newLabel(1, ValueType.PICTURE);
    label.setLabelProtoData(pictureLabelValue);

    experimentServer.addLabel(experimentServer, label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    Label label2 = Label.fromLabel(label.getLabelProto());
    PictureLabelValue pictureLabelValue2 =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("bar").build();
    label2.setLabelProtoData(pictureLabelValue2);
    experimentClient.updateLabel(experimentClient, label2);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    experimentClient.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    assertEquals(3, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(0, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(0, sync.getImageDownloads().size());
    assertEquals(0, sync.getImageUploads().size());

    assertEquals(3, experimentClient.getChanges().size());
    assertEquals(3, experimentServer.getChanges().size());

    assertEquals(0, experimentClient.getLabelCount());
    assertEquals(0, experimentServer.getLabelCount());
  }

  @Test
  public void testMergeExperimentsTrialAddOnly() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentClient.addTrial(trial);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(0, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(0, experimentServer.getTrialCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(1, experimentServer.getTrialCount());
  }

  @Test
  public void testMergeExperimentsTrialAddAndDeleteLocalAndEditRemotely() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());
    trial.setTitle("Foo");

    experimentServer.addTrial(trial);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    trial.setTitle("Bar");
    experimentClient.updateTrial(trial);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(1, experimentServer.getTrialCount());

    Trial toDelete = experimentServer.getTrial(trial.getTrialId());
    experimentServer.deleteTrialOnlyForTesting(toDelete);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(0, experimentServer.getTrialCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(3, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(0, experimentServer.getTrialCount());
  }

  @Test
  public void testMergeExperimentsTrialNoteAdd() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    experimentServer.addTrial(trial);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    PictureLabelValue pictureLabelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("foo").build();
    Label label = Label.newLabel(1, ValueType.PICTURE);
    label.setLabelProtoData(pictureLabelValue);

    Trial clientTrial = experimentClient.getTrial(trial.getTrialId());

    clientTrial.addLabel(experimentClient, label);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, clientTrial.getLabelCount());
    assertEquals(0, trial.getLabelCount());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(1, sync.getImageDownloads().size());
    assertEquals(0, sync.getImageUploads().size());

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, clientTrial.getLabelCount());
    assertEquals(1, trial.getLabelCount());
  }

  @Test
  public void testMergeExperimentsTrialNoteAddLocally() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    experimentServer.addTrial(trial);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    PictureLabelValue pictureLabelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("foo").build();
    Label label = Label.newLabel(1, ValueType.PICTURE);
    label.setLabelProtoData(pictureLabelValue);

    Trial serverTrial = experimentServer.getTrial(trial.getTrialId());

    serverTrial.addLabel(experimentServer, label);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, serverTrial.getLabelCount());
    assertEquals(1, trial.getLabelCount());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(0, sync.getImageDownloads().size());
    assertEquals(1, sync.getImageUploads().size());

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, serverTrial.getLabelCount());
    assertEquals(1, trial.getLabelCount());
  }

  @Test
  public void testMergeExperimentsTrialNoteUpdate() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    experimentServer.addTrial(trial);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);

    trial.addLabel(label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    Label label2 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption2 = Caption.newBuilder().setText("caption2").build();
    label2.setCaption(caption2);

    Trial clientTrial = experimentClient.getTrial(trial.getTrialId());

    clientTrial.updateLabel(experimentClient, label2);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, clientTrial.getLabelCount());
    assertEquals(1, trial.getLabelCount());

    assertEquals("caption", experimentServer.getLabel(label.getLabelId()).getCaptionText());

    assertEquals("caption2", experimentClient.getLabel(label.getLabelId()).getCaptionText());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, clientTrial.getLabelCount());
    assertEquals(1, trial.getLabelCount());

    assertEquals("caption2", experimentServer.getLabel(label.getLabelId()).getCaptionText());

    assertEquals("caption2", experimentClient.getLabel(label.getLabelId()).getCaptionText());
  }

  @Test
  public void testMergeExperimentsTrialNoteDelete() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    experimentServer.addTrial(trial);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);

    trial.addLabel(label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    Trial clientTrial = experimentClient.getTrial(trial.getTrialId());

    clientTrial.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(0, clientTrial.getLabelCount());
    assertEquals(1, trial.getLabelCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(0, clientTrial.getLabelCount());
    assertEquals(0, trial.getLabelCount());
  }

  @Test
  public void testMergeExperimentsAddTrialAndNoteAndDeleteTrial() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    experimentClient.addTrial(trial);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);
    trial.addLabel(experimentClient, label);

    assertEquals(1, trial.getLabelCount());

    trial.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    assertEquals(3, experimentClient.getChanges().size());
    assertEquals(0, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(0, experimentServer.getTrialCount());

    assertEquals(0, trial.getLabelCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(3, experimentClient.getChanges().size());
    assertEquals(3, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(1, experimentServer.getTrialCount());

    assertEquals(0, experimentServer.getTrial(trial.getTrialId()).getLabelCount());
  }

  @Test
  public void testMergeExperimentsNoteEditConflict() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);

    experimentServer.addLabel(experimentServer, label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    Label label2 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption2 = Caption.newBuilder().setText("caption2").build();
    label2.setCaption(caption2);

    experimentClient.updateLabel(experimentClient, label2);

    Label label3 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption3 = Caption.newBuilder().setText("caption3").build();
    label3.setCaption(caption3);

    experimentServer.updateLabel(experimentServer, label3);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(4, experimentServer.getChanges().size());
    assertEquals(2, experimentServer.getLabelCount());
  }

  @Test
  public void testMergeExperimentsNoteEditLocallyDeletedExternalConflict() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);

    experimentServer.addLabel(experimentServer, label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    experimentClient.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    Label label3 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption3 = Caption.newBuilder().setText("caption3").build();
    label3.setCaption(caption3);

    experimentServer.updateLabel(experimentServer, label3);

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(0, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(3, experimentServer.getChanges().size());
    assertEquals(0, experimentServer.getLabelCount());
  }

  @Test
  public void testMergeExperimentsNoteEditedExternallyDeletedLocallyConflict() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);

    experimentServer.addLabel(experimentServer, label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    Label label2 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption2 = Caption.newBuilder().setText("caption2").build();
    label2.setCaption(caption2);

    experimentClient.updateLabel(experimentClient, label2);

    experimentServer.deleteLabelAndReturnAssetDeleter(experimentServer, label, getAppAccount());

    assertEquals(2, experimentClient.getChanges().size());
    assertEquals(2, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(0, experimentServer.getLabelCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(3, experimentServer.getChanges().size());
    assertEquals(0, experimentServer.getLabelCount());
  }

  @Test
  public void testMergeDeletedTrialsNotUploaded() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial1 = Trial.newTrial(1, new SensorLayout[0], null, getContext());
    experimentServer.addTrial(trial1);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    Trial trial2 = Trial.newTrial(1, new SensorLayout[0], null, getContext());
    experimentServer.addTrial(trial2);

    experimentServer.deleteTrialOnlyForTesting(trial1);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(3, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getTrialCount());
    assertEquals(1, experimentServer.getTrialCount());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertEquals(1, sync.getTrialUploads().size());
    assertTrue(sync.getTrialUploads().contains(trial2.getTrialId()));
  }

  @Test
  public void testMergeExperimentsOverwrite() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);
    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    experimentServer.addTrial(trial);

    PictureLabelValue pictureLabelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder().setFilePath("foo").build();
    Label label = Label.newLabel(1, ValueType.PICTURE);
    label.setLabelProtoData(pictureLabelValue);

    experimentClient.addLabel(experimentClient, label);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(0, experimentClient.getTrialCount());

    assertEquals(0, experimentServer.getLabelCount());
    assertEquals(1, experimentServer.getTrialCount());

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), true);

    assertEquals(1, experimentClient.getChanges().size());
    assertEquals(1, experimentServer.getChanges().size());

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(0, experimentClient.getTrialCount());

    assertEquals(1, experimentServer.getLabelCount());
    assertEquals(0, experimentServer.getTrialCount());

    assertEquals(0, sync.getTrialUploads().size());
    assertEquals(0, sync.getTrialDownloads().size());
    assertEquals(0, sync.getImageUploads().size());
    assertEquals(0, sync.getImageDownloads().size());
  }

  @Test
  public void testMergeExperimentsNoteEditedWithCaptionChange() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Label label = Label.newLabel(1, ValueType.TEXT);
    Caption caption = Caption.newBuilder().setText("caption").build();
    label.setCaption(caption);

    experimentServer.addLabel(experimentServer, label);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    Label label2 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption2 = Caption.newBuilder().setText("caption2").build();
    label2.setCaption(caption2);
    experimentClient.addChange(Change.newModifyTypeChange(ElementType.CAPTION, label.getLabelId()));
    assertThat(experimentClient.getChanges()).hasSize(2);
    experimentClient.updateLabel(experimentClient, label2);
    assertThat(experimentClient.getChanges()).hasSize(3);

    experimentServer.addChange(Change.newModifyTypeChange(ElementType.CAPTION, label.getLabelId()));
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertEquals(1, experimentClient.getLabelCount());
    assertEquals(1, experimentServer.getLabelCount());

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentServer.getChanges()).hasSize(5);
    assertEquals(1, experimentServer.getLabelCount());
  }

}
