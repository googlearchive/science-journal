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

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import com.google.android.apps.forscience.whistlepunk.audiogen.SonificationTypeAdapterFactory;
import java.util.Arrays;

/** DialogFragment to display audio settings to the user. */
public class AudioSettingsDialog extends DialogFragment {

  public interface AudioSettingsDialogListener {
    void onAudioSettingsPreview(String[] previewSonificationTypes, String[] sensorIds);

    void onAudioSettingsApplied(String[] newSonificationTypes, String[] sensorIds);

    void onAudioSettingsCanceled(String[] originalSonificationTypes, String[] sensorIds);
  }

  public static final String TAG = "AudioSettingsDialog";
  private static final String KEY_SONIFICATION_TYPES = "sonification_types";
  private static final String KEY_SENSOR_ID = "sensor_id";
  private static final String KEY_ACTIVE_SENSOR_INDEX = "active_sensor_index";

  private String[] mSensorIds;
  private String[] mOriginalSonificationTypes;
  private String[] mSonificationTypes;
  private int mActiveSensorIndex;

  /**
   * Gets a new instance of the AudioSettingsDialog.
   *
   * @param sonificationTypes A list of currently selected sonification types.
   * @param sensorIds A list of sensor IDs. This must be in the same order as the sonification
   *     types: This class assumes that each sensorID has a matching sonificationType
   * @param activeSensorIndex The currently active sensor index.
   * @return
   */
  public static AudioSettingsDialog newInstance(
      String[] sonificationTypes, String[] sensorIds, int activeSensorIndex) {
    AudioSettingsDialog dialog = new AudioSettingsDialog();
    Bundle args = new Bundle();
    args.putStringArray(KEY_SONIFICATION_TYPES, sonificationTypes);
    args.putStringArray(KEY_SENSOR_ID, sensorIds);
    args.putInt(KEY_ACTIVE_SENSOR_INDEX, activeSensorIndex);
    dialog.setArguments(args);
    return dialog;
  }

  public AudioSettingsDialog() {}

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putStringArray(KEY_SONIFICATION_TYPES, mSonificationTypes);
    outState.putInt(KEY_ACTIVE_SENSOR_INDEX, mActiveSensorIndex);
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    if (getParentFragment() != null) {
      ((AudioSettingsDialogListener) getParentFragment())
          .onAudioSettingsCanceled(mOriginalSonificationTypes, mSensorIds);
    }
  }

  @Override
  public AlertDialog onCreateDialog(Bundle savedInstanceState) {
    mOriginalSonificationTypes = getArguments().getStringArray(KEY_SONIFICATION_TYPES);
    if (savedInstanceState != null) {
      // Use the most recent version if possible.
      mSonificationTypes = savedInstanceState.getStringArray(KEY_SONIFICATION_TYPES);
      mActiveSensorIndex = savedInstanceState.getInt(KEY_ACTIVE_SENSOR_INDEX);
    } else {
      // Create a deep copy of the original sonification types.
      mSonificationTypes = new String[mOriginalSonificationTypes.length];
      System.arraycopy(
          mOriginalSonificationTypes, 0, mSonificationTypes, 0, mOriginalSonificationTypes.length);
      mActiveSensorIndex = getArguments().getInt(KEY_ACTIVE_SENSOR_INDEX);
    }
    mSensorIds = getArguments().getStringArray(KEY_SENSOR_ID);

    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    View rootView =
        LayoutInflater.from(getActivity()).inflate(R.layout.audio_settings_dialog, null);
    alertDialog.setView(rootView);
    Spinner spinner = (Spinner) rootView.findViewById(R.id.audio_settings_sensor_selector);
    final ListView typeList =
        (ListView) rootView.findViewById(R.id.audio_settings_type_selector_group);

    alertDialog.setTitle(getResources().getString(R.string.menu_item_audio_settings));

    final String[] sonificationTypesHumanReadable;

    // Add additional audio options only if the testing options setting is enabled.
    if (DevOptionsFragment.getEnableAdditionalSonificationTypes(getActivity())) {
      String[] humanReadableDev =
          getActivity().getResources().getStringArray(R.array.sonification_types_dev);
      String[] humanReadableProd =
          getActivity().getResources().getStringArray(R.array.sonification_types_prod);
      sonificationTypesHumanReadable =
          Arrays.copyOf(humanReadableProd, humanReadableProd.length + humanReadableDev.length);
      System.arraycopy(
          humanReadableDev,
          0,
          sonificationTypesHumanReadable,
          humanReadableProd.length,
          humanReadableDev.length);
    } else {
      sonificationTypesHumanReadable =
          getActivity().getResources().getStringArray(R.array.sonification_types_prod);
    }

    final int initialSelectedIndex =
        findSelection(
            SonificationTypeAdapterFactory.SONIFICATION_TYPES,
            mSonificationTypes[mActiveSensorIndex]);

    typeList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

    final ArrayAdapter<String> typeAdapter =
        new ArrayAdapter<>(
            getActivity(), R.layout.dialog_single_choice_item, sonificationTypesHumanReadable);
    typeList.setAdapter(typeAdapter);
    typeList.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            mSonificationTypes[mActiveSensorIndex] =
                SonificationTypeAdapterFactory.SONIFICATION_TYPES[position];
            if (getParentFragment() != null) {
              ((AudioSettingsDialogListener) getParentFragment())
                  .onAudioSettingsPreview(mSonificationTypes, mSensorIds);
            }
          }
        });
    selectItem(typeList, initialSelectedIndex);

    if (mSensorIds.length <= 1) {
      spinner.setVisibility(View.GONE);
      rootView.findViewById(R.id.audio_settings_sensor_selector_title).setVisibility(View.GONE);
    } else {
      String[] sensorNames = new String[mSensorIds.length];
      SensorAppearanceProvider appearanceProvider =
          AppSingleton.getInstance(getActivity()).getSensorAppearanceProvider();
      for (int i = 0; i < mSensorIds.length; i++) {
        sensorNames[i] = appearanceProvider.getAppearance(mSensorIds[i]).getName(getActivity());
      }
      final ArrayAdapter<String> sensorAdapter =
          new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, sensorNames);
      sensorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      spinner.setAdapter(sensorAdapter);
      spinner.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
              mActiveSensorIndex = position;
              // Look up the sonification type of this newly active sensor.
              String newActiveSonificationType = mSonificationTypes[mActiveSensorIndex];
              int typePosition =
                  findSelection(
                      SonificationTypeAdapterFactory.SONIFICATION_TYPES, newActiveSonificationType);
              selectItem(typeList, typePosition);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
          });
      spinner.setSelection(mActiveSensorIndex);
    }

    alertDialog.setPositiveButton(
        android.R.string.ok,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (getParentFragment() != null) {
              ((AudioSettingsDialogListener) getParentFragment())
                  .onAudioSettingsApplied(mSonificationTypes, mSensorIds);
            }
          }
        });
    alertDialog.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.cancel();
          }
        });
    alertDialog.setCancelable(true);
    AlertDialog dialog = alertDialog.create();
    return dialog;
  }

  private void selectItem(final ListView typeList, final int index) {
    typeList.post(
        new Runnable() {
          @Override
          public void run() {
            typeList.setSelection(index);
            typeList.setItemChecked(index, true);
            typeList.clearFocus();
          }
        });
  }

  private int findSelection(String[] typeAliases, String sonificationType) {
    for (int i = 0; i < typeAliases.length; i++) {
      if (typeAliases[i].equals(sonificationType)) {
        return i;
      }
    }
    return 0;
  }
}
