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

package com.google.android.apps.forscience.whistlepunk.project;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Fragment for saving/updating projects details (title, cover,...etc).
 */
public class UpdateProjectFragment extends Fragment {

    private static final String TAG = "UpdateProjectFragment";

    /**
     * Project ID to get the details for when updating project.
     */
    public static final String ARG_PROJECT_ID = "project_id";

    /**
     * Boolean extra denoting if this is a new project.
     */
    public static final String ARG_NEW = "new";

    /**
     * An action ID to handle results returned from photo picker intent.
     */
    private static final int SELECT_PHOTO = 1;

    /**
     * Alpha to use for add image drawable;
     */
    private static final int ADD_IMAGE_ALPHA = (int) (255 * .5f);

    /**
     * Permission request for writing to external storage.
     */
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    private String mProjectId;
    private Project mProject;
    private EditText mProjectTitle;
    private EditText mProjectDescription;
    private ImageButton mProjectCoverButton;
    private TextView mCoverButtonLabel;
    private boolean mWasEdited;

    /**
     * This is set if we get a photo result before the project finishes loading.
     */
    private String mPendingPhotoPath;

    public UpdateProjectFragment() {
    }

    public static UpdateProjectFragment newInstance(String projectId, boolean newProject) {
        UpdateProjectFragment fragment = new UpdateProjectFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        args.putBoolean(ARG_NEW, newProject);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        getActivity().setTitle(getString(isNewProject() ? R.string.title_activity_new_project :
                R.string.title_activity_update_project));
    }

    @Override
    public void onStart() {
        super.onStart();
        mProjectId = getArguments().getString(ARG_PROJECT_ID);

        getDataController().getProjectById(mProjectId,
                new LoggingConsumer<Project>(TAG, "retrieve project") {
                    @Override
                    public void success(Project project) {
                        attachProjectDetails(project);
                    }
                });
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(isNewProject() ?
                TrackerConstants.SCREEN_NEW_PROJECT : TrackerConstants.SCREEN_UPDATE_PROJECT);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!isNewProject() && mWasEdited) {
            WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(TrackerConstants.CATEGORY_PROJECTS,
                            TrackerConstants.ACTION_EDITED,
                            null, 0);
            mWasEdited = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_update_project, container, false);

        mCoverButtonLabel = (TextView) view.findViewById(R.id.project_cover_label);
        mProjectTitle = (EditText) view.findViewById(R.id.project_title);
        mProjectDescription = (EditText) view.findViewById(R.id.project_description);

        mProjectCoverButton = (ImageButton) view.findViewById(R.id.project_cover);
        mProjectCoverButton.getDrawable().setAlpha(ADD_IMAGE_ALPHA);
        mProjectCoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(getActivity(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED) {
                    launchPhotoPicker();
                } else {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
                }
            }
        });
        mProjectTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mProject == null) {
                    return;
                }
                if (!s.toString().equals(mProject.getTitle())) {
                    mProject.setTitle(s.toString().trim());
                    mWasEdited = true;
                    saveProject();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        mProjectDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mProject == null) {
                    return;
                }
                if (!s.toString().equals(mProject.getDescription())) {
                    mProject.setDescription(s.toString().trim());
                    mWasEdited = true;
                    saveProject();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_update_project, menu);
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (isNewProject()) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        }
        // Hide the save button if updating. All data should be saved automatically.
        menu.findItem(R.id.action_save).setVisible(isNewProject());
        actionBar.setDisplayHomeAsUpEnabled(true);

        actionBar.setTitle(getString(isNewProject() ? R.string.title_activity_new_project :
                R.string.title_activity_update_project));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            if (isNewProject()) {
                // we should delete the project: the user killed it without saving
                // TODO: should warn the user if they edited anything.
                deleteProject();
            } else {
                goToDetails();
            }
            return true;
        } else if (id == R.id.action_save) {
            saveProject(true /* go to details when done */);
            WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(TrackerConstants.CATEGORY_PROJECTS,
                            TrackerConstants.ACTION_CREATE,
                            null, 0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean isNewProject() {
        return getArguments().getBoolean(ARG_NEW, false);
    }

    private void attachProjectDetails(final Project project) {
        mProject = project;
        mProjectTitle.setText(project.getTitle());
        mProjectDescription.setText(project.getDescription());
        if (!TextUtils.isEmpty(mPendingPhotoPath)
                && !mPendingPhotoPath.equals(mProject.getCoverPhoto())) {
            // We got a request for a new photo before the project loaded. update this now.
            project.setCoverPhoto(mPendingPhotoPath);
            // Now clear out the pending field.
            mPendingPhotoPath = null;
            saveProject();
        }
        if (project.getCoverPhoto() != null) {
            loadImage(project.getCoverPhoto());
            mCoverButtonLabel.setTextColor(Color.WHITE);
        }
        mWasEdited = false;
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    private void goToDetails() {
        Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
        upIntent.putExtra(ProjectDetailsFragment.ARG_PROJECT_ID, mProjectId);
        NavUtils.navigateUpTo(getActivity(), upIntent);
    }

    /**
     * Saves the project and stays on the same screen.
     */
    private void saveProject() {
        saveProject(false /* stay here */);
    }

    /**
     * Saves the project then optionally goes to project details based on
     * {@param goToDetailsWhenDone}.
     */
    private void saveProject(final boolean goToDetailsWhenDone) {
        mProject.setTitle(mProjectTitle.getText().toString().trim());
        mProject.setDescription(mProjectDescription.getText().toString().trim());
        getDataController().updateProject(mProject, new MaybeConsumer<Success>() {
            @Override
            public void fail(Exception e) {
                AccessibilityUtils.makeSnackbar(
                        getView(), getResources().getString(R.string.project_save_failed),
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void success(Success value) {
                if (goToDetailsWhenDone) {
                    goToDetails();
                }
            }
        });
    }

    private void deleteProject() {
        getDataController().deleteProject(mProject, new LoggingConsumer<Success>(TAG, "Deleting") {
            @Override
            public void success(Success value) {
                // Go back to the project list page
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.putExtra(MainActivity.ARG_SELECTED_NAV_ITEM_ID,
                        R.id.navigation_item_projects);
                NavUtils.navigateUpTo(getActivity(), intent);
            }
        });
    }

    private void launchPhotoPicker() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, SELECT_PHOTO);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check for non-null Uri here because of b/27899888
        if (requestCode == SELECT_PHOTO && resultCode == Activity.RESULT_OK
                && data.getData() != null) {
            try {
                // The ACTION_PICK intent give temporary access to the
                // selected photo. We need to copy the selected photo to
                // to another file to get the real absolute path and store
                // that file's path into the Project.
                File imageFile = PictureUtils.createImageFile(System.currentTimeMillis());
                copyUriToFile(data.getData(), imageFile);
                String path = "file:" + imageFile.getAbsolutePath();
                if (mProject != null) {
                    mProject.setCoverPhoto(path);
                    saveProject();
                } else {
                    mPendingPhotoPath = path;
                }
                loadImage(path);
                // Update the color of the text to be white on top of the image.
                mCoverButtonLabel.setTextColor(Color.WHITE);
                mWasEdited = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Copies a content URI returned from ACTION_PICK intent to another file.
     * @param uri A content URI to get the content from using content resolver.
     * @param destFile A destination file to store the copy into.
     * @throws IOException
     */
    private void copyUriToFile(Uri uri, File destFile) throws IOException {
        InputStream source = null;
        FileOutputStream dest = null;
        try {
            source = getActivity().getContentResolver().openInputStream(uri);
            dest = new FileOutputStream(destFile);
            ByteStreams.copy(source, dest);
        } finally {
            if (source != null) {
                source.close();
            }
            if (dest != null) {
                dest.close();
            }
        }
    }

    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {

        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted. Launch photo picker.
                launchPhotoPicker();
            } else {
                // Permission denied. Do nothing.
                AccessibilityUtils.makeSnackbar(
                        getView(), getResources().getString(R.string.permission_request_write),
                        Snackbar.LENGTH_SHORT).show();
            }
        }

    }

    private void loadImage(String path) {
        Glide.with(this)
                .load(path)
                .centerCrop()
                .into(mProjectCoverButton);
    }
}
