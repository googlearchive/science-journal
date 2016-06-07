package com.google.android.apps.forscience.whistlepunk.project;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.R;

public class UpdateProjectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_project);

        if (savedInstanceState == null) {
            String projectId = getIntent().getExtras().getString(
                    UpdateProjectFragment.ARG_PROJECT_ID);
            UpdateProjectFragment fragment = UpdateProjectFragment.newInstance(projectId,
                    getIntent().getExtras().getBoolean(UpdateProjectFragment.ARG_NEW, false));
            fragment.setRetainInstance(true);

            getSupportFragmentManager().beginTransaction().add(R.id.container, fragment)
                    .commit();
        }
    }

    public static void launch(Context context, String projectId, boolean newProject) {
        final Intent intent = getLaunchIntent(context, projectId, newProject);
        if (newProject) {
            // Add in the detail intent so that we have it underneath the update activity if this is
            // new.
            Intent detailIntent = ProjectDetailsActivity.getLaunchIntent(context, projectId);
            context.startActivities(new Intent[] {detailIntent, intent});
        } else {
            context.startActivity(intent);
        }
    }

    @NonNull
    public static Intent getLaunchIntent(Context context, String projectId, boolean newProject) {
        final Intent intent = new Intent(context, UpdateProjectActivity.class);
        intent.putExtra(UpdateProjectFragment.ARG_PROJECT_ID, projectId);
        intent.putExtra(UpdateProjectFragment.ARG_NEW, newProject);
        return intent;
    }
}
