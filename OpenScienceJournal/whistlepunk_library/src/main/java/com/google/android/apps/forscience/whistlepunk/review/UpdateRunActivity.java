package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.R;

public class UpdateRunActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_run);

        if (savedInstanceState == null) {
            String runId = getIntent().getExtras().getString(
                    UpdateRunFragment.ARG_RUN_ID);
            UpdateRunFragment fragment = UpdateRunFragment.newInstance(runId);
            fragment.setRetainInstance(true);

            getSupportFragmentManager().beginTransaction().add(R.id.container, fragment)
                    .commit();
        }
    }

    public static void launch(Context context, String runId) {
        final Intent intent = new Intent(context, UpdateRunActivity.class);
        intent.putExtra(UpdateRunFragment.ARG_RUN_ID, runId);
        context.startActivity(intent);
    }
}
