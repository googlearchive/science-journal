/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.review.labels;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Change;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import java.io.File;

/** Details view controller for PictureLabel */
public class PictureLabelDetailsFragment extends LabelDetailsFragment {

  private ImageView imageView;
  private Intent shareIntent;

  public static PictureLabelDetailsFragment newInstance(
      AppAccount appAccount, String experimentId, String trialId, Label originalLabel) {
    PictureLabelDetailsFragment result = new PictureLabelDetailsFragment();
    Bundle args = new Bundle();
    args.putString(LabelDetailsActivity.ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(LabelDetailsActivity.ARG_EXPERIMENT_ID, experimentId);
    args.putString(LabelDetailsActivity.ARG_TRIAL_ID, trialId);
    args.putParcelable(LabelDetailsActivity.ARG_LABEL, originalLabel);
    result.setArguments(args);
    return result;
  }

  public PictureLabelDetailsFragment() {
    // Required empty public constructor
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
    setHasOptionsMenu(true);
    View rootView = inflater.inflate(R.layout.picture_label_details_fragment, container, false);

    setupCaption(rootView);

    imageView = (ImageView) rootView.findViewById(R.id.image);

    // TODO: Transition

    return rootView;
  }

  @Override
  public void onResume() {
    super.onResume();
    // Load on resume in case edits have been made.
    PictureUtils.loadExperimentImage(
        getActivity(),
        imageView,
        appAccount,
        experimentId,
        originalLabel.getPictureLabelValue().getFilePath(),
        false);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_picture_label_details, menu);
    getContext();
    shareIntent =
        FileMetadataUtil.getInstance()
            .createPhotoShareIntent(
                getContext(),
                appAccount,
                experimentId,
                originalLabel.getPictureLabelValue().getFilePath(),
                originalLabel.getCaptionText());
    if (shareIntent != null) {
      menu.findItem(R.id.btn_share_photo).setVisible(true);
    }

    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    actionBar.setTitle("");

    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_edit) {
      // Add a change here so we re-upload the image. We never get confirmation that the image
      // has changed from our intent, and it's not a big deal if we do this unnecessarily.
      experiment
          .getValue()
          .addChange(Change.newModifyTypeChange(ElementType.NOTE, originalLabel.getLabelId()));
      PictureUtils.launchExternalEditor(
          getActivity(),
          appAccount,
          experimentId,
          originalLabel.getPictureLabelValue().getFilePath());
      return true;
    } else if (item.getItemId() == R.id.btn_share_photo) {
      if (shareIntent != null) {
        getContext()
            .startActivity(
                Intent.createChooser(
                    shareIntent,
                    getContext().getResources().getString(R.string.export_photo_chooser_title)));
      }
      return true;
    } else if (item.getItemId() == R.id.btn_download_photo) {
      requestDownload();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private void requestDownload() {
    // TODO(b/130895178): Figure out why onPermissionPermanentlyDenied isn't being called here.
    Context context = getContext();
    ExportService.requestDownloadPermissions(
        () -> {
          String sourcePath = originalLabel.getPictureLabelValue().getFilePath();
          File sourceFile =
              new File(
                  PictureUtils.getExperimentImagePath(
                      context, appAccount, experimentId, sourcePath));
          Uri sourceUri = Uri.fromFile(sourceFile);
          ExportService.saveToDownloads(context, sourceUri);
        },
        getActivity(),
        R.id.root_layout,
        TrackerConstants.CATEGORY_NOTES,
        TrackerConstants.LABEL_PICTURE_DETAIL);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(R.id.action_edit);
    item.getIcon().mutate().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
    super.onPrepareOptionsMenu(menu);
  }
}
