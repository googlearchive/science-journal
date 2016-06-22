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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ActiveBundle;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;

/**
 * OptionsPresenter for the video sensor
 */
class VideoOptionsPresenter implements SensorPresenter.OptionsPresenter {
    public static final String PREFS_KEY_VIDEO_RECORDING_FPS = "prefs_fps";

    private static final int MAX_FPS = 40;

    private double convertProgressToFps(int progress) {
        return Math.pow(10, 1 - (MAX_FPS - progress) / 10);
    }

    private int convertFpsToProgress(double fps) {
        Double d = (Math.log10(fps) - 1) * 10 + MAX_FPS;
        return d.intValue();
    }

    @Override
    public View buildOptionsView(final ActiveBundle activeBundle, Context context) {
        @SuppressLint("InflateParams") final View inflated =
                LayoutInflater.from(context).inflate(R.layout.video_stream_options, null);
        final EditText frequencyEdit =
                (EditText) inflated.findViewById(R.id.video_stream_fps_seek_text);
        float fps = getFps(activeBundle.getReadOnly());
        frequencyEdit.setText(String.valueOf(fps));

        final SeekBar seekBar = (SeekBar) inflated.findViewById(R.id.video_stream_fps_seek_bar);
        seekBar.setProgress(convertFpsToProgress(fps));
        seekBar.setMax(MAX_FPS);

        frequencyEdit.setOnKeyListener(new TextView.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                TextView view = (TextView) v;
                try {
                    Double d = Double.parseDouble(view.getText().toString());
                    seekBar.setProgress(convertFpsToProgress(d));
                } catch (NumberFormatException e) {
                }
                return true;
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final double fps = convertProgressToFps(progress);
                activeBundle.changeFloat(PREFS_KEY_VIDEO_RECORDING_FPS, (float) fps);
                frequencyEdit.setText(String.valueOf(fps));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        return inflated;
    }

    private float getFps(ReadableSensorOptions bundle) {
        return bundle.getFloat(
                PREFS_KEY_VIDEO_RECORDING_FPS, VideoSensor.DEFAULT_VIDEO_RECORDING_FPS);
    }

    @Override
    public void applyOptions(ReadableSensorOptions bundle) {

    }
}
