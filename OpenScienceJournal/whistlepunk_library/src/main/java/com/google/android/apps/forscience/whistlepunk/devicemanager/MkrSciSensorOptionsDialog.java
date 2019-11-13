package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.MkrSciBleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor;
import java.util.Objects;

/** Options dialog for a MkrSciSensor */
public class MkrSciSensorOptionsDialog extends DialogFragment {

  private static final String TAG = "MkrSciSensorOptionsDialog";

  private static final String KEY_ACCOUNT_KEY = "account_key";
  private static final String KEY_EXPERIMENT_ID = "experiment_id";
  private static final String KEY_SENSOR_ID = "sensor_id";
  private static final String KEY_AVAILABLE_OPTIONS = "available_options";
  private static final String KEY_DEFAULT_OPTION = "def_option";

  private RadioButton[] radioButtons;

  private DeviceOptionsListener listener;

  private DataController dataController;

  private MkrSciBleSensorSpec sensorSpec;

  public static MkrSciSensorOptionsDialog newInstance(
      AppAccount appAccount,
      String experimentId,
      String sensorId,
      String[] options,
      int defChecked) {
    Bundle args = new Bundle();
    args.putString(KEY_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(KEY_EXPERIMENT_ID, experimentId);
    args.putString(KEY_SENSOR_ID, sensorId);
    args.putStringArray(KEY_AVAILABLE_OPTIONS, options);
    args.putInt(KEY_DEFAULT_OPTION, defChecked);
    MkrSciSensorOptionsDialog dialog = new MkrSciSensorOptionsDialog();
    dialog.setArguments(args);
    return dialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    dataController = AppSingleton.getInstance(getActivity()).getDataController(getAppAccount());
    listener = getOptionsListener();

    final Bundle args = getArguments();
    final String experimentId = args.getString(KEY_EXPERIMENT_ID);
    final String sensorId = args.getString(KEY_SENSOR_ID);
    final String[] optionIds = args.getStringArray(KEY_AVAILABLE_OPTIONS);
    final int defChecked = args.getInt(KEY_DEFAULT_OPTION);
    assert optionIds != null;

    final int size = optionIds.length;

    final View view =
        LayoutInflater.from(getActivity()).inflate(R.layout.mkrsci_sensor_options_dialog, null);
    final RadioGroup radioGroup = view.findViewById(R.id.radio_group);
    radioButtons = new RadioButton[size];

    for (int i = 0; i < size; i++) {
      final String optionId = optionIds[i];
      int labelId;
      switch (optionIds[i]) {
        case MkrSciBleSensor.HANDLER_TEMPERATURE_CELSIUS:
          labelId = R.string.mkrsci_input_sensor_temperature_c;
          break;
        case MkrSciBleSensor.HANDLER_TEMPERATURE_FAHRENHEIT:
          labelId = R.string.mkrsci_input_sensor_temperature_f;
          break;
        case MkrSciBleSensor.HANDLER_LIGHT:
          labelId = R.string.mkrsci_input_sensor_light;
          break;
        default:
          labelId = R.string.mkrsci_input_sensor_raw;
          break;
      }
      radioButtons[i] = new RadioButton(getActivity());
      radioButtons[i].setTag(optionId);
      radioButtons[i].setText(labelId);
      radioButtons[i].setOnClickListener(
          view1 -> {
            sensorSpec.setHandler(optionId);
          });
      radioGroup.addView(
          radioButtons[i],
          new RadioGroup.LayoutParams(
              RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));
    }

    loadSensorSpec(sensorId, defChecked);

    AlertDialog.Builder builder =
        new AlertDialog.Builder(getActivity())
            .setView(view)
            .setTitle(R.string.title_activity_sensor_settings)
            .setCancelable(true)
            .setPositiveButton(
                android.R.string.ok,
                (dialog, i) -> {
                  saveSensorSpec(experimentId, sensorId);
                  dialog.dismiss();
                });
    return builder.create();
  }

  private void loadSensorSpec(String sensorId, int defChecked) {
    dataController.getExternalSensorById(
        sensorId,
        new LoggingConsumer<ExternalSensorSpec>(TAG, "Load external sensor with ID = " + sensorId) {
          @Override
          public void success(ExternalSensorSpec sensor) {
            sensorSpec = (MkrSciBleSensorSpec) sensor;
            final String handler = sensorSpec.getHandler();
            int index = defChecked;
            for (int i = 0; i < radioButtons.length; i++) {
              if (Objects.equals(handler, radioButtons[i].getTag())) {
                index = i;
                break;
              }
            }
            radioButtons[index].setChecked(true);
          }
        });
  }

  private void saveSensorSpec(String experimentId, String sensorId) {
    dataController.addOrGetExternalSensor(
        sensorSpec,
        new LoggingConsumer<String>(TAG, "update external sensor") {
          @Override
          public void success(final String newSensorId) {
            if (!newSensorId.equals(sensorId)) {
              dataController.replaceSensorInExperiment(
                  experimentId,
                  sensorId,
                  newSensorId,
                  new LoggingConsumer<Success>(TAG, "update experiment") {
                    @Override
                    public void success(Success value) {
                      if (listener != null) {
                        listener.onExperimentSensorReplaced(sensorId, newSensorId);
                      }
                    }
                  });
            }
          }
        });
  }

  private AppAccount getAppAccount() {
    return WhistlePunkApplication.getAccount(getContext(), getArguments(), KEY_ACCOUNT_KEY);
  }

  private DeviceOptionsListener getOptionsListener() {
    Activity activity = getActivity();
    if (activity instanceof DeviceOptionsListener) {
      return (DeviceOptionsListener) getActivity();
    } else {
      return null;
    }
  }
}
