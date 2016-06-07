package com.google.android.apps.forscience.whistlepunk;

import android.hardware.Sensor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.apps.forscience.whistlepunk.sensors.SensorTool;

/**
 * An activity which displays a level to the user.
 */
public class LevelActivity extends AppCompatActivity {

    private SensorTool mSensorTool;
    private LevelView mLevelView;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_level);
        mLevelView = (LevelView) findViewById(R.id.level_view);
        // Prefer a gravity sensor, as the accelerometer does not subtract
        // out movement of the phone. If the gravity sensor is not available, Accelerometer is
        // close enough.
        boolean hasGravity = SensorTool.deviceHasSensor(this, Sensor.TYPE_GRAVITY);
        int sensorType = hasGravity ? Sensor.TYPE_GRAVITY : Sensor.TYPE_ACCELEROMETER;
        mSensorTool = new SensorTool(this, sensorType) {
            public void onSensorUpdate(float[] values) {
                mLevelView.updateGravityValues(values);
            }
        };
        mSensorTool.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_level, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorTool.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorTool.stop();
    }
}
