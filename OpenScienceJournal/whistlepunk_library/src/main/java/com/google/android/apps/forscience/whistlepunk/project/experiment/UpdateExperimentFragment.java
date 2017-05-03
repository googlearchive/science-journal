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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;

/**
 * Fragment for saving/updating experiment detials (title, description...etc).
 */
public class UpdateExperimentFragment extends Fragment {

    private static final String TAG = "UpdateExperimentFrag";

    /**
     * Indicates the experiment ID we're currently updating.
     */
    public static final String ARG_EXPERIMENT_ID = "experiment_id";

    /**
     * Boolean extra denoting whether this is a new experiment or not.
     */
    public static final String ARG_NEW = "new";

    /**
     * Parcelable extra with the component name of the parent class that should be used when saving
     * or quitting. If {@code null}, do the default handling.
     */
    public static final String ARG_PARENT_COMPONENT = "parent_component";

    private String mExperimentId;
    private Experiment mExperiment;
    private TextView mExperimentTitle;
    private TextView mExperimentDescription;
    private ComponentName mParentComponent;
    private boolean mWasEdited;

    public UpdateExperimentFragment() {
    }

    public static UpdateExperimentFragment newInstance(String experimentId, boolean isNewExperiment,
                                                       ComponentName parentComponent) {
        UpdateExperimentFragment fragment = new UpdateExperimentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putBoolean(ARG_NEW, isNewExperiment);
        args.putParcelable(ARG_PARENT_COMPONENT, parentComponent);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExperimentId = getArguments().getString(ARG_EXPERIMENT_ID);
        mParentComponent = getArguments().getParcelable(ARG_PARENT_COMPONENT);
        getDataController().getExperimentById(mExperimentId,
                new LoggingConsumer<Experiment>(TAG, "load experiment") {
                    @Override
                    public void success(Experiment experiment) {
                        attachExperimentDetails(experiment);
                    }
                });
        getActivity().setTitle(getString(isNewExperiment() ?
                R.string.title_activity_new_experiment :
                R.string.title_activity_update_experiment));
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                isNewExperiment() ? TrackerConstants.SCREEN_NEW_EXPERIMENT :
                        TrackerConstants.SCREEN_UPDATE_EXPERIMENT);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWasEdited && !isNewExperiment()) {
            WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                            TrackerConstants.ACTION_EDITED,
                            null, 0);
            mWasEdited = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_update_experiment, container, false);
        mExperimentTitle = (EditText) view.findViewById(R.id.experiment_title);
        mExperimentDescription = (EditText) view.findViewById(R.id.experiment_description);
        mExperimentTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mExperiment == null) {
                    return;
                }
                if (!s.toString().equals(mExperiment.getTitle())) {
                    mExperiment.setTitle(s.toString().trim());
                    saveExperiment();
                    mWasEdited = true;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        mExperimentDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mExperiment == null) {
                    return;
                }
                if (!s.toString().equals(mExperiment.getDescription())) {
                    mExperiment.setDescription(s.toString().trim());
                    saveExperiment();
                    mWasEdited = true;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_update_experiment, menu);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (isNewExperiment()) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        }
        menu.findItem(R.id.action_save).setVisible(isNewExperiment());
        actionBar.setDisplayHomeAsUpEnabled(true);

        actionBar.setTitle(getString(isNewExperiment() ? R.string.title_activity_new_experiment :
                R.string.title_activity_update_experiment));

        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            if (isNewExperiment()) {
                // We should delete the experiment: user killed it without saving.
                // TODO: warn the user if they edited anything.
                deleteExperiment();
            } else {
                goToParent();
            }
            return true;
        } else if (id == R.id.action_save) {
            saveExperiment(true /* go to parent when done */);
            WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                            TrackerConstants.ACTION_CREATE,
                            null, 0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean isNewExperiment() {
        return getArguments().getBoolean(ARG_NEW, false);
    }

    private void attachExperimentDetails(Experiment experiment) {
        mExperiment = experiment;
        mExperimentTitle.setText(experiment.getTitle());
        mExperimentDescription.setText(experiment.getDescription());
        mWasEdited = false;
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    /**
     * Save the experiment and stay on this screen.
     */
    private void saveExperiment() {
        saveExperiment(false /* stay on this screen */);
    }

    /**
     * Save the experiment and optionally to go the parent, depending on
     * {@param goToParentWhenDone}.
     */
    private void saveExperiment(final boolean goToParentWhenDone) {
        getDataController().updateExperiment(mExperimentId,
                new LoggingConsumer<Success>(TAG, "update experiment") {
                    @Override
                    public void fail(Exception e) {
                        super.fail(e);
                        AccessibilityUtils.makeSnackbar(
                                getView(),
                                getResources().getString(R.string.experiment_save_failed),
                                Snackbar.LENGTH_SHORT).show();
                    }

                    @Override
                    public void success(Success value) {
                        if (goToParentWhenDone) {
                            goToParent();
                        }
                    }
                });
    }

    private void deleteExperiment() {
        getDataController().deleteExperiment(mExperiment, new LoggingConsumer<Success>(TAG,
                "Deleting new experiment") {
            @Override
            public void success(Success value) {
                if (mParentComponent != null) {
                    goToParent();
                } else if (getActivity() != null) {
                    // Go back to the experiment list page
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.putExtra(MainActivity.ARG_SELECTED_NAV_ITEM_ID,
                            R.id.navigation_item_experiments);
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    NavUtils.navigateUpTo(getActivity(), intent);
                } else {
                    Log.e(TAG, "Can't exit activity because it's no longer there.");
                }
            }
        });
    }

    private void goToParent() {
        Intent upIntent;
        if (mParentComponent != null) {
            upIntent = new Intent();
            upIntent.setClassName(mParentComponent.getPackageName(),
                    mParentComponent.getClassName());
        } else {
            upIntent = NavUtils.getParentActivityIntent(getActivity());
        }
        upIntent.putExtra(ExperimentDetailsFragment.ARG_EXPERIMENT_ID, mExperimentId);
        if (getActivity() != null) {
            NavUtils.navigateUpTo(getActivity(), upIntent);
        } else {
            Log.e(TAG, "Can't exit activity because it's no longer there.");
        }
    }
}
