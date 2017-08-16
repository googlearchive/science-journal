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

package com.google.android.apps.forscience.whistlepunk;

import android.Manifest;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.project.experiment.UpdateExperimentFragment;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment controlling adding picture notes from the gallery in the observe pane.
 */
public class GalleryFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<List<String>> {
    private static final String TAG = "GalleryFragment";
    private static final int PHOTO_LOADER_INDEX = 1;
    private static final String KEY_SELECTED_PHOTO = "selected_photo";

    // Same methods as the camera fragment, so share the listener code.
    private CameraFragment.CameraFragmentListener mListener;
    private GalleryItemAdapter mGalleryAdapter;
    private ImageButton mAddButton;
    private int mInitiallySelectedPhoto;

    public static Fragment newInstance(RxPermissions permissions) {
        GalleryFragment fragment = new GalleryFragment();

        // TODO: use RxPermissions instead of PermissionsUtils everywhere?
        permissions.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                   .firstElement()
                   .subscribe(granted -> {
                       if (granted) {
                           fragment.loadImages();
                       }
                       // TODO: else to show retry and error.
                   });
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInitiallySelectedPhoto =
                savedInstanceState != null ? savedInstanceState.getInt(KEY_SELECTED_PHOTO) : -1;

        mGalleryAdapter = new GalleryItemAdapter(getActivity(), (image, selected) -> {
            if (selected) {
                mAddButton.setEnabled(!TextUtils.isEmpty(image));
            } else {
                mAddButton.setEnabled(false);
            }
        });

        new RxPermissions(getActivity()).request(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        .firstElement()
                                        .subscribe(granted -> {
                                            if (granted) {
                                                loadImages();
                                            }
                                        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.gallery_fragment, null);

        mAddButton = (ImageButton) rootView.findViewById(R.id.btn_add);
        mAddButton.setOnClickListener(view -> {
            final long timestamp = getTimestamp(mAddButton.getContext());
            GoosciPictureLabelValue.PictureLabelValue labelValue =
                    new GoosciPictureLabelValue.PictureLabelValue();
            String selectedImage = mGalleryAdapter.getSelectedImage();
            if (TextUtils.isEmpty(selectedImage)) {
                // TODO: Is this possible?
                return;
            }

            CameraFragment.CameraFragmentListener listener =
                    getListener(mAddButton.getContext());
            listener.getActiveExperimentId().firstElement().subscribe(experimentId -> {
                Label result = Label.newLabel(timestamp, GoosciLabel.Label.PICTURE);
                File imageFile = PictureUtils.createImageFile(getActivity(), experimentId,
                        result.getLabelId());

                try {
                    UpdateExperimentFragment.copyUriToFile(mAddButton.getContext(),
                            Uri.parse(selectedImage), imageFile);
                    labelValue.filePath = FileMetadataManager.getRelativePathInExperiment(
                            experimentId, imageFile);
                } catch (IOException e) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, e.getMessage());
                    }
                    labelValue.filePath = "";
                }

                result.setLabelProtoData(labelValue);
                listener.onPictureLabelTaken(result);
                mGalleryAdapter.deselectImages();
            });
        });

        // TODO: Need to update the content description if we are recording or not.
        // This will probably happen in the ControlBar rather than here.
        mAddButton.setEnabled(false);

        RecyclerView gallery = (RecyclerView) rootView.findViewById(R.id.gallery);
        GridLayoutManager layoutManager = new GridLayoutManager(gallery.getContext(),
                gallery.getContext().getResources().getInteger(R.integer.gallery_column_count));
        gallery.setLayoutManager(layoutManager);
        gallery.setItemAnimator(new DefaultItemAnimator());
        gallery.setAdapter(mGalleryAdapter);

        return rootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_PHOTO, mGalleryAdapter.mSelectedIndex);
    }

    private long getTimestamp(Context context) {
        return getClock(context).getNow();
    }

    private Clock getClock(Context context) {
        return AppSingleton.getInstance(context)
                .getSensorEnvironment()
                .getDefaultClock();
    }

    private CameraFragment.CameraFragmentListener getListener(Context context) {
        if (mListener == null) {
            if (context instanceof CameraFragment.ListenerProvider) {
                mListener = ((CameraFragment.ListenerProvider) context).getCameraFragmentListener();
            } else {
                Fragment parentFragment = getParentFragment();
                if (parentFragment instanceof CameraFragment.ListenerProvider) {
                    mListener = ((CameraFragment.ListenerProvider) parentFragment)
                            .getCameraFragmentListener();
                }
            }
        }
        return mListener;
    }

    private void loadImages() {
        getLoaderManager().initLoader(PHOTO_LOADER_INDEX, null, this);
    }

    @Override
    public Loader<List<String>> onCreateLoader(int i, Bundle bundle) {
        return new PhotoAsyncLoader(getActivity().getApplicationContext());
    }

    @Override
    public void onLoadFinished(Loader<List<String>> loader, List<String> imageUris) {
        mGalleryAdapter.setImages(imageUris);
        if (mInitiallySelectedPhoto != -1) {
            // Resume from saved state if needed.
            mGalleryAdapter.setSelectedIndex(mInitiallySelectedPhoto);
            String image = mGalleryAdapter.getSelectedImage();
            mAddButton.setEnabled(!TextUtils.isEmpty(image));
            mInitiallySelectedPhoto = -1;
        }
    }

    @Override
    public void onLoaderReset(Loader<List<String>> loader) {
        mGalleryAdapter.clearImages();
    }

    private static class GalleryItemAdapter extends
            RecyclerView.Adapter<GalleryItemAdapter.ViewHolder> {

        interface ImageClickListener {
            void onImageClicked(String image, boolean selected);
        }

        private List<String> mImages;
        private final Context mContext;
        private final ImageClickListener mListener;
        private int mSelectedIndex = -1;

        public GalleryItemAdapter(Context context, ImageClickListener listener) {
            mImages = new ArrayList<>();
            mContext = context;
            mListener = listener;
        }

        public void setImages(List<String> images) {
            mImages.clear();
            mImages.addAll(images);
            notifyDataSetChanged();
        }

        public void clearImages() {
            mImages.clear();
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent,
                int viewType) {
            View itemView = LayoutInflater.from(mContext).inflate(R.layout.gallery_image, parent,
                    false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String image = mImages.get(position);

            Glide.with(mContext)
                    .load(image)
                    .thumbnail(.5f)
                    .into(holder.image);

            boolean selected = position == mSelectedIndex;
            holder.selectedIndicator.setVisibility(selected ? View.VISIBLE : View.GONE);

            holder.image.setOnClickListener(view -> {
                boolean newlySelected = holder.getAdapterPosition() != mSelectedIndex;
                if (newlySelected) {
                    // It was clicked for the first time.
                    setSelectedIndex(holder.getAdapterPosition());
                } else {
                    // A second click on the same image is a deselect.
                    mSelectedIndex = -1;
                }
                holder.selectedIndicator.setVisibility(newlySelected ? View.VISIBLE : View.GONE);
                mListener.onImageClicked(image, newlySelected);
            });
        }

        @Override
        public int getItemCount() {
            return mImages.size();
        }

        public String getSelectedImage() {
            if (mSelectedIndex == -1) {
                return null;
            }
            return mImages.get(mSelectedIndex);
        }

        public void deselectImages() {
            int previous = mSelectedIndex;
            mSelectedIndex = -1;
            notifyItemChanged(previous);
        }

        public void setSelectedIndex(int indexToSelect) {
            deselectImages();
            if (indexToSelect < mImages.size()) {
                mSelectedIndex = indexToSelect;
                notifyItemChanged(indexToSelect);
            } else {
                mSelectedIndex = -1;
            }
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ImageView image;
            public View selectedIndicator;

            public ViewHolder(View itemView) {
                super(itemView);
                image = (ImageView) itemView.findViewById(R.id.image);
                selectedIndicator = itemView.findViewById(R.id.selected_indicator);
            }
        }
    }
}
