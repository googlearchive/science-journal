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

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;

/** Activity to display static information about a sensor. */
public class SensorInfoActivity extends AppCompatActivity {

  public static final String EXTRA_ACCOUNT_KEY = "account_key";
  public static final String EXTRA_SENSOR_ID = "sensor_id";
  public static final String EXTRA_COLOR_ID = "color_id";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppAccount appAccount = WhistlePunkApplication.getAccount(this, getIntent(), EXTRA_ACCOUNT_KEY);
    final String sensorId = getIntent().getStringExtra(EXTRA_SENSOR_ID);
    final int color = getIntent().getIntExtra(EXTRA_COLOR_ID, R.color.color_primary);
    setContentView(R.layout.activity_sensor_info);
    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    WhistlePunkApplication.getUsageTracker(this)
        .trackEvent(
            TrackerConstants.CATEGORY_INFO,
            TrackerConstants.ACTION_INFO,
            AppSingleton.getInstance(this).getSensorRegistry().getLoggingId(sensorId),
            0);

    // TODO: Set the action bar and window status bar colors. Need to get the darker color
    // for the window status bar first (b/26827677)
    /*
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
    }
    */

    final TextView overview = (TextView) findViewById(R.id.overview);
    final TextView firstParagraph = (TextView) findViewById(R.id.info_first_paragraph);
    final TextView secondParagraph = (TextView) findViewById(R.id.info_second_paragraph);
    final ImageView imageView = (ImageView) findViewById(R.id.info_image);

    final SensorAppearance appearance =
        AppSingleton.getInstance(this)
            .getSensorAppearanceProvider(appAccount)
            .getAppearance(sensorId);
    if (appearance == null) {
      firstParagraph.setText(getResources().getString(R.string.unknown_sensor));
      secondParagraph.setText("");
      imageView.setVisibility(View.GONE);
      return;
    }
    getSupportActionBar()
        .setTitle(
            String.format(
                getResources().getString(R.string.title_activity_sensor_info_format),
                appearance.getName(this)));

    overview.setText(appearance.getShortDescription(getApplicationContext()));

    // TODO: loading UX needed?  (b/31589508)
    if (!appearance.hasLearnMore()) {
      imageView.setVisibility(View.GONE);
    } else {
      appearance
          .loadLearnMore(this)
          .subscribe(
              contents -> {
                firstParagraph.setText(contents.getFirstParagraph());
                secondParagraph.setText(contents.getSecondParagraph());
                int drawable = contents.getDrawableResourceId();
                if (drawable != 0) {
                  imageView.setVisibility(View.VISIBLE);
                  GlideApp.with(this)
                      .load(contents.getDrawableResourceId())
                      .fitCenter()
                      .into(imageView);
                } else {
                  imageView.setVisibility(View.GONE);
                }
              });
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(this)
        .trackScreenView(TrackerConstants.SCREEN_SENSOR_INFO);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        onBackPressed();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
