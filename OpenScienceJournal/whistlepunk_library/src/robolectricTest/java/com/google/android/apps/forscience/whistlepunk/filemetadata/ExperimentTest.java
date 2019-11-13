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

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.ExperimentCreator;
import com.google.android.apps.forscience.whistlepunk.FakeAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.ExperimentOverview;
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
    GoosciExperiment.Experiment.Builder result = GoosciExperiment.Experiment.newBuilder();
    for (int i = 0; i < labelTimes.length; i++) {
      Label label = Label.newLabel(labelTimes[i], ValueType.TEXT);
      result.addLabels(label.getLabelProto());
    }
    return result.build();
  }

  @Test
  public void testLabels() {
    GoosciExperiment.Experiment.Builder proto = GoosciExperiment.Experiment.newBuilder();
    GoosciUserMetadata.ExperimentOverview overview =
        GoosciUserMetadata.ExperimentOverview.getDefaultInstance();

    // No labels on creation
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(getContext(), proto.build(), overview);
    assertThat(experiment.getLabelCount()).isEqualTo(0);
    assertThat(experiment.getLabels()).isEmpty();

    // Add a label manually, outside of the proto
    GoosciPictureLabelValue.PictureLabelValue labelValueProto =
        GoosciPictureLabelValue.PictureLabelValue.getDefaultInstance();
    GoosciLabel.Label labelProto =
        GoosciLabel.Label.newBuilder()
            .setProtoData(labelValueProto.toByteString())
            .setType(ValueType.PICTURE)
            .build();
    experiment.addLabel(experiment, Label.fromLabel(labelProto));
    assertThat(experiment.getLabelCount()).isEqualTo(1);

    // Make sure the proto gets updated properly
    proto.addLabels(labelProto);
    assertThat(experiment.getExperimentProto().getLabels(0).toString())
        .isEqualTo(proto.getLabels(0).toString());

    // Try constructing an experiment from a proto that already has these fields.
    Experiment experiment2 =
        ExperimentCreator.newExperimentForTesting(getContext(), proto.build(), overview);
    assertThat(experiment2.getLabels().get(0).getPictureLabelValue()).isEqualTo(labelValueProto);
    assertThat(experiment2.getLabelCount()).isEqualTo(1);
    experiment2.addLabel(experiment2, Label.newLabel(20, ValueType.TEXT));
    assertThat(experiment2.getLabelCount()).isEqualTo(2);

    assertThat(experiment2.getExperimentProto().getLabelsCount()).isEqualTo(2);
    assertThat(experiment2.getExperimentProto().getLabelsList()).hasSize(2);
  }

  @Test
  public void testGetLabelsForRange() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {99, 100, 125, 201});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, GoosciUserMetadata.ExperimentOverview.getDefaultInstance());

    com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range.Builder range =
        Range.newBuilder().setStartMs(0).setEndMs(10);
    assertThat(experiment.getLabelsForRange(range.build())).isEmpty();

    range.setEndMs(200);
    assertThat(experiment.getLabelsForRange(range.build())).hasSize(3);

    range.setEndMs(300);
    assertThat(experiment.getLabelsForRange(range.build())).hasSize(4);

    range.setStartMs(100);
    assertThat(experiment.getLabelsForRange(range.build())).hasSize(3);
  }

  @Test
  public void testChangesConstructedProperly() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});

    // No changes on creation
    Experiment experiment = Experiment.fromExperiment(proto, new ExperimentOverviewPojo());
    assertThat(experiment.getChanges()).isEmpty();

    experiment.addChange(new Change());
    experiment.addChange(new Change());
    assertThat(experiment.getChanges()).hasSize(2);

    Experiment experiment2 =
        Experiment.fromExperiment(experiment.getExperimentProto(), new ExperimentOverviewPojo());
    assertThat(experiment2.getChanges()).hasSize(2);
  }

  @Test
  public void testTrials() {
    GoosciExperiment.Experiment.Builder proto = makeExperimentWithLabels(new long[] {}).toBuilder();

    // No trials on creation
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(),
            proto.build(),
            GoosciUserMetadata.ExperimentOverview.getDefaultInstance());
    assertThat(experiment.getTrialCount()).isEqualTo(0);
    assertThat(experiment.getTrials()).isEmpty();

    // Trials on creation that overlap with notes should get those notes added properly.
    GoosciTrial.Trial trialProto =
        GoosciTrial.Trial.newBuilder()
            .setTitle("cats")
            .setRecordingRange(Range.newBuilder().setStartMs(100).setEndMs(200))
            .build();
    proto.clearTrials().addTrials(trialProto);

    Experiment experiment1 =
        ExperimentCreator.newExperimentForTesting(
            getContext(),
            proto.build(),
            GoosciUserMetadata.ExperimentOverview.getDefaultInstance());
    assertThat(experiment1.getTrialCount()).isEqualTo(1);

    // Adding a new trial should work as expected.
    GoosciTrial.Trial trialProto2 =
        GoosciTrial.Trial.newBuilder()
            .setTitle("more cats")
            .setRecordingRange(Range.newBuilder().setStartMs(200).setEndMs(500))
            .build();
    Trial trial2 = Trial.fromTrial(trialProto2);
    experiment1.getTrials().add(trial2);

    assertThat(experiment1.getTrialCount()).isEqualTo(2);

    // Getting the proto includes trial updates
    assertThat(experiment1.getExperimentProto().getTrialsList()).hasSize(2);
  }

  @Test
  public void testGetTrialsWithFilters() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, GoosciUserMetadata.ExperimentOverview.getDefaultInstance());

    // New trials are invalid -- no end time.
    SensorLayout[] noLayouts = new SensorLayout[0];
    experiment.addTrial(Trial.newTrial(10, noLayouts, new FakeAppearanceProvider(), getContext()));
    experiment.addTrial(Trial.newTrial(20, noLayouts, new FakeAppearanceProvider(), getContext()));

    assertThat(experiment.getTrials()).hasSize(2);
    assertThat(experiment.getTrials(true, /* include invalid */ true)).hasSize(2);
    assertThat(experiment.getTrials(false, true)).hasSize(2);
    assertThat(experiment.getTrials(true, false)).isEmpty();

    GoosciTrial.Trial validProto =
        GoosciTrial.Trial.newBuilder()
            .setRecordingRange(Range.newBuilder().setStartMs(100).setEndMs(200))
            .setTrialId("valid")
            .build();
    Trial valid = Trial.fromTrial(validProto);
    experiment.addTrial(valid);

    assertThat(experiment.getTrials(false, true)).hasSize(3);
    assertThat(experiment.getTrials(true, false)).hasSize(1);
    assertThat(experiment.getTrials(false, false)).hasSize(1);

    GoosciTrial.Trial archivedProto =
        GoosciTrial.Trial.newBuilder()
            .setRecordingRange(Range.newBuilder().setStartMs(300).setEndMs(400))
            .setArchived(true)
            .setTrialId("archived")
            .build();
    Trial archived = Trial.fromTrial(archivedProto);
    experiment.addTrial(archived);

    assertThat(experiment.getTrials(true, true)).hasSize(4);
    assertThat(experiment.getTrials(/* include archived */ false, true)).hasSize(3);
    assertThat(experiment.getTrials(true, false)).hasSize(2);
    assertThat(experiment.getTrials(false, false)).hasSize(1);
  }

  @Test
  public void testCleanInvalidTrials() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, GoosciUserMetadata.ExperimentOverview.getDefaultInstance());

    // Trials are valid.
    GoosciTrial.Trial validProto =
        GoosciTrial.Trial.newBuilder()
            .setRecordingRange(Range.newBuilder().setStartMs(100).setEndMs(200))
            .setTrialId("valid")
            .build();
    experiment.addTrial(Trial.fromTrial(validProto));
    experiment.addTrial(Trial.fromTrial(validProto));

    assertThat(experiment.getTrials()).hasSize(2);
    assertThat(experiment.getTrials(true, /* include invalid */ true)).hasSize(2);
    assertThat(experiment.getTrials(false, true)).hasSize(2);
    assertThat(experiment.getTrials(true, false)).hasSize(2);

    // Trials are invalid -- no end time.
    SensorLayout[] noLayouts = new SensorLayout[0];
    experiment.addTrial(Trial.newTrial(10, noLayouts, new FakeAppearanceProvider(), getContext()));
    experiment.addTrial(Trial.newTrial(20, noLayouts, new FakeAppearanceProvider(), getContext()));

    assertThat(experiment.getTrials()).hasSize(4);
    assertThat(experiment.getTrials(true, /* include invalid */ true)).hasSize(4);
    assertThat(experiment.getTrials(false, true)).hasSize(4);
    assertThat(experiment.getTrials(true, false)).hasSize(2);

    GoosciTrial.Trial archivedProto =
        GoosciTrial.Trial.newBuilder()
            .setRecordingRange(Range.newBuilder().setStartMs(300).setEndMs(400))
            .setArchived(true)
            .setTrialId("archived")
            .build();
    Trial archived = Trial.fromTrial(archivedProto);
    experiment.addTrial(archived);

    assertThat(experiment.getTrials(true, true)).hasSize(5);
    assertThat(experiment.getTrials(/* include archived */ false, true)).hasSize(4);
    assertThat(experiment.getTrials(true, false)).hasSize(3);
    assertThat(experiment.getTrials(false, false)).hasSize(2);

    // Deleting the trial with the real delete function causes a crash because of the context
    // class type. All we actually want is to remove the trial from the list.
    experiment.cleanTrialsOnlyForTesting();

    assertThat(experiment.getTrials(true, true)).hasSize(3);
    assertThat(experiment.getTrials(/* include archived */ false, true)).hasSize(2);
    assertThat(experiment.getTrials(true, /* include invalid */ false)).hasSize(3);
    assertThat(experiment.getTrials(false, false)).hasSize(2);
  }

  @Test
  public void testUpdatesProtoOnlyWhenNeeded() {
    GoosciExperiment.Experiment.Builder proto =
        makeExperimentWithLabels(new long[] {99, 100, 125, 201}).toBuilder();
    GoosciTrial.Trial trialProto = GoosciTrial.Trial.newBuilder().setTitle("title").build();
    proto.clearTrials().addTrials(trialProto);

    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(),
            proto.build(),
            GoosciUserMetadata.ExperimentOverview.getDefaultInstance());

    // Try to get the proto *before* converting the objects into lists.
    GoosciExperiment.Experiment result = experiment.getExperimentProto();
    assertThat(result.getLabelsCount()).isEqualTo(4);
    assertThat(result.getLabelsList()).hasSize(4);
    assertThat(result.getTrialsCount()).isEqualTo(1);
    assertThat(result.getTrialsList()).hasSize(1);
  }

  @Test
  public void testTrialNameOrdering() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(
            getContext(), proto, GoosciUserMetadata.ExperimentOverview.getDefaultInstance());
    GoosciTrial.Trial trialProto1 =
        GoosciTrial.Trial.newBuilder()
            .setTrialId("trial1")
            .setRecordingRange(Range.newBuilder().setStartMs(0))
            .build();
    GoosciTrial.Trial trialProto2 =
        GoosciTrial.Trial.newBuilder()
            .setTrialId("trial2")
            .setRecordingRange(Range.newBuilder().setStartMs(10))
            .build();
    GoosciTrial.Trial trialProto3 =
        GoosciTrial.Trial.newBuilder()
            .setTrialId("trial3")
            .setRecordingRange(Range.newBuilder().setStartMs(20))
            .build();
    Trial trial1 = Trial.fromTrial(trialProto1);
    Trial trial2 = Trial.fromTrial(trialProto2);
    Trial trial3 = Trial.fromTrial(trialProto3);

    Context context = getContext();
    experiment.addTrial(trial1);
    assertThat(experiment.getTrials().get(0).getTitle(context)).isEqualTo("Recording 1");
    experiment.addTrial(trial2);
    assertThat(experiment.getTrial(trial1.getTrialId()).getTitle(context)).isEqualTo("Recording 1");
    assertThat(experiment.getTrial(trial2.getTrialId()).getTitle(context)).isEqualTo("Recording 2");

    // Deleting the trial with the real delete function causes a crash because of the context
    // class type. All we actually want is to remove the trial from the list.
    experiment.deleteTrialOnlyForTesting(trial2);
    experiment.addTrial(trial3);
    assertThat(experiment.getTrial(trial1.getTrialId()).getTitle(context)).isEqualTo("Recording 1");
    assertThat(experiment.getTrial(trial3.getTrialId()).getTitle(context)).isEqualTo("Recording 3");
  }

  @Test
  public void testSetGetImagePath() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    ExperimentOverview overview =
        GoosciUserMetadata.ExperimentOverview.newBuilder().setExperimentId("experimentId").build();
    Experiment experiment =
        ExperimentCreator.newExperimentForTesting(getContext(), proto, overview);
    experiment.setImagePath("test.jpg");
    String overviewImagePath = experiment.getExperimentOverview().getImagePath();
    String experimentImagePath = experiment.getExperimentProto().getImagePath();

    assertThat(overviewImagePath).isEqualTo("experiments/experimentId/test.jpg");
    assertThat(experimentImagePath).isEqualTo("test.jpg");

    experiment.setImagePath("path.jpg");
    overviewImagePath = experiment.getExperimentOverview().getImagePath();
    assertThat(overviewImagePath).isEqualTo("experiments/experimentId/path.jpg");

    experimentImagePath = experiment.getExperimentProto().getImagePath();
    assertThat(experimentImagePath).isEqualTo("path.jpg");
  }

  @Test
  public void testChangesAdded() {
    GoosciExperiment.Experiment proto = makeExperimentWithLabels(new long[] {});
    ExperimentOverviewPojo pojo = new ExperimentOverviewPojo();
    pojo.setExperimentId("id");
    Experiment experiment = Experiment.fromExperiment(proto, pojo);

    assertThat(experiment.getChanges()).isEmpty();
    experiment.setTitle("foo");
    assertThat(experiment.getChanges()).hasSize(1);

    Label label = Label.newLabel(1000, ValueType.TEXT);
    experiment.addLabel(experiment, label);
    assertThat(experiment.getChanges()).hasSize(2);

    label.setTimestamp(2000);
    experiment.updateLabel(experiment, label);
    assertThat(experiment.getChanges()).hasSize(3);

    label.setTimestamp(3000);
    experiment.updateLabelWithoutSorting(experiment, label);
    assertThat(experiment.getChanges()).hasSize(4);

    GoosciTrial.Trial trialProto =
        GoosciTrial.Trial.newBuilder()
            .addLabels(GoosciLabel.Label.newBuilder().setLabelId("labelId").setTimestampMs(1))
            .build();
    Trial trial = Trial.fromTrial(trialProto);

    experiment.addTrial(trial);

    assertThat(trial.getLabelCount()).isEqualTo(1);
    assertThat(experiment.getChanges()).hasSize(5);

    trial.setTitle("foo");
    experiment.updateTrial(trial);

    assertThat(trial.getLabelCount()).isEqualTo(1);
    assertThat(experiment.getChanges()).hasSize(6);

    Label label2 = trial.getLabels().get(0);
    label2.setTimestamp(10);
    trial.updateLabel(experiment, label2);
    assertThat(trial.getLabels().get(0).getTimeStamp()).isEqualTo(10);
    assertThat(experiment.getChanges()).hasSize(7);

    label2.setTimestamp(20);
    trial.updateLabelWithoutSorting(experiment, label2);
    assertThat(trial.getLabels().get(0).getTimeStamp()).isEqualTo(20);
    assertThat(experiment.getChanges()).hasSize(8);
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

    assertThat(sync.getImageDownloads()).isEmpty();
    assertThat(sync.getImageUploads()).isEmpty();

    assertThat(sync.getTrialDownloads()).isEmpty();
    assertThat(sync.getTrialUploads()).isEmpty();

    assertThat(experimentServer.getTitle()).isEqualTo("Title");
    assertThat(experimentServer.getChanges()).hasSize(1);
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

    assertThat(sync.getImageDownloads()).isEmpty();
    assertThat(sync.getImageUploads()).isEmpty();

    assertThat(sync.getTrialDownloads()).isEmpty();
    assertThat(sync.getTrialUploads()).isEmpty();

    assertThat(experimentServer.getTitle()).isEqualTo("Title2");
    assertThat(experimentServer.getChanges()).hasSize(2);
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

    assertThat(experimentServer.getTitle()).isEqualTo("Title2");
    assertThat(experimentServer.getChanges()).hasSize(2);
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

    assertThat(experimentServer.getTitle()).isEqualTo("Title3");
    assertThat(experimentServer.getChanges()).hasSize(3);
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

    assertThat(experimentServer.getTitle()).isEqualTo("Title2 / Title3");
    assertThat(experimentServer.getChanges()).hasSize(3);
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).isEmpty();

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(0);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(sync.getImageDownloads()).hasSize(1);

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).isEmpty();

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(0);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);
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

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(1);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(sync.getImageDownloads()).hasSize(1);

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    assertThat(experimentClient.getLabel(label2.getLabelId()).getCaptionText())
        .isEqualTo(experimentServer.getLabel(label2.getLabelId()).getCaptionText());
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

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(0);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(sync.getImageDownloads()).isEmpty();
    assertThat(sync.getImageUploads()).isEmpty();

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(experimentClient.getLabelCount()).isEqualTo(0);
    assertThat(experimentServer.getLabelCount()).isEqualTo(0);
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

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(1);

    experimentClient.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    assertThat(experimentClient.getChanges()).hasSize(3);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(0);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(sync.getImageDownloads()).isEmpty();
    assertThat(sync.getImageUploads()).isEmpty();

    assertThat(experimentClient.getChanges()).hasSize(3);
    assertThat(experimentServer.getChanges()).hasSize(3);

    assertThat(experimentClient.getLabelCount()).isEqualTo(0);
    assertThat(experimentServer.getLabelCount()).isEqualTo(0);
  }

  @Test
  public void testMergeExperimentsTrialAddOnly() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial = Trial.newTrial(1, new SensorLayout[0], null, getContext());

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    experimentClient.addTrial(trial);

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).isEmpty();

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(0);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(1);
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    trial.setTitle("Bar");
    experimentClient.updateTrial(trial);

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(1);

    Trial toDelete = experimentServer.getTrial(trial.getTrialId());
    experimentServer.deleteTrialOnlyForTesting(toDelete);

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(0);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(3);

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(0);
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

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(clientTrial.getLabelCount()).isEqualTo(1);
    assertThat(trial.getLabelCount()).isEqualTo(0);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(sync.getImageDownloads()).hasSize(1);
    assertThat(sync.getImageUploads()).isEmpty();

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(clientTrial.getLabelCount()).isEqualTo(1);
    assertThat(trial.getLabelCount()).isEqualTo(1);
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(serverTrial.getLabelCount()).isEqualTo(1);
    assertThat(trial.getLabelCount()).isEqualTo(1);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(sync.getImageDownloads()).isEmpty();
    assertThat(sync.getImageUploads()).hasSize(1);

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(serverTrial.getLabelCount()).isEqualTo(1);
    assertThat(trial.getLabelCount()).isEqualTo(1);
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

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(clientTrial.getLabelCount()).isEqualTo(1);
    assertThat(trial.getLabelCount()).isEqualTo(1);

    assertThat(experimentServer.getLabel(label.getLabelId()).getCaptionText()).isEqualTo("caption");

    assertThat(experimentClient.getLabel(label.getLabelId()).getCaptionText())
        .isEqualTo("caption2");

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(clientTrial.getLabelCount()).isEqualTo(1);
    assertThat(trial.getLabelCount()).isEqualTo(1);

    assertThat(experimentServer.getLabel(label.getLabelId()).getCaptionText())
        .isEqualTo("caption2");

    assertThat(experimentClient.getLabel(label.getLabelId()).getCaptionText())
        .isEqualTo("caption2");
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

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(clientTrial.getLabelCount()).isEqualTo(0);
    assertThat(trial.getLabelCount()).isEqualTo(1);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(clientTrial.getLabelCount()).isEqualTo(0);
    assertThat(trial.getLabelCount()).isEqualTo(0);
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

    assertThat(trial.getLabelCount()).isEqualTo(1);

    trial.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    assertThat(experimentClient.getChanges()).hasSize(3);
    assertThat(experimentServer.getChanges()).isEmpty();

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(0);

    assertThat(trial.getLabelCount()).isEqualTo(0);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentClient.getChanges()).hasSize(3);
    assertThat(experimentServer.getChanges()).hasSize(3);

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(1);

    assertThat(experimentServer.getTrial(trial.getTrialId()).getLabelCount()).isEqualTo(0);
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

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

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentServer.getChanges()).hasSize(4);
    assertThat(experimentServer.getLabelCount()).isEqualTo(2);
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    experimentClient.deleteLabelAndReturnAssetDeleter(experimentClient, label, getAppAccount());

    Label label3 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption3 = Caption.newBuilder().setText("caption3").build();
    label3.setCaption(caption3);

    experimentServer.updateLabel(experimentServer, label3);

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(experimentClient.getLabelCount()).isEqualTo(0);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentServer.getChanges()).hasSize(3);
    assertThat(experimentServer.getLabelCount()).isEqualTo(0);
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    Label label2 =
        Label.fromUuidAndValue(1, label.getLabelId(), ValueType.TEXT, label.getTextLabelValue());
    Caption caption2 = Caption.newBuilder().setText("caption2").build();
    label2.setCaption(caption2);

    experimentClient.updateLabel(experimentClient, label2);

    experimentServer.deleteLabelAndReturnAssetDeleter(experimentServer, label, getAppAccount());

    assertThat(experimentClient.getChanges()).hasSize(2);
    assertThat(experimentServer.getChanges()).hasSize(2);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(0);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentServer.getChanges()).hasSize(3);
    assertThat(experimentServer.getLabelCount()).isEqualTo(0);
  }

  @Test
  public void testMergeDeletedTrialsNotUploaded() {
    Experiment experimentServer = Experiment.newExperiment(1, "experimentId", 1);

    Trial trial1 = Trial.newTrial(1, new SensorLayout[0], null, getContext());
    experimentServer.addTrial(trial1);

    Experiment experimentClient =
        Experiment.fromExperiment(
            experimentServer.getExperimentProto(), experimentServer.getExperimentOverview());

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    Trial trial2 = Trial.newTrial(1, new SensorLayout[0], null, getContext());
    experimentServer.addTrial(trial2);

    experimentServer.deleteTrialOnlyForTesting(trial1);

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(3);

    assertThat(experimentClient.getTrialCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(1);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(sync.getTrialUploads()).hasSize(1);
    assertThat(sync.getTrialUploads().contains(trial2.getTrialId())).isTrue();
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

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentClient.getTrialCount()).isEqualTo(0);

    assertThat(experimentServer.getLabelCount()).isEqualTo(0);
    assertThat(experimentServer.getTrialCount()).isEqualTo(1);

    FileSyncCollection sync =
        experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), true);

    assertThat(experimentClient.getChanges()).hasSize(1);
    assertThat(experimentServer.getChanges()).hasSize(1);

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentClient.getTrialCount()).isEqualTo(0);

    assertThat(experimentServer.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getTrialCount()).isEqualTo(0);

    assertThat(sync.getTrialUploads()).isEmpty();
    assertThat(sync.getTrialDownloads()).isEmpty();
    assertThat(sync.getImageUploads()).isEmpty();
    assertThat(sync.getImageDownloads()).isEmpty();
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

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

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

    assertThat(experimentClient.getLabelCount()).isEqualTo(1);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);

    experimentServer.mergeFrom(experimentClient, getContext(), getAppAccount(), false);

    assertThat(experimentServer.getChanges()).hasSize(5);
    assertThat(experimentServer.getLabelCount()).isEqualTo(1);
  }

}
