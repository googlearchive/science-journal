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

package com.google.android.apps.forscience.whistlepunk;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;


public class RulerActivity extends AppCompatActivity {

    /**
     * A ground truth calibration target, such as a credit card or a ruler.
     */
    private static class RulerCalibration {

        /**
         * Title to show to the user.
         */
        String title;

        /**
         * Units for this target.
         */
        @RulerView.Units int units;

        /**
         * Target size, in the units.
         */
        float target;

        RulerCalibration(String title, int units, float target) {
            this.title = title;
            this.units = units;
            this.target = target;
        }
    }

    private RulerView mRulerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ruler);
        mRulerView = (RulerView) findViewById(R.id.ruler);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ruler, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_calibrate);
        item.setTitle(mRulerView.getCalibrationMode() ? R.string.action_end_calibrate :
                R.string.action_calibrate);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_calibrate) {
            if (mRulerView.getCalibrationMode()) {
                mRulerView.setCalibrationMode(false);
            } else {
                startCalibration();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * @return a list of ground truth calibration values.
     */
    private List<RulerCalibration> getCalibrations(@RulerView.Units int units) {
        ArrayList<RulerCalibration> calibrations = new ArrayList<>();

        if (units == RulerView.UNITS_METRIC) {
            // Credit card in metric.
            calibrations.add(new RulerCalibration(getString(R.string.calibration_credit_card),
                    RulerView.UNITS_METRIC, 53.98f));
            // Ruler measuring mm in metric.
            calibrations.add(new RulerCalibration(getString(R.string.calibration_ruler_metric),
                    RulerView.UNITS_METRIC, 80f));
        } else if (units == RulerView.UNITS_IMPERIAL) {
            // Credit card in imperial.
            calibrations.add(new RulerCalibration(getString(R.string.calibration_credit_card),
                    RulerView.UNITS_IMPERIAL, 21.25f));
            // Ruler measuring inches in imperial.
            calibrations.add(new RulerCalibration(getString(R.string.calibration_ruler_imperial),
                    RulerView.UNITS_IMPERIAL, 30f));
        }

        return calibrations;
    }

    private void startCalibration() {
        // Ask user what they will calibrate with.
        final List<RulerCalibration> calibrations = getCalibrations(mRulerView.getUnits());
        // Get the list of titles to display to the user.
        String[] titles = new String[calibrations.size()];
        for (int index = 0, size = calibrations.size(); index < size; ++index) {
            titles[index] = calibrations.get(index).title;
        }
        // TODO: add an item to the end to reset the calibration completely.

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(R.string.title_calibrate)
                .setItems(titles,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                RulerCalibration calibration = calibrations.get(which);
                                mRulerView.setCalibrationTarget(calibration.target,
                                        calibration.units);
                                mRulerView.setCalibrationMode(true);
                            }
                        })
                .show();
    }
}
