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
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue;

/**
 * ViewHolder and helper methods for showing notes in a list.
 */
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
    }

    public void setNote(Label label, String experimentId) {
        if (label.getType() == GoosciLabel.Label.TEXT) {
            String text = label.getTextLabelValue().text;
            image.setVisibility(View.GONE);
            // No caption, and no caption edit button.
            captionIcon.setVisibility(View.GONE);
            captionView.setVisibility(View.GONE);
            if (!TextUtils.isEmpty(text)) {
                this.text.setText(text);
                this.text.setTextColor(this.text.getResources().getColor(R.color.text_color_black));
            } else {
                this.text.setText(this.text.getResources().getString(
                        R.string.pinned_note_placeholder_text));
                this.text.setTextColor(this.text.getResources().getColor(
                        R.color.text_color_light_grey));
            }
        } else {
            text.setVisibility(View.GONE);
            // Deal with the caption, which is applicable to everything but text labels.
            setupCaption(label.getCaptionText());
        }

        if (label.getType() == GoosciLabel.Label.PICTURE) {
            GoosciPictureLabelValue.PictureLabelValue labelValue = label.getPictureLabelValue();
            image.setVisibility(View.VISIBLE);
            PictureUtils.loadExperimentImage(image.getContext(), image, experimentId,
                    labelValue.filePath);
        } else {
            PictureUtils.clearImage(image);
            image.setVisibility(View.GONE);
        }

        if (label.getType() != GoosciLabel.Label.SENSOR_TRIGGER &&
            label.getType() != GoosciLabel.Label.SNAPSHOT) {
            valuesList.setVisibility(View.GONE);
        } else {
            if (label.getType() == GoosciLabel.Label.SENSOR_TRIGGER) {
                loadTriggerIntoList(valuesList, label);
            }
            if (label.getType() == GoosciLabel.Label.SNAPSHOT) {
                loadSnapshotsIntoList(valuesList, label);
            }
        }

        ColorUtils.colorDrawable(captionIcon.getContext(),
                captionIcon.getDrawable(), R.color.text_color_light_grey);
        ColorUtils.colorDrawable(menuButton.getContext(),
                menuButton.getDrawable(), R.color.text_color_light_grey);

    }

    private void setupCaption(String caption) {
        if (!TextUtils.isEmpty(caption)) {
            captionView.setVisibility(View.VISIBLE);
            captionTextView.setText(caption);
            captionIcon.setVisibility(View.GONE);
        } else {
            captionView.setVisibility(View.GONE);
            captionIcon.setVisibility(View.VISIBLE);
        }
    }

    public static void loadSnapshotsIntoList(ViewGroup valuesList, Label label) {
        Context context = valuesList.getContext();
        GoosciSnapshotValue.SnapshotLabelValue.SensorSnapshot[] snapshots =
                label.getSnapshotLabelValue().snapshots;

        valuesList.setVisibility(View.VISIBLE);
        // Make sure it has the correct number of views, re-using as many as possible.
        int childCount = valuesList.getChildCount();
        if (childCount < snapshots.length) {
            for (int i = 0; i < snapshots.length - childCount; i++) {
                LayoutInflater.from(context).inflate(R.layout.snapshot_value_details, valuesList);
            }
        } else if (childCount > snapshots.length) {
            valuesList.removeViews(0, childCount - snapshots.length);
        }

        String valueFormat = context.getResources().getString(
                R.string.data_with_units);
        for (int i = 0; i < snapshots.length; i++) {
            GoosciSnapshotValue.SnapshotLabelValue.SensorSnapshot snapshot = snapshots[i];
            ViewGroup snapshotLayout = (ViewGroup) valuesList.getChildAt(i);

            GoosciSensorAppearance.BasicSensorAppearance appearance =
                    snapshot.sensor.rememberedAppearance;
            TextView sensorName = (TextView) snapshotLayout.findViewById(R.id.sensor_name);
            sensorName.setCompoundDrawablesRelative(null, null, null, null);
            sensorName.setText(appearance.name);
            String value = BuiltInSensorAppearance.formatValue(snapshot.value,
                    appearance.pointsAfterDecimal);
            ((TextView) snapshotLayout.findViewById(R.id.sensor_value)).setText(
                    String.format(valueFormat, value, appearance.units));

            loadLargeDrawable(appearance,
                    AppSingleton.getInstance(context).getSensorAppearanceProvider(),
                    snapshotLayout);
        }
    }

    public static void loadTriggerIntoList(ViewGroup valuesList, Label label) {
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
        String triggerWhenText = context.getResources().getStringArray(
                R.array.trigger_when_list_note_text)[labelValue.triggerInformation.triggerWhen];
        GoosciSensorAppearance.BasicSensorAppearance appearance =
                labelValue.sensor.rememberedAppearance;

        if (appearance == null) {
            // TODO: when is this necessary?  Can we prevent it?  Remove this workaround after
            //  b/64115986 is fixed?
            appearance = createDefaultAppearance();
        }

        sensorName.setText(context.getResources().getString(R.string.trigger_label_name_header,
                appearance.name, triggerWhenText));

        String valueFormat = context.getResources().getString(R.string.data_with_units);
        String value = BuiltInSensorAppearance.formatValue(
                labelValue.triggerInformation.valueToTrigger,
                appearance.pointsAfterDecimal);
        ((TextView) valuesList.findViewById(R.id.sensor_value)).setText(
                String.format(valueFormat, value,
                        appearance.units));
        loadLargeDrawable(appearance,
                AppSingleton.getInstance(context).getSensorAppearanceProvider(), valuesList);
    }

    private static GoosciSensorAppearance.BasicSensorAppearance createDefaultAppearance() {
        return new GoosciSensorAppearance.BasicSensorAppearance();
    }

    private static void loadLargeDrawable(GoosciSensorAppearance.BasicSensorAppearance appearance,
            SensorAppearanceProvider appearanceProvider, ViewGroup layout) {
        GoosciIcon.IconPath iconPath = appearance.largeIconPath;
        SensorAppearance sa = getSensorAppearance(iconPath, appearanceProvider);
        if (sa != null) {
            SensorAnimationBehavior behavior = sa.getSensorAnimationBehavior();
            behavior.initializeLargeIcon(((ImageView) layout.findViewById(R.id.large_icon)));
        }
    }

    private static SensorAppearance getSensorAppearance(GoosciIcon.IconPath iconPath,
            SensorAppearanceProvider appearanceProvider) {
        if (iconPath == null) {
            return null;
        }
        switch (iconPath.type) {
            case GoosciIcon.IconPath.BUILTIN:
                return appearanceProvider.getAppearance(iconPath.pathString);
            case GoosciIcon.IconPath.LEGACY_ANDROID_BLE:
                return SensorTypeProvider.getSensorAppearance(Integer.valueOf(iconPath.pathString),
                        "");
        }
        return null;
    }
}
