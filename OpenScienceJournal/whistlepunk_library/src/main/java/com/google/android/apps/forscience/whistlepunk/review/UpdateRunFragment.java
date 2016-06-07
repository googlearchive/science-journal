package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.Run;

/**
 * Fragment for updating a run's metadata.
 */
public class UpdateRunFragment extends Fragment {

    private static final String TAG = "UpdateRunFragment";

    /**
     * Run ID (start label ID) for the run to update.
     */
    public static final String ARG_RUN_ID = "run_id";

    private String mRunId;
    private ExperimentRun mExperimentRun;
    private EditText mRunTitle;

    public UpdateRunFragment() {
    }

    public static UpdateRunFragment newInstance(String runId) {
        UpdateRunFragment fragment = new UpdateRunFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RUN_ID, runId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        mRunId = getArguments().getString(ARG_RUN_ID);

        getDataController().getExperimentRun(mRunId,
                new LoggingConsumer<ExperimentRun>(TAG, "retrieve run") {
                    @Override
                    public void success(ExperimentRun run) {
                        mExperimentRun = run;
                        mRunTitle.setText(mExperimentRun.getRunTitle(getActivity()));
                    }
                });
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_UPDATE_RUN);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_update_run, container, false);
        mRunTitle = (EditText) view.findViewById(R.id.run_title);
        return view;
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_update_run, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            returnToRunReview();
            return true;
        } else if (id == R.id.action_save) {
            saveAndReturn();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void saveAndReturn() {
        mExperimentRun.setRunTitle(mRunTitle.getText().toString().trim());
        getDataController().updateRun(mExperimentRun.getRun(), new LoggingConsumer<Success>(TAG,
                "updating run") {
            @Override
            public void success(Success value) {
                returnToRunReview();
            }
        });
    }

    private void returnToRunReview() {
        Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
        upIntent.putExtra(RunReviewActivity.EXTRA_FROM_RECORD, false);
        upIntent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, mRunId);
        NavUtils.navigateUpTo(getActivity(), upIntent);
    }
}
