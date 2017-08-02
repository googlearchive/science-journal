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

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;
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
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecordFragment;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation;


import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for adding or editing a trigger.
 */
public class EditTriggerFragment extends Fragment {
    private static final String TAG = "EditTriggerFragment";

    private static final String ARG_SENSOR_ID = "sensor_id";
    private static final String ARG_EXPERIMENT_ID = "experiment_id";
    private static final String ARG_SENSOR_LAYOUT = "sensor_layout";
    private static final String ARG_TRIGGER_ID = "trigger_id";
    private static final String ARG_LAYOUT_POSITION = "sensor_layout_position";
    private static final int PERMISSION_VIBRATE = 1;

    private String mSensorId;
    private String mExperimentId;
    private Experiment mExperiment;
    private GoosciSensorLayout.SensorLayout mSensorLayout;
    private SensorTrigger mTriggerToEdit;
    private AppCompatSpinner mTypeSpinner;
    private AppCompatSpinner mWhenSpinner;
    private EditText mValue;
    private EditText mNoteValue;
    private SwitchCompat mAudioAlert;
    private SwitchCompat mVisualAlert;
    private SwitchCompat mHapticAlert;
    private SwitchCompat mOnlyWhenRecording;
    private int mSensorLayoutPosition;
    private ViewGroup mNoteGroup;
    private ViewGroup mAlertGroup;
    private ViewGroup mOnlyWhenRecordingGroup;
    private boolean mIsSavingNewTrigger = false;
    private boolean mTriggerWasEdited = false;
    private NumberFormat mNumberFormat;

    public static EditTriggerFragment newInstance(String sensorId, String experimentId,
            String triggerId, byte[] sensorLayoutBlob, int position,
            ArrayList<String> triggerOrder) {
        EditTriggerFragment result = new EditTriggerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SENSOR_ID, sensorId);
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putString(ARG_TRIGGER_ID, triggerId);
        args.putByteArray(ARG_SENSOR_LAYOUT, sensorLayoutBlob);
        args.putInt(ARG_LAYOUT_POSITION, position);
        args.putStringArrayList(TriggerListFragment.ARG_TRIGGER_ORDER, triggerOrder);
        result.setArguments(args);
        return result;
    }

    public EditTriggerFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSensorId = getArguments().getString(ARG_SENSOR_ID);
        mExperimentId = getArguments().getString(ARG_EXPERIMENT_ID);
        try {
            mSensorLayout = GoosciSensorLayout.SensorLayout.parseFrom(
                    getArguments().getByteArray(ARG_SENSOR_LAYOUT));
        } catch (InvalidProtocolBufferNanoException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Error parsing the SensorLayout", e);
            }
            mSensorLayout = RecordFragment.defaultLayout(0);
        }
        mSensorLayoutPosition = getArguments().getInt(ARG_LAYOUT_POSITION);
        getDataController().getExperimentById(mExperimentId,
                new LoggingConsumer<Experiment>(TAG, "get experiment") {
                    @Override
                    public void success(Experiment value) {
                        mExperiment = value;
                        String triggerId = getArguments().getString(ARG_TRIGGER_ID, "");
                        mTriggerToEdit = mExperiment.getSensorTrigger(triggerId);
                        populateView(getView());
                    }
                });
    }

    @Override
    public void onDestroy() {
        if (mTriggerWasEdited) {
            WhistlePunkApplication.getUsageTracker(getActivity()).trackEvent(
                    TrackerConstants.CATEGORY_TRIGGERS, TrackerConstants.ACTION_EDITED, null, 0);
        }
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_TRIGGER_EDIT);
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

        mNoteGroup = (ViewGroup) view.findViewById(R.id.note_type_trigger_section);
        mAlertGroup = (ViewGroup) view.findViewById(R.id.alert_type_trigger_section);
        mOnlyWhenRecordingGroup = (ViewGroup) view.findViewById(R.id.only_when_recording_section);

        mTypeSpinner = (AppCompatSpinner) view.findViewById(R.id.trigger_type_spinner);
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.trigger_type_list, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTypeSpinner.setAdapter(typeAdapter);

        mWhenSpinner = (AppCompatSpinner) view.findViewById(R.id.trigger_when_spinner);
        ArrayAdapter<CharSequence> whenAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.trigger_when_list, android.R.layout.simple_spinner_item);
        whenAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mWhenSpinner.setAdapter(whenAdapter);

        mValue = (EditText) view.findViewById(R.id.value_input);

        NumberFormat format = getValueNumberFormat();
        if (format instanceof  DecimalFormat) {
            DecimalFormatSymbols symbols = ((DecimalFormat) format).getDecimalFormatSymbols();
            // We can only dependably do this if the decimal separator is a fullstop.
            // http://b/8319249
            if (symbols.getDecimalSeparator() == '.') {
                mValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
        }

        mNoteValue = (EditText) view.findViewById(R.id.trigger_note_text);
        mAudioAlert = (SwitchCompat) view.findViewById(R.id.alert_type_audio_selector);
        mVisualAlert = (SwitchCompat) view.findViewById(R.id.alert_type_visual_selector);
        mHapticAlert = (SwitchCompat) view.findViewById(R.id.alert_type_haptic_selector);
        mHapticAlert.setEnabled(TriggerHelper.hasVibrator(getActivity()));
        mOnlyWhenRecording = (SwitchCompat) view.findViewById(R.id.trigger_only_when_recording);

        TextView unitsTextView = (TextView) view.findViewById(R.id.units);
        String units = AppSingleton.getInstance(getActivity())
                .getSensorAppearanceProvider().getAppearance(mSensorId).getUnits(getActivity());
        unitsTextView.setText(units);

        if (!isNewTrigger()) {
            // Populate the view with the trigger's data.
            int actionType = mTriggerToEdit.getActionType();
            mValue.setText(format.format(mTriggerToEdit.getValueToTrigger()));
            mTypeSpinner.setSelection(actionType);
            mWhenSpinner.setSelection(mTriggerToEdit.getTriggerWhen());
            if (actionType == TriggerInformation.TRIGGER_ACTION_ALERT) {
                int[] alertTypes = mTriggerToEdit.getAlertTypes();
                for (int i = 0; i < alertTypes.length; i++) {
                    int alertType = alertTypes[i];
                    if (alertType == TriggerInformation.TRIGGER_ALERT_AUDIO) {
                        mAudioAlert.setChecked(true);
                    } else if (alertType == TriggerInformation.TRIGGER_ALERT_VISUAL) {
                        mVisualAlert.setChecked(true);
                    } else if (alertType == TriggerInformation.TRIGGER_ALERT_PHYSICAL) {
                        mHapticAlert.setChecked(true);
                    }
                }
            } else if (actionType == TriggerInformation.TRIGGER_ACTION_NOTE) {
                mNoteValue.setText(mTriggerToEdit.getNoteText());
            }
            mOnlyWhenRecording.setChecked(mTriggerToEdit.shouldTriggerOnlyWhenRecording());
            updateViewVisibilities(actionType);

            // Now add the save listeners if this is an edited trigger (not a new trigger).
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    saveTrigger();
                }
            };
            mNoteValue.addTextChangedListener(watcher);
            mValue.addTextChangedListener(watcher);
            mWhenSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position,
                        long id) {
                    saveTrigger();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            CompoundButton.OnCheckedChangeListener checkedChangeListener =
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            saveTrigger();
                        }
                    };
            mAudioAlert.setOnCheckedChangeListener(checkedChangeListener);
            mVisualAlert.setOnCheckedChangeListener(checkedChangeListener);
        } else {
            // Default to an alert spinner that triggers "at" a value, and does a visual alert.
            mTypeSpinner.setSelection(TriggerInformation.TRIGGER_ACTION_ALERT);
            mWhenSpinner.setSelection(TriggerInformation.TRIGGER_WHEN_AT);
            mVisualAlert.setChecked(true);
            mOnlyWhenRecording.setChecked(false);
        }

        mWhenSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {
                // Hide all but alert types if this is a ABOVE or BELOW.
                if (position == TriggerInformation.TRIGGER_WHEN_ABOVE ||
                        position == TriggerInformation.TRIGGER_WHEN_BELOW) {
                    mTypeSpinner.setSelection(TriggerInformation.TRIGGER_ACTION_ALERT);
                    mTypeSpinner.setEnabled(false);
                    updateViewVisibilities(TriggerInformation.TRIGGER_ACTION_ALERT);
                    selectAlertTypeIfNeeded();
                } else {
                    mTypeSpinner.setEnabled(true);
                }
                if (!isNewTrigger()) {
                    saveTrigger();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateViewVisibilities(position);
                if (position == TriggerInformation.TRIGGER_ACTION_ALERT) {
                    selectAlertTypeIfNeeded();
                }
                if (position == TriggerInformation.TRIGGER_ACTION_START_RECORDING ||
                        position == TriggerInformation.TRIGGER_ACTION_STOP_RECORDING) {
                    mOnlyWhenRecording.setChecked(false);
                }
                if (!isNewTrigger()) {
                    saveTrigger();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mHapticAlert.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isNewTrigger()) {
                    saveTrigger();
                }
            }
        });

        mOnlyWhenRecording.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
        if (mNumberFormat == null) {
            mNumberFormat = NumberFormat.getNumberInstance(getResources().getConfiguration()
                    .locale);
            mNumberFormat.setMaximumFractionDigits(100);
        }
        return mNumberFormat;
    }

    private void updateViewVisibilities(int actionType) {
        if (actionType == TriggerInformation.TRIGGER_ACTION_START_RECORDING ||
                actionType == TriggerInformation.TRIGGER_ACTION_STOP_RECORDING) {
            mNoteGroup.setVisibility(View.GONE);
            mAlertGroup.setVisibility(View.GONE);
            mOnlyWhenRecordingGroup.setVisibility(View.GONE);
        } else if (actionType == TriggerInformation.TRIGGER_ACTION_ALERT) {
            mNoteGroup.setVisibility(View.GONE);
            mAlertGroup.setVisibility(View.VISIBLE);
            mOnlyWhenRecordingGroup.setVisibility(View.VISIBLE);
        } else if (actionType == TriggerInformation.TRIGGER_ACTION_NOTE) {
            mNoteGroup.setVisibility(View.VISIBLE);
            mAlertGroup.setVisibility(View.GONE);
            mOnlyWhenRecordingGroup.setVisibility(View.VISIBLE);
        }
    }

    private void selectAlertTypeIfNeeded() {
        // Alert type triggers should have the visual alert field checked by default
        // when the user switches to this type.
        if (!mHapticAlert.isChecked() && !mVisualAlert.isChecked() &&
                !mAudioAlert.isChecked()) {
            mVisualAlert.setChecked(true);
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
        menu.findItem(R.id.action_save).setEnabled(!mIsSavingNewTrigger);

        SensorAppearance appearance = AppSingleton.getInstance(getActivity())
                .getSensorAppearanceProvider().getAppearance(mSensorId);
        String triggerTitle = getString(isNewTrigger() ? R.string.title_fragment_add_trigger :
                R.string.title_fragment_edit_trigger);
        actionBar.setTitle(String.format(triggerTitle, appearance.getName(getActivity())));

        super.onCreateOptionsMenu(menu, inflater);
    }

    // Whether we are creating a new trigger or editing an existing trigger.
    private boolean isNewTrigger() {
        return mTriggerToEdit == null;
    }

    // Calculates the current list of alert types based on which alert checkboxes are selected.
    private int[] getCurrentAlertTypes() {
        List<Integer> alertTypesList = new ArrayList<>();
        if (mHapticAlert.isChecked()) {
            alertTypesList.add(TriggerInformation.TRIGGER_ALERT_PHYSICAL);
        }
        if (mVisualAlert.isChecked()) {
            alertTypesList.add(TriggerInformation.TRIGGER_ALERT_VISUAL);
        }
        if (mAudioAlert.isChecked()) {
            alertTypesList.add(TriggerInformation.TRIGGER_ALERT_AUDIO);
        }
        int[] alertTypes = new int[alertTypesList.size()];
        for (int i = 0; i < alertTypesList.size(); i++) {
            alertTypes[i] = alertTypesList.get(i);
        }
        return alertTypes;
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
        int triggerType = mTypeSpinner.getSelectedItemPosition();
        int triggerWhen = mWhenSpinner.getSelectedItemPosition();
        boolean triggerOnlyWhenRecording = mOnlyWhenRecording.isChecked();
        double triggerValue = 0;
        try {
            NumberFormat format = getValueNumberFormat();
            Number number = format.parse(mValue.getText().toString());
            triggerValue = number.doubleValue();
        } catch (ParseException ex) {
            mValue.setError(getActivity().getResources().getString(
                    R.string.cannot_save_invalid_value));
            return;
        }
        if (isNewTrigger()) {
            createNewTrigger(dc, triggerType, triggerWhen, triggerValue, triggerOnlyWhenRecording);
        } else {
            updateTrigger(dc, triggerType, triggerWhen, triggerValue, triggerOnlyWhenRecording,
                    returnToParent);
        }
    }

    // Updates a trigger in the database if it has changed, otherwise just returns to the parent
    // fragment.
    private void updateTrigger(DataController dc, int triggerType, int triggerWhen,
            double triggerValue, boolean triggerOnlyWhenRecording, final boolean returnToParent) {
        boolean isUpdated = updateLocalTriggerIfChanged(triggerType, triggerWhen, triggerValue,
                triggerOnlyWhenRecording);
        // Only update and activate if changes were made.
        if (!isUpdated && returnToParent) {
            goToParent();
            return;
        }
        mTriggerWasEdited = isUpdated;
        TriggerHelper.addTriggerToLayoutActiveTriggers(mSensorLayout,
                mTriggerToEdit.getTriggerId());
        mExperiment.updateSensorTrigger(mTriggerToEdit);
        dc.updateExperiment(mExperimentId,
                new LoggingConsumer<Success>(TAG, "update experiment's trigger") {
                    @Override
                    public void success(Success value) {
                        updateSensorLayoutAndGoToParent(returnToParent);
                    }
                });
    }

    private boolean updateLocalTriggerIfChanged(int triggerType, int triggerWhen,
            double triggerValue, boolean triggerOnlyWhenRecording) {
        boolean isUpdated = false;
        if (mTriggerToEdit.getTriggerWhen() != triggerWhen) {
            mTriggerToEdit.setTriggerWhen(triggerWhen);
            isUpdated = true;
        }
        if (mTriggerToEdit.getValueToTrigger() != triggerValue) {
            mTriggerToEdit.setValueToTrigger(triggerValue);
            isUpdated = true;
        }
        if (mTriggerToEdit.getActionType() != triggerType) {
            mTriggerToEdit.setTriggerActionType(triggerType);
            isUpdated = true;
        }
        if (mTriggerToEdit.shouldTriggerOnlyWhenRecording() != triggerOnlyWhenRecording) {
            mTriggerToEdit.setTriggerOnlyWhenRecording(triggerOnlyWhenRecording);
            isUpdated = true;
        }
        if (triggerType == TriggerInformation.TRIGGER_ACTION_NOTE) {
            String noteText = String.valueOf(mNoteValue.getText());
            if (!TextUtils.equals(noteText, mTriggerToEdit.getNoteText())) {
                mTriggerToEdit.setNoteText(String.valueOf(mNoteValue.getText()));
                isUpdated = true;
            }
        } else if (triggerType == TriggerInformation.TRIGGER_ACTION_ALERT) {
            int[] alertTypes = getCurrentAlertTypes();
            if (!SensorTrigger.hasSameAlertTypes(alertTypes, mTriggerToEdit.getAlertTypes())) {
                mTriggerToEdit.setAlertTypes(alertTypes);
                isUpdated = true;
            }
        }
        return isUpdated;
    }

    // Creates a new trigger in the database, and adds it to the active triggers in the SensorLayout
    // before returning to the parent fragment.
    private void createNewTrigger(DataController dc, int triggerType, int triggerWhen,
            double triggerValue, boolean triggerOnlyWhenRecording) {
        // Now that the trigger is verified, make sure the save button can't be pushed again.
        mIsSavingNewTrigger = true;
        getActivity().invalidateOptionsMenu();
        SensorTrigger triggerToAdd = null;
        if (triggerType == TriggerInformation.TRIGGER_ACTION_START_RECORDING ||
                triggerType == TriggerInformation.TRIGGER_ACTION_STOP_RECORDING) {
            triggerToAdd = SensorTrigger.newTrigger(mSensorId, triggerWhen, triggerType,
                    triggerValue);
        } else if (triggerType == TriggerInformation.TRIGGER_ACTION_NOTE) {
            triggerToAdd = SensorTrigger.newNoteTypeTrigger(mSensorId, triggerWhen,
                    String.valueOf(mNoteValue.getText()), triggerValue);
            triggerToAdd.setTriggerOnlyWhenRecording(triggerOnlyWhenRecording);
        } else if (triggerType == TriggerInformation.TRIGGER_ACTION_ALERT) {
            triggerToAdd = SensorTrigger.newAlertTypeTrigger(mSensorId, triggerWhen,
                    getCurrentAlertTypes(), triggerValue);
            triggerToAdd.setTriggerOnlyWhenRecording(triggerOnlyWhenRecording);
        }
        TriggerHelper.addTriggerToLayoutActiveTriggers(mSensorLayout, triggerToAdd.getTriggerId());
        mExperiment.addSensorTrigger(triggerToAdd);
        dc.updateExperiment(mExperimentId, new MaybeConsumer<Success>() {
                    @Override
                    public void fail(Exception e) {
                        mIsSavingNewTrigger = false;
                        getActivity().invalidateOptionsMenu();
                    }

                    @Override
                    public void success(Success value) {
                        // Since we can only save the trigger once, no need to reset
                        // mIsSavingNewTrigger onSuccess.
                        updateSensorLayoutAndGoToParent(true);
                    }
                });
        String triggerTypeString =
                getResources().getStringArray(R.array.trigger_type_list)[triggerType];
        String triggerWhenString =
                getResources().getStringArray(R.array.trigger_when_list)[triggerWhen];
        WhistlePunkApplication.getUsageTracker(getActivity()).trackEvent(
                TrackerConstants.CATEGORY_TRIGGERS, TrackerConstants.ACTION_CREATE,
                triggerTypeString + " when " + triggerWhenString, 0);
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    // Verifies that the user has entered valid input to the form.
    private boolean verifyInput() {
        // The user must enter a value at which the trigger happens.
        if (TextUtils.isEmpty(mValue.getText())) {
            mValue.setError(getActivity().getResources().getString(
                    R.string.cannot_save_invalid_value));
            return false;
        }
        if (mTypeSpinner.getSelectedItemPosition() == TriggerInformation.TRIGGER_ACTION_ALERT &&
                getCurrentAlertTypes().length == 0) {
            AccessibilityUtils.makeSnackbar(getView(),
                    getResources().getString(R.string.alert_trigger_needs_alerts),
                    Snackbar.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    // Updates the SensorLayout. This is because each time we edit a trigger, it gets enabled
    // and added to the active triggers in the SensorLayout. Note that if no changes are made,
    // the trigger is not re-enabled in the SensorLayout.
    private void updateSensorLayoutAndGoToParent(final boolean goToParent) {
        mExperiment.updateSensorLayout(mSensorLayoutPosition, mSensorLayout);
        getDataController().updateExperiment(mExperimentId,
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
        upIntent.putExtra(TriggerListActivity.EXTRA_SENSOR_ID, mSensorId);
        upIntent.putExtra(TriggerListActivity.EXTRA_EXPERIMENT_ID, mExperimentId);
        upIntent.putExtra(TriggerListActivity.EXTRA_LAYOUT_POSITION, mSensorLayoutPosition);
        upIntent.putExtra(TriggerListActivity.EXTRA_TRIGGER_ORDER,
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
            if (!mIsSavingNewTrigger) {
                // Don't allow multiple saves even if the DB is slow.
                saveTriggerAndReturn();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
