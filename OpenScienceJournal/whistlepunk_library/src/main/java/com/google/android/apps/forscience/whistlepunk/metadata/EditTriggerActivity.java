package com.google.android.apps.forscience.whistlepunk.metadata;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.R;

/**
 * Activity for adding and editing triggers.
 */
public class EditTriggerActivity extends AppCompatActivity {
    public static final String EXTRA_SENSOR_ID = "sensor_id";
    public static final String EXTRA_EXPERIMENT_ID = "experiment_id";
    public static final String EXTRA_TRIGGER_INFO_BLOB = "trigger_info_blob";
    public static final String EXTRA_SENSOR_LAYOUT_BLOB = "sensor_layout_blob";
    public static final String EXTRA_TRIGGER_ID = "trigger_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_trigger);

        Bundle extras = getIntent().getExtras();

        if (extras != null && savedInstanceState == null) {
            String sensorId = extras.getString(EXTRA_SENSOR_ID, "");
            String experimentId = extras.getString(EXTRA_EXPERIMENT_ID, "");
            byte[] triggerInfoBlob = extras.getByteArray(EXTRA_TRIGGER_INFO_BLOB);
            String triggerId = extras.getString(EXTRA_TRIGGER_ID, "");
            byte[] sensorLayoutBlob = extras.getByteArray(EXTRA_SENSOR_LAYOUT_BLOB);
            int position = extras.getInt(TriggerListActivity.EXTRA_LAYOUT_POSITION);
            EditTriggerFragment fragment = EditTriggerFragment.newInstance(sensorId, experimentId,
                    triggerId, triggerInfoBlob, sensorLayoutBlob, position);
            getSupportFragmentManager().beginTransaction().add(R.id.container, fragment).commit();
        }
    }
}
