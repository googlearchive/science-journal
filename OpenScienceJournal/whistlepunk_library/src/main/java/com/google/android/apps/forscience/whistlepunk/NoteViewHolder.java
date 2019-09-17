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

package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue.SnapshotLabelValue;

/** ViewHolder and helper methods for showing notes in a list. */
public class NoteViewHolder extends RecyclerView.ViewHolder {
  public TextView durationText;
  public ImageButton menuButton;
  public ImageView captionIcon;
  public View captionView;
  public TextView captionTextView;
  public TextView text;
  public ImageView image;
  public ViewGroup valuesList;
  public RelativeTimeTextView relativeTimeView;

  public NoteViewHolder(View v) {
    super(v);
    durationText = (TextView) v.findViewById(R.id.time_text);
    menuButton = (ImageButton) v.findViewById(R.id.note_menu_button);
    text = (TextView) v.findViewById(R.id.note_text);
    image = (ImageView) v.findViewById(R.id.note_image);
    captionIcon = (ImageView) v.findViewById(R.id.edit_icon);
    captionView = itemView.findViewById(R.id.caption_section);
    captionTextView = (TextView) itemView.findViewById(R.id.caption);
    valuesList = (ViewGroup) itemView.findViewById(R.id.snapshot_values_list);
    relativeTimeView = (RelativeTimeTextView) itemView.findViewById(R.id.relative_time_text);
    CardView cardView = v.findViewById(R.id.card_view);
    if (Flags.showActionBar()) {
      cardView.setUseCompatPadding(true);
      cardView.setCardElevation(0);
      cardView.setBackground(
          cardView.getResources().getDrawable(R.drawable.card_view_with_hairline_border));
    }
  }

  public void setNote(
      Label label, AppAccount appAccount, String experimentId, boolean claimExperimentsMode) {
    if (label.getType() == GoosciLabel.Label.ValueType.TEXT) {
      String text = label.getTextLabelValue().getText();
      image.setVisibility(View.GONE);
      // No caption, and no caption edit button.
      captionIcon.setVisibility(View.GONE);
      captionView.setVisibility(View.GONE);
      if (!TextUtils.isEmpty(text)) {
        this.text.setText(text);
        this.text.setTextColor(this.text.getResources().getColor(R.color.text_color_black));
      } else {
        this.text.setText(
            this.text.getResources().getString(R.string.pinned_note_placeholder_text));
        this.text.setTextColor(this.text.getResources().getColor(R.color.text_color_light_grey));
      }
    } else {
      text.setVisibility(View.GONE);
      // Deal with the caption, which is applicable to everything but text labels.
      setupCaption(label.getCaptionText(), claimExperimentsMode);
    }

    if (label.getType() == GoosciLabel.Label.ValueType.PICTURE) {
      GoosciPictureLabelValue.PictureLabelValue labelValue = label.getPictureLabelValue();
      image.setVisibility(View.VISIBLE);
      PictureUtils.loadExperimentImage(
          image.getContext(), image, appAccount, experimentId, labelValue.getFilePath(), true);
    } else {
      PictureUtils.clearImage(image);
      image.setVisibility(View.GONE);
    }

    if (label.getType() != GoosciLabel.Label.ValueType.SENSOR_TRIGGER
        && label.getType() != GoosciLabel.Label.ValueType.SNAPSHOT) {
      valuesList.setVisibility(View.GONE);
    } else {
      if (label.getType() == GoosciLabel.Label.ValueType.SENSOR_TRIGGER) {
        loadTriggerIntoList(valuesList, label, appAccount);
      }
      if (label.getType() == GoosciLabel.Label.ValueType.SNAPSHOT) {
        loadSnapshotsIntoList(valuesList, label, appAccount);
      }
    }

    ColorUtils.colorDrawable(
        captionIcon.getContext(), captionIcon.getDrawable(), R.color.text_color_light_grey);
    ColorUtils.colorDrawable(
        menuButton.getContext(), menuButton.getDrawable(), R.color.text_color_light_grey);
  }

  private void setupCaption(String caption, boolean claimExperimentsMode) {
    if (!TextUtils.isEmpty(caption)) {
      captionView.setVisibility(View.VISIBLE);
      captionTextView.setText(caption);
      captionIcon.setVisibility(View.GONE);
    } else {
      captionView.setVisibility(View.GONE);
      captionIcon.setVisibility(claimExperimentsMode ? View.GONE : View.VISIBLE);
    }
  }

  public static void loadSnapshotsIntoList(
      ViewGroup valuesList, Label label, AppAccount appAccount) {
    Context context = valuesList.getContext();
    SnapshotLabelValue snapshotLabelValue = label.getSnapshotLabelValue();

    valuesList.setVisibility(View.VISIBLE);
    // Make sure it has the correct number of views, re-using as many as possible.
    int childCount = valuesList.getChildCount();
    int snapshotsCount = snapshotLabelValue.getSnapshotsCount();
    if (childCount < snapshotsCount) {
      for (int i = 0; i < snapshotsCount - childCount; i++) {
        LayoutInflater.from(context).inflate(R.layout.snapshot_value_details, valuesList);
      }
    } else if (childCount > snapshotsCount) {
      valuesList.removeViews(0, childCount - snapshotsCount);
    }

    SensorAppearanceProvider sensorAppearanceProvider =
        AppSingleton.getInstance(context).getSensorAppearanceProvider(appAccount);

    String valueFormat = context.getResources().getString(R.string.data_with_units);
    for (int i = 0; i < snapshotsCount; i++) {
      GoosciSnapshotValue.SnapshotLabelValue.SensorSnapshot snapshot =
          snapshotLabelValue.getSnapshots(i);
      ViewGroup snapshotLayout = (ViewGroup) valuesList.getChildAt(i);

      GoosciSensorAppearance.BasicSensorAppearance appearance =
          snapshot.getSensor().getRememberedAppearance();
      TextView sensorName = (TextView) snapshotLayout.findViewById(R.id.sensor_name);
      sensorName.setCompoundDrawablesRelative(null, null, null, null);
      sensorName.setText(appearance.getName());
      String value =
          BuiltInSensorAppearance.formatValue(
              snapshot.getValue(), appearance.getPointsAfterDecimal());
      ((TextView) snapshotLayout.findViewById(R.id.sensor_value))
          .setText(String.format(valueFormat, value, appearance.getUnits()));

      loadLargeDrawable(appearance, sensorAppearanceProvider, snapshotLayout, snapshot.getValue());
    }
  }

  public static void loadTriggerIntoList(ViewGroup valuesList, Label label, AppAccount appAccount) {
    Context context = valuesList.getContext();

    valuesList.setVisibility(View.VISIBLE);
    GoosciSensorTriggerLabelValue.SensorTriggerLabelValue labelValue =
        label.getSensorTriggerLabelValue();

    // Make sure there is exactly 1 view in the list for a trigger note.
    // This is necessary because valuesList is also used for snapshots, which may have multiple
    // views in the list.
    if (valuesList.getChildCount() == 0) {
      LayoutInflater.from(context).inflate(R.layout.snapshot_value_details, valuesList);
    } else if (valuesList.getChildCount() > 1) {
      valuesList.removeViews(1, valuesList.getChildCount() - 1);
    }

    TextView sensorName = (TextView) valuesList.findViewById(R.id.sensor_name);
    Drawable black = context.getResources().getDrawable(R.drawable.ic_label_black_18dp);
    Drawable drawable =
        ColorUtils.colorBlackDrawable(valuesList.getContext(), black, R.color.color_accent);
    sensorName.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null);
    String triggerWhenText =
        context.getResources()
            .getStringArray(R.array.trigger_when_list_note_text)[
            labelValue.getTriggerInformation().getTriggerWhen().getNumber()];
    GoosciSensorAppearance.BasicSensorAppearance appearance =
        labelValue.getSensor().getRememberedAppearance();

    if (appearance == null) {
      // TODO: when is this necessary?  Can we prevent it?  Remove this workaround after
      //  b/64115986 is fixed?
      appearance = createDefaultAppearance();
    }

    sensorName.setText(
        context
            .getResources()
            .getString(R.string.trigger_label_name_header, appearance.getName(), triggerWhenText));

    String valueFormat = context.getResources().getString(R.string.data_with_units);
    String value =
        BuiltInSensorAppearance.formatValue(
            labelValue.getTriggerInformation().getValueToTrigger(),
            appearance.getPointsAfterDecimal());
    ((TextView) valuesList.findViewById(R.id.sensor_value))
        .setText(String.format(valueFormat, value, appearance.getUnits()));
    loadLargeDrawable(
        appearance,
        AppSingleton.getInstance(context).getSensorAppearanceProvider(appAccount),
        valuesList,
        labelValue.getTriggerInformation().getValueToTrigger());
  }

  private static GoosciSensorAppearance.BasicSensorAppearance createDefaultAppearance() {
    return GoosciSensorAppearance.BasicSensorAppearance.getDefaultInstance();
  }

  private static void loadLargeDrawable(
      GoosciSensorAppearance.BasicSensorAppearance appearance,
      SensorAppearanceProvider appearanceProvider,
      ViewGroup layout,
      double value) {
    SensorAppearance sa = getSensorAppearance(appearance, appearanceProvider);
    if (sa != null) {
      SensorAnimationBehavior behavior = sa.getSensorAnimationBehavior();
      behavior.initializeLargeIcon(
          (RelativeLayout) layout.findViewById(R.id.large_icon_container), value);
    }
  }

  private static SensorAppearance getSensorAppearance(
      GoosciSensorAppearance.BasicSensorAppearance appearance,
      SensorAppearanceProvider appearanceProvider) {
    GoosciIcon.IconPath iconPath = appearance.getIconPath();
    switch (iconPath.getType()) {
      case BUILTIN:
        return appearanceProvider.getAppearance(iconPath.getPathString());
      case LEGACY_ANDROID_BLE:
        return SensorTypeProvider.getSensorAppearance(
            Integer.valueOf(iconPath.getPathString()), "");
      case PROTO:
        return new ProtoSensorAppearance(appearance);
      case MKRSCI_ANDROID_BLE:
        return MkrSciBleSensorAppearance.get(iconPath.getPathString());
    }
    return new ProtoSensorAppearance(appearance);
  }
}
