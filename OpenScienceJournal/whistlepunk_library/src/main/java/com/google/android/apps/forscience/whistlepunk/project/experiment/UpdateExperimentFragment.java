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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxEvent;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.io.ByteStreams;
import io.reactivex.subjects.BehaviorSubject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Fragment for saving/updating experiment details (title, description...etc). */
public class UpdateExperimentFragment extends Fragment {

  private static final String TAG = "UpdateExperimentFrag";

  /**
   * Indicates the account key for the account that owns the experiment we're currently updating.
   */
  public static final String ARG_ACCOUNT_KEY = "account_key";
  /** Indicates the experiment ID we're currently updating. */
  public static final String ARG_EXPERIMENT_ID = "experiment_id";

  private static final String KEY_SAVED_PICTURE_PATH = "picture_path";

  private AppAccount appAccount;
  private String experimentId;
  private BehaviorSubject<Experiment> experiment = BehaviorSubject.create();
  private ImageView photoPreview;
  private String pictureLabelPath = null;
  private RxEvent saved = new RxEvent();

  public UpdateExperimentFragment() {}

  public static UpdateExperimentFragment newInstance(AppAccount appAccount, String experimentId) {
    UpdateExperimentFragment fragment = new UpdateExperimentFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(ARG_EXPERIMENT_ID, experimentId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    appAccount = WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_ACCOUNT_KEY);
    experimentId = getArguments().getString(ARG_EXPERIMENT_ID);
    if (savedInstanceState != null) {
      pictureLabelPath = savedInstanceState.getString(KEY_SAVED_PICTURE_PATH);
    }
    getDataController()
        .getExperimentById(
            experimentId,
            new LoggingConsumer<Experiment>(TAG, "load experiment") {
              @Override
              public void success(Experiment experiment) {
                attachExperimentDetails(experiment);
              }
            });
    getActivity().setTitle(getString(R.string.title_activity_update_experiment));
    setHasOptionsMenu(true);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_update_experiment, menu);

    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
    actionBar.setHomeActionContentDescription(android.R.string.cancel);

    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_save) {
      saved.onHappened();
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(
              TrackerConstants.CATEGORY_EXPERIMENTS,
              TrackerConstants.ACTION_EDITED,
              TrackerConstants.LABEL_UPDATE_EXPERIMENT,
              0);
      getActivity().finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onDestroy() {
    saved.onDoneHappening();
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_UPDATE_EXPERIMENT);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_update_experiment, container, false);
    EditText title = (EditText) view.findViewById(R.id.experiment_title);
    photoPreview = (ImageView) view.findViewById(R.id.experiment_cover);
    Button chooseButton = (Button) view.findViewById(R.id.btn_choose_photo);
    ImageButton takeButton = (ImageButton) view.findViewById(R.id.btn_take_photo);

    // Set the color of the placeholder drawable. This isn't used anywhere else
    // so we don't need to mutate() the drawable.
    photoPreview
        .getDrawable()
        .setColorFilter(
            photoPreview.getResources().getColor(R.color.text_color_light_grey),
            PorterDuff.Mode.SRC_IN);

    experiment.subscribe(
        experiment -> {
          title.setText(experiment.getTitle());

          saved
              .happens()
              .subscribe(
                  o -> {
                    String newValue = title.getText().toString().trim();
                    if (!newValue.equals(experiment.getTitle())) {
                      experiment.setTitle(newValue);
                      saveExperiment();
                    }
                  });

          if (!TextUtils.isEmpty(experiment.getExperimentOverview().getImagePath())) {
            // Load the current experiment photo
            PictureUtils.loadExperimentOverviewImage(
                appAccount, photoPreview, experiment.getExperimentOverview().getImagePath());
          }
          chooseButton.setOnClickListener(
              new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                  PictureUtils.launchPhotoPicker(UpdateExperimentFragment.this);
                }
              });
          takeButton.setOnClickListener(
              new View.OnClickListener() {
                // TODO: Take photo
                @Override
                public void onClick(View view) {
                  PermissionUtils.tryRequestingPermission(
                      getActivity(),
                      PermissionUtils.REQUEST_CAMERA,
                      new PermissionUtils.PermissionListener() {
                        @Override
                        public void onPermissionGranted() {
                          pictureLabelPath =
                              PictureUtils.capturePictureLabel(
                                  getActivity(),
                                  appAccount,
                                  experimentId,
                                  UUID.randomUUID().toString());
                        }

                        @Override
                        public void onPermissionDenied() {}

                        @Override
                        public void onPermissionPermanentlyDenied() {}
                      });
                }
              });
        });
    title.setOnEditorActionListener(
        (textView, i, keyEvent) -> {
          if (i == EditorInfo.IME_ACTION_DONE) {
            title.clearFocus();
            title.setFocusable(false);
          }
          return false;
        });
    title.setOnTouchListener(
        (v, e) -> {
          title.setFocusableInTouchMode(true);
          title.requestFocus();
          return false;
        });
    return view;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PictureUtils.REQUEST_SELECT_PHOTO
        && resultCode == Activity.RESULT_OK
        && data.getData() != null) {
      boolean success = false;
      File imageFile = null;
      // Check for non-null Uri here because of b/27899888
      try {
        // The ACTION_GET_CONTENT intent give temporary access to the
        // selected photo. We need to copy the selected photo to
        // to another file to get the real absolute path and store
        // that file's path into the experiment.

        // Use the experiment ID to name the project image.
        imageFile =
            PictureUtils.createImageFile(
                getActivity(), appAccount, experimentId, UUID.randomUUID().toString());
        copyUriToFile(getActivity(), data.getData(), imageFile);
        success = true;
      } catch (IOException e) {
        Log.e(TAG, "Could not save file", e);
      }
      if (success) {
        String relativePathInExperiment =
            FileMetadataUtil.getInstance().getRelativePathInExperiment(experimentId, imageFile);
        String overviewPath =
            PictureUtils.getExperimentOverviewRelativeImagePath(
                experimentId, relativePathInExperiment);
        setImagePath(overviewPath);
        PictureUtils.loadExperimentOverviewImage(appAccount, photoPreview, overviewPath);
      }
      return;
    } else if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
      if (resultCode == Activity.RESULT_OK) {
        String overviewPath =
            PictureUtils.getExperimentOverviewRelativeImagePath(experimentId, pictureLabelPath);
        setImagePath(overviewPath);
        PictureUtils.loadExperimentImage(
            getActivity(), photoPreview, appAccount, experimentId, pictureLabelPath, false);
      } else {
        pictureLabelPath = null;
      }
      // TODO: cancel doesn't restore old picture path.
      return;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  public void setImagePath(String overviewPath) {
    experiment
        .firstElement()
        .subscribe(
            e -> {
              e.setImagePath(overviewPath);
              saveExperiment();
            });
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putString(KEY_SAVED_PICTURE_PATH, pictureLabelPath);
    super.onSaveInstanceState(outState);
  }

  /**
   * Copies a content URI returned from ACTION_GET_CONTENT intent to another file.
   *
   * @param uri A content URI to get the content from using content resolver.
   * @param destFile A destination file to store the copy into.
   * @throws IOException
   */
  public static void copyUriToFile(Context context, Uri uri, File destFile) throws IOException {
    try (InputStream source = context.getContentResolver().openInputStream(uri);
        FileOutputStream dest = new FileOutputStream(destFile)) {
      ByteStreams.copy(source, dest);
    }
  }

  private void attachExperimentDetails(final Experiment experiment) {
    this.experiment.onNext(experiment);
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
  }

  /** Save the experiment */
  private void saveExperiment() {
    getDataController()
        .updateExperiment(
            experimentId,
            new LoggingConsumer<Success>(TAG, "update experiment") {
              @Override
              public void fail(Exception e) {
                super.fail(e);
                AccessibilityUtils.makeSnackbar(
                        getView(),
                        getResources().getString(R.string.experiment_save_failed),
                        Snackbar.LENGTH_SHORT)
                    .show();
              }

              @Override
              public void success(Success value) {
                // Do nothing
              }
            });
  }
}
