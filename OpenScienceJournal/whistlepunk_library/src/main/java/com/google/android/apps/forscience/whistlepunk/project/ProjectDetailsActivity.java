package com.google.android.apps.forscience.whistlepunk.project;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.R;

public class ProjectDetailsActivity extends MetadataActivity {

    private static final String TAG = "ProjectDetailsActivity";
    private String mProjectId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_details);

        Bundle extras = getIntent().getExtras();
        if (savedInstanceState == null && extras != null) {
            mProjectId = extras.getString(
                    ProjectDetailsFragment.ARG_PROJECT_ID);
            ProjectDetailsFragment fragment = ProjectDetailsFragment.newInstance(mProjectId);
            fragment.setRetainInstance(true);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        } else if (savedInstanceState != null){
            mProjectId = savedInstanceState.getString(ProjectDetailsFragment.ARG_PROJECT_ID);
        }
    }

    public static void launch(Context context, String projectId, Bundle options) {
        final Intent intent = getLaunchIntent(context, projectId);
        context.startActivity(intent, options);
    }

    @NonNull
    public static Intent getLaunchIntent(Context context, String projectId) {
        final Intent intent = new Intent(context, ProjectDetailsActivity.class);
        intent.putExtra(ProjectDetailsFragment.ARG_PROJECT_ID, projectId);
        return intent;
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(ProjectDetailsFragment.ARG_PROJECT_ID, mProjectId);
        super.onSaveInstanceState(savedInstanceState);
    }
}
