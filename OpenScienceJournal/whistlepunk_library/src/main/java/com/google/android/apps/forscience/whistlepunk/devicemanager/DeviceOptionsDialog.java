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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

/** Presents dialog options for external devices. */
public class DeviceOptionsDialog extends DialogFragment {
  private static final String TAG = "DeviceOptionsDialog";
  private static final String KEY_ACCOUNT_KEY = "account_key";
  private static final String KEY_EXPERIMENT_ID = "experiment_id";
  private static final String KEY_SENSOR_ID = "sensor_id";
  private static final String KEY_SETTINGS_INTENT = "settings_intent";
  private static final String KEY_SHOW_FORGET = "show_forget";
  public static final DeviceOptionsListener NULL_LISTENER =
      new DeviceOptionsListener() {
        @Override
        public void onExperimentSensorReplaced(String oldSensorId, String newSensorId) {
          // do nothing
        }

        @Override
        public void onRemoveSensorFromExperiment(String experimentId, String sensorId) {
          // do nothing
        }
      };

  private DataController dataController;
  private DeviceOptionsViewController viewController;

  public static DeviceOptionsDialog newInstance(
      AppAccount appAccount,
      String experimentId,
      String sensorId,
      PendingIntent externalSettingsIntent,
      boolean showForgetButton) {
    Bundle args = new Bundle();
    args.putString(KEY_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(KEY_EXPERIMENT_ID, experimentId);
    args.putString(KEY_SENSOR_ID, sensorId);
    args.putParcelable(KEY_SETTINGS_INTENT, externalSettingsIntent);
    args.putBoolean(KEY_SHOW_FORGET, showForgetButton);

    DeviceOptionsDialog dialog = new DeviceOptionsDialog();
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_DEVICE_OPTIONS);
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  public Dialog onCreateDialog(final Bundle savedInstanceState) {
    final String sensorId = getArguments().getString(KEY_SENSOR_ID);
    final PendingIntent settingsIntent = getArguments().getParcelable(KEY_SETTINGS_INTENT);
    boolean showForget = getArguments().getBoolean(KEY_SHOW_FORGET);
    DialogInterface.OnClickListener onOK;
    View view;
    if (settingsIntent == null) {
      // TODO: this is cheating, we're assuming that if there's no settings intent, we
      // can treat this like a BLE controller.
      setupControllers();
      dataController.getExternalSensorById(
          sensorId,
          new LoggingConsumer<ExternalSensorSpec>(
              TAG, "Load external sensor with ID = " + sensorId) {
            @Override
            public void success(ExternalSensorSpec sensor) {
              viewController.setSensor(sensorId, sensor, savedInstanceState);
            }
          });
      onOK =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              viewController.commit(getOptionsListener());
            }
          };
      view = viewController.getView();
    } else {
      onOK =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              dialog.dismiss();
            }
          };

      view = LayoutInflater.from(getActivity()).inflate(R.layout.api_device_options_dialog, null);
      view.findViewById(R.id.button)
          .setOnClickListener(
              new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                  try {
                    settingsIntent.send();
                  } catch (PendingIntent.CanceledException e) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                      Log.e(TAG, "settings intent cancelled", e);
                    }
                  }
                }
              });
    }
    AlertDialog.Builder builder =
        new AlertDialog.Builder(getActivity())
            .setView(view)
            .setTitle(R.string.title_activity_sensor_settings)
            .setPositiveButton(android.R.string.ok, onOK);
    if (showForget) {
      builder.setNegativeButton(
          R.string.external_devices_settings_forget,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              removeDeviceFromExperiment();
            }
          });
    }
    return builder.create();
  }

  private void setupControllers() {
    dataController = AppSingleton.getInstance(getActivity()).getDataController(getAppAccount());
    viewController =
        new DeviceOptionsViewController(getActivity(), dataController, getExperimentId());
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (viewController != null) {
      viewController.onSaveInstanceState(outState);
    }
  }

  private void removeDeviceFromExperiment() {
    if (getActivity() instanceof DeviceOptionsListener) {
      ((DeviceOptionsListener) getActivity())
          .onRemoveSensorFromExperiment(getExperimentId(), getArguments().getString(KEY_SENSOR_ID));
    }
  }

  private AppAccount getAppAccount() {
    return WhistlePunkApplication.getAccount(getContext(), getArguments(), KEY_ACCOUNT_KEY);
  }

  private String getExperimentId() {
    return getArguments().getString(KEY_EXPERIMENT_ID);
  }

  private DeviceOptionsListener getOptionsListener() {
    if (getActivity() instanceof DeviceOptionsListener) {
      return (DeviceOptionsListener) getActivity();
    } else {
      return null;
    }
  }
}
