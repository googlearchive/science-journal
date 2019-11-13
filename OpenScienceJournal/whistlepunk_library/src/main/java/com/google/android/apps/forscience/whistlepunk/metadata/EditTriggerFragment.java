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
package com.google.android.apps.forscience.whistlepunk.metadata;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecordFragment;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerAlertType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Set;

/** Fragment for adding or editing a trigger. */
public class EditTriggerFragment extends Fragment {
  private static final String TAG = "EditTriggerFragment";

  private static final String ARG_ACCOUNT_KEY = "account_key";
  private static final String ARG_SENSOR_ID = "sensor_id";
  private static final String ARG_EXPERIMENT_ID = "experiment_id";
  private static final String ARG_SENSOR_LAYOUT = "sensor_layout";
  private static final String ARG_TRIGGER_ID = "trigger_id";
  private static final String ARG_LAYOUT_POSITION = "sensor_layout_position";

  private AppAccount appAccount;
  private String sensorId;
  private String experimentId;
  private Experiment experiment;
  private SensorLayoutPojo sensorLayout;
  private SensorTrigger triggerToEdit;
  private AppCompatSpinner typeSpinner;
  private AppCompatSpinner whenSpinner;
  private EditText value;
  private EditText noteValue;
  private SwitchCompat audioAlert;
  private SwitchCompat visualAlert;
  private SwitchCompat hapticAlert;
  private SwitchCompat onlyWhenRecording;
  private int sensorLayoutPosition;
  private ViewGroup noteGroup;
  private ViewGroup alertGroup;
  private ViewGroup onlyWhenRecordingGroup;
  private boolean isSavingNewTrigger = false;
  private boolean triggerWasEdited = false;
  private NumberFormat numberFormat;

  public static EditTriggerFragment newInstance(
      AppAccount appAccount,
      String sensorId,
      String experimentId,
      String triggerId,
      byte[] sensorLayoutBlob,
      int position,
      ArrayList<String> triggerOrder) {
    EditTriggerFragment result = new EditTriggerFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(ARG_SENSOR_ID, sensorId);
    args.putString(ARG_EXPERIMENT_ID, experimentId);
    args.putString(ARG_TRIGGER_ID, triggerId);
    args.putByteArray(ARG_SENSOR_LAYOUT, sensorLayoutBlob);
    args.putInt(ARG_LAYOUT_POSITION, position);
    args.putStringArrayList(TriggerListFragment.ARG_TRIGGER_ORDER, triggerOrder);
    result.setArguments(args);
    return result;
  }

  public EditTriggerFragment() {}

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    appAccount = WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_ACCOUNT_KEY);
    sensorId = getArguments().getString(ARG_SENSOR_ID);
    experimentId = getArguments().getString(ARG_EXPERIMENT_ID);
    try {
      sensorLayout =
          SensorLayoutPojo.fromProto(
              GoosciSensorLayout.SensorLayout.parseFrom(
                  getArguments().getByteArray(ARG_SENSOR_LAYOUT)));
    } catch (InvalidProtocolBufferException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Error parsing the SensorLayout", e);
      }
      sensorLayout = RecordFragment.defaultLayout(0);
    }
    sensorLayoutPosition = getArguments().getInt(ARG_LAYOUT_POSITION);
    getDataController()
        .getExperimentById(
            experimentId,
            new LoggingConsumer<Experiment>(TAG, "get experiment") {
              @Override
              public void success(Experiment value) {
                experiment = value;
                String triggerId = getArguments().getString(ARG_TRIGGER_ID, "");
                triggerToEdit = experiment.getSensorTrigger(triggerId);
                populateView(getView());
              }
            });
  }

  @Override
  public void onDestroy() {
    if (triggerWasEdited) {
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(TrackerConstants.CATEGORY_TRIGGERS, TrackerConstants.ACTION_EDITED, null, 0);
    }
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_TRIGGER_EDIT);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_trigger_edit, parent, false);
    setHasOptionsMenu(true);
    populateView(view);
    return view;
  }

  private void populateView(View view) {
    if (view == null) {
      return;
    }

    noteGroup = (ViewGroup) view.findViewById(R.id.note_type_trigger_section);
    alertGroup = (ViewGroup) view.findViewById(R.id.alert_type_trigger_section);
    onlyWhenRecordingGroup = (ViewGroup) view.findViewById(R.id.only_when_recording_section);

    typeSpinner = (AppCompatSpinner) view.findViewById(R.id.trigger_type_spinner);
    ArrayAdapter<CharSequence> typeAdapter =
        ArrayAdapter.createFromResource(
            getActivity(), R.array.trigger_type_list, android.R.layout.simple_spinner_item);
    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    typeSpinner.setAdapter(typeAdapter);

    whenSpinner = (AppCompatSpinner) view.findViewById(R.id.trigger_when_spinner);
    ArrayAdapter<CharSequence> whenAdapter =
        ArrayAdapter.createFromResource(
            getActivity(), R.array.trigger_when_list, android.R.layout.simple_spinner_item);
    whenAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    whenSpinner.setAdapter(whenAdapter);

    value = (EditText) view.findViewById(R.id.value_input);

    NumberFormat format = getValueNumberFormat();
    if (format instanceof DecimalFormat) {
      DecimalFormatSymbols symbols = ((DecimalFormat) format).getDecimalFormatSymbols();
      // We can only dependably do this if the decimal separator is a fullstop.
      // http://b/8319249
      if (symbols.getDecimalSeparator() == '.') {
        value.setInputType(
            InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
      }
    }

    noteValue = (EditText) view.findViewById(R.id.trigger_note_text);
    audioAlert = (SwitchCompat) view.findViewById(R.id.alert_type_audio_selector);
    visualAlert = (SwitchCompat) view.findViewById(R.id.alert_type_visual_selector);
    hapticAlert = (SwitchCompat) view.findViewById(R.id.alert_type_haptic_selector);
    hapticAlert.setEnabled(TriggerHelper.hasVibrator(getActivity()));
    onlyWhenRecording = (SwitchCompat) view.findViewById(R.id.trigger_only_when_recording);

    TextView unitsTextView = (TextView) view.findViewById(R.id.units);
    String units =
        AppSingleton.getInstance(getActivity())
            .getSensorAppearanceProvider(appAccount)
            .getAppearance(sensorId)
            .getUnits(getActivity());
    unitsTextView.setText(units);

    if (!isNewTrigger()) {
      // Populate the view with the trigger's data.
      TriggerActionType actionType = triggerToEdit.getActionType();
      value.setText(format.format(triggerToEdit.getValueToTrigger()));
      typeSpinner.setSelection(actionType.getNumber());
      whenSpinner.setSelection(triggerToEdit.getTriggerWhen().getNumber());
      if (actionType == TriggerActionType.TRIGGER_ACTION_ALERT) {
        Set<TriggerAlertType> alertTypes = triggerToEdit.getAlertTypes();
        if (alertTypes.contains(TriggerAlertType.TRIGGER_ALERT_AUDIO)) {
          audioAlert.setChecked(true);
        } else if (alertTypes.contains(TriggerAlertType.TRIGGER_ALERT_VISUAL)) {
          visualAlert.setChecked(true);
        } else if (alertTypes.contains(TriggerAlertType.TRIGGER_ALERT_PHYSICAL)) {
          hapticAlert.setChecked(true);
        }
      } else if (actionType == TriggerActionType.TRIGGER_ACTION_NOTE) {
        noteValue.setText(triggerToEdit.getNoteText());
      }
      onlyWhenRecording.setChecked(triggerToEdit.shouldTriggerOnlyWhenRecording());
      updateViewVisibilities(actionType);

      // Now add the save listeners if this is an edited trigger (not a new trigger).
      TextWatcher watcher =
          new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
              saveTrigger();
            }
          };
      noteValue.addTextChangedListener(watcher);
      value.addTextChangedListener(watcher);
      whenSpinner.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
              saveTrigger();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
          });
      CompoundButton.OnCheckedChangeListener checkedChangeListener =
          new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
              saveTrigger();
            }
          };
      audioAlert.setOnCheckedChangeListener(checkedChangeListener);
      visualAlert.setOnCheckedChangeListener(checkedChangeListener);
    } else {
      // Default to an alert spinner that triggers "at" a value, and does a visual alert.
      typeSpinner.setSelection(TriggerActionType.TRIGGER_ACTION_ALERT_VALUE);
      whenSpinner.setSelection(TriggerWhen.TRIGGER_WHEN_AT_VALUE);
      visualAlert.setChecked(true);
      onlyWhenRecording.setChecked(false);
    }

    whenSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            // Hide all but alert types if this is a ABOVE or BELOW.
            if (position == TriggerWhen.TRIGGER_WHEN_ABOVE_VALUE
                || position == TriggerWhen.TRIGGER_WHEN_BELOW_VALUE) {
              typeSpinner.setSelection(TriggerActionType.TRIGGER_ACTION_ALERT_VALUE);
              typeSpinner.setEnabled(false);
              updateViewVisibilities(TriggerActionType.TRIGGER_ACTION_ALERT);
              selectAlertTypeIfNeeded();
            } else {
              typeSpinner.setEnabled(true);
            }
            if (!isNewTrigger()) {
              saveTrigger();
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    typeSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            updateViewVisibilities(TriggerActionType.forNumber(position));
            if (position == TriggerActionType.TRIGGER_ACTION_ALERT_VALUE) {
              selectAlertTypeIfNeeded();
            }
            if (position == TriggerActionType.TRIGGER_ACTION_START_RECORDING_VALUE
                || position == TriggerActionType.TRIGGER_ACTION_STOP_RECORDING_VALUE) {
              onlyWhenRecording.setChecked(false);
            }
            if (!isNewTrigger()) {
              saveTrigger();
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    hapticAlert.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!isNewTrigger()) {
              saveTrigger();
            }
          }
        });

    onlyWhenRecording.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!isNewTrigger()) {
              saveTrigger();
            }
          }
        });
  }

  @NonNull
  private NumberFormat getValueNumberFormat() {
    if (numberFormat == null) {
      numberFormat = NumberFormat.getNumberInstance(getResources().getConfiguration().locale);
      numberFormat.setMaximumFractionDigits(100);
    }
    return numberFormat;
  }

  private void updateViewVisibilities(TriggerActionType actionType) {
    if (actionType == TriggerActionType.TRIGGER_ACTION_START_RECORDING
        || actionType == TriggerActionType.TRIGGER_ACTION_STOP_RECORDING) {
      noteGroup.setVisibility(View.GONE);
      alertGroup.setVisibility(View.GONE);
      onlyWhenRecordingGroup.setVisibility(View.GONE);
    } else if (actionType == TriggerActionType.TRIGGER_ACTION_ALERT) {
      noteGroup.setVisibility(View.GONE);
      alertGroup.setVisibility(View.VISIBLE);
      onlyWhenRecordingGroup.setVisibility(View.VISIBLE);
    } else if (actionType == TriggerActionType.TRIGGER_ACTION_NOTE) {
      noteGroup.setVisibility(View.VISIBLE);
      alertGroup.setVisibility(View.GONE);
      onlyWhenRecordingGroup.setVisibility(View.VISIBLE);
    }
  }

  private void selectAlertTypeIfNeeded() {
    // Alert type triggers should have the visual alert field checked by default
    // when the user switches to this type.
    if (!hapticAlert.isChecked() && !visualAlert.isChecked() && !audioAlert.isChecked()) {
      visualAlert.setChecked(true);
    }
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_edit_trigger, menu);

    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    actionBar.setDisplayHomeAsUpEnabled(true);
    if (isNewTrigger()) {
      actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
      actionBar.setHomeActionContentDescription(android.R.string.cancel);
    }

    menu.findItem(R.id.action_save).setVisible(isNewTrigger());
    menu.findItem(R.id.action_save).setEnabled(!isSavingNewTrigger);

    SensorAppearance appearance =
        AppSingleton.getInstance(getActivity())
            .getSensorAppearanceProvider(appAccount)
            .getAppearance(sensorId);
    String triggerTitle =
        getString(
            isNewTrigger()
                ? R.string.title_fragment_add_trigger
                : R.string.title_fragment_edit_trigger);
    actionBar.setTitle(String.format(triggerTitle, appearance.getName(getActivity())));

    super.onCreateOptionsMenu(menu, inflater);
  }

  // Whether we are creating a new trigger or editing an existing trigger.
  private boolean isNewTrigger() {
    return triggerToEdit == null;
  }

  // Calculates the current list of alert types based on which alert checkboxes are selected.
  private ImmutableSet<TriggerAlertType> getCurrentAlertTypes() {
    ImmutableSet.Builder<TriggerAlertType> alertTypesSet = ImmutableSet.builder();
    if (hapticAlert.isChecked()) {
      alertTypesSet.add(TriggerAlertType.TRIGGER_ALERT_PHYSICAL);
    }
    if (visualAlert.isChecked()) {
      alertTypesSet.add(TriggerAlertType.TRIGGER_ALERT_VISUAL);
    }
    if (audioAlert.isChecked()) {
      alertTypesSet.add(TriggerAlertType.TRIGGER_ALERT_AUDIO);
    }
    return alertTypesSet.build();
  }

  // Saves the trigger based on the current state of the UI and returns to the parent activity.
  private void saveTriggerAndReturn() {
    saveTrigger(true);
  }

  // Saves the trigger based on the current state of the UI.
  private void saveTrigger() {
    saveTrigger(false);
  }

  private void saveTrigger(boolean returnToParent) {
    if (!verifyInput()) {
      return;
    }
    final DataController dc = getDataController();
    TriggerActionType triggerType =
        TriggerActionType.forNumber(typeSpinner.getSelectedItemPosition());
    TriggerWhen triggerWhen = TriggerWhen.forNumber(whenSpinner.getSelectedItemPosition());
    boolean triggerOnlyWhenRecording = onlyWhenRecording.isChecked();
    double triggerValue = 0;
    try {
      NumberFormat format = getValueNumberFormat();
      Number number = format.parse(value.getText().toString());
      triggerValue = number.doubleValue();
    } catch (ParseException ex) {
      value.setError(getActivity().getResources().getString(R.string.cannot_save_invalid_value));
      return;
    }
    if (isNewTrigger()) {
      createNewTrigger(dc, triggerType, triggerWhen, triggerValue, triggerOnlyWhenRecording);
    } else {
      updateTrigger(
          dc, triggerType, triggerWhen, triggerValue, triggerOnlyWhenRecording, returnToParent);
    }
  }

  // Updates a trigger in the database if it has changed, otherwise just returns to the parent
  // fragment.
  private void updateTrigger(
      DataController dc,
      TriggerActionType triggerType,
      TriggerWhen triggerWhen,
      double triggerValue,
      boolean triggerOnlyWhenRecording,
      final boolean returnToParent) {
    boolean isUpdated =
        updateLocalTriggerIfChanged(
            triggerType, triggerWhen, triggerValue, triggerOnlyWhenRecording);
    // Only update and activate if changes were made.
    if (!isUpdated && returnToParent) {
      goToParent();
      return;
    }
    triggerWasEdited = isUpdated;
    TriggerHelper.addTriggerToLayoutActiveTriggers(sensorLayout, triggerToEdit.getTriggerId());
    experiment.updateSensorTrigger(triggerToEdit);
    dc.updateExperiment(
        experimentId,
        new LoggingConsumer<Success>(TAG, "update experiment's trigger") {
          @Override
          public void success(Success value) {
            updateSensorLayoutAndGoToParent(returnToParent);
          }
        });
  }

  private boolean updateLocalTriggerIfChanged(
      TriggerActionType triggerType,
      TriggerWhen triggerWhen,
      double triggerValue,
      boolean triggerOnlyWhenRecording) {
    boolean isUpdated = false;
    if (triggerToEdit.getTriggerWhen() != triggerWhen) {
      triggerToEdit.setTriggerWhen(triggerWhen);
      isUpdated = true;
    }
    if (triggerToEdit.getValueToTrigger() != triggerValue) {
      triggerToEdit.setValueToTrigger(triggerValue);
      isUpdated = true;
    }
    if (triggerToEdit.getActionType() != triggerType) {
      triggerToEdit.setTriggerActionType(triggerType);
      isUpdated = true;
    }
    if (triggerToEdit.shouldTriggerOnlyWhenRecording() != triggerOnlyWhenRecording) {
      triggerToEdit.setTriggerOnlyWhenRecording(triggerOnlyWhenRecording);
      isUpdated = true;
    }
    if (triggerType == TriggerActionType.TRIGGER_ACTION_NOTE) {
      String noteText = String.valueOf(noteValue.getText());
      if (!TextUtils.equals(noteText, triggerToEdit.getNoteText())) {
        triggerToEdit.setNoteText(String.valueOf(noteValue.getText()));
        isUpdated = true;
      }
    } else if (triggerType == TriggerActionType.TRIGGER_ACTION_ALERT) {
      Set<TriggerAlertType> alertTypes = getCurrentAlertTypes();
      if (!alertTypes.equals(triggerToEdit.getAlertTypes())) {
        triggerToEdit.setAlertTypes(alertTypes);
        isUpdated = true;
      }
    }
    return isUpdated;
  }

  // Creates a new trigger in the database, and adds it to the active triggers in the SensorLayout
  // before returning to the parent fragment.
  private void createNewTrigger(
      DataController dc,
      TriggerActionType triggerType,
      TriggerWhen triggerWhen,
      double triggerValue,
      boolean triggerOnlyWhenRecording) {
    // Now that the trigger is verified, make sure the save button can't be pushed again.
    isSavingNewTrigger = true;
    getActivity().invalidateOptionsMenu();
    SensorTrigger triggerToAdd = null;
    if (triggerType == TriggerActionType.TRIGGER_ACTION_START_RECORDING
        || triggerType == TriggerActionType.TRIGGER_ACTION_STOP_RECORDING) {
      triggerToAdd = SensorTrigger.newTrigger(sensorId, triggerWhen, triggerType, triggerValue);
    } else if (triggerType == TriggerActionType.TRIGGER_ACTION_NOTE) {
      triggerToAdd =
          SensorTrigger.newNoteTypeTrigger(
              sensorId, triggerWhen, String.valueOf(noteValue.getText()), triggerValue);
      triggerToAdd.setTriggerOnlyWhenRecording(triggerOnlyWhenRecording);
    } else if (triggerType == TriggerActionType.TRIGGER_ACTION_ALERT) {
      triggerToAdd =
          SensorTrigger.newAlertTypeTrigger(
              sensorId, triggerWhen, getCurrentAlertTypes(), triggerValue);
      triggerToAdd.setTriggerOnlyWhenRecording(triggerOnlyWhenRecording);
    }
    TriggerHelper.addTriggerToLayoutActiveTriggers(sensorLayout, triggerToAdd.getTriggerId());
    experiment.addSensorTrigger(triggerToAdd);
    dc.updateExperiment(
        experimentId,
        new MaybeConsumer<Success>() {
          @Override
          public void fail(Exception e) {
            isSavingNewTrigger = false;
            getActivity().invalidateOptionsMenu();
          }

          @Override
          public void success(Success value) {
            // Since we can only save the trigger once, no need to reset
            // isSavingNewTrigger onSuccess.
            updateSensorLayoutAndGoToParent(true);
          }
        });
    String triggerTypeString =
        getResources().getStringArray(R.array.trigger_type_list)[triggerType.getNumber()];
    String triggerWhenString =
        getResources().getStringArray(R.array.trigger_when_list)[triggerWhen.getNumber()];
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_TRIGGERS,
            TrackerConstants.ACTION_CREATE,
            triggerTypeString + " when " + triggerWhenString,
            0);
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
  }

  // Verifies that the user has entered valid input to the form.
  private boolean verifyInput() {
    // The user must enter a value at which the trigger happens.
    if (TextUtils.isEmpty(value.getText())) {
      value.setError(getActivity().getResources().getString(R.string.cannot_save_invalid_value));
      return false;
    }
    if (typeSpinner.getSelectedItemPosition() == TriggerActionType.TRIGGER_ACTION_ALERT_VALUE
        && getCurrentAlertTypes().isEmpty()) {
      AccessibilityUtils.makeSnackbar(
              getView(),
              getResources().getString(R.string.alert_trigger_needs_alerts),
              Snackbar.LENGTH_LONG)
          .show();
      return false;
    }
    return true;
  }

  // Updates the SensorLayout. This is because each time we edit a trigger, it gets enabled
  // and added to the active triggers in the SensorLayout. Note that if no changes are made,
  // the trigger is not re-enabled in the SensorLayout.
  private void updateSensorLayoutAndGoToParent(final boolean goToParent) {
    experiment.updateSensorLayout(sensorLayoutPosition, sensorLayout);
    getDataController()
        .updateExperiment(
            experimentId,
            new LoggingConsumer<Success>(TAG, "update experiment with layout") {
              @Override
              public void success(Success value) {
                if (goToParent) {
                  goToParent();
                }
              }
            });
  }

  // Returns to the TriggerListActivity.
  private void goToParent() {
    if (getActivity() == null) {
      return;
    }
    // To restart the TriggerListActivity, need to pass back the trigger and layout info.
    Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
    upIntent.putExtra(TriggerListActivity.EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    upIntent.putExtra(TriggerListActivity.EXTRA_SENSOR_ID, sensorId);
    upIntent.putExtra(TriggerListActivity.EXTRA_EXPERIMENT_ID, experimentId);
    upIntent.putExtra(TriggerListActivity.EXTRA_LAYOUT_POSITION, sensorLayoutPosition);
    upIntent.putExtra(
        TriggerListActivity.EXTRA_TRIGGER_ORDER,
        getArguments().getStringArrayList(TriggerListActivity.EXTRA_TRIGGER_ORDER));
    if (getActivity() != null) {
      NavUtils.navigateUpTo(getActivity(), upIntent);
    } else {
      Log.e(TAG, "Can't exit activity because it's no longer there.");
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == android.R.id.home) {
      // If this is a new trigger and they didn't explicitly save, just don't save it.
      // Triggers being edited save at each change, so no need to save that either.
      goToParent();
      return true;
    } else if (id == R.id.action_save) {
      // Only available for new triggers.
      if (!isSavingNewTrigger) {
        // Don't allow multiple saves even if the DB is slow.
        saveTriggerAndReturn();
      }
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
