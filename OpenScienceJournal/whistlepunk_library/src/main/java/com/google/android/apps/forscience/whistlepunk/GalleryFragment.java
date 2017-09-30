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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.project.experiment.UpdateExperimentFragment;
import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.SingleSubject;

/**
 * Fragment controlling adding picture notes from the gallery in the observe pane.
 */
public class GalleryFragment extends PanesToolFragment implements
        LoaderManager.LoaderCallbacks<List<PhotoAsyncLoader.Image>> {
    private static final String TAG = "GalleryFragment";
    private static final int PHOTO_LOADER_INDEX = 1;
    private static final String KEY_SELECTED_PHOTO = "selected_photo";

    // Same methods as the camera fragment, so share the listener code.
    private Listener mListener;
    private GalleryItemAdapter mGalleryAdapter;
    private int mInitiallySelectedPhoto;
    private SingleSubject<LoaderManager> mWhenLoaderManager = SingleSubject.create();
    private BehaviorSubject<Boolean> mAddButtonEnabled = BehaviorSubject.create();
    private BehaviorSubject<Boolean> mPermissionGranted = BehaviorSubject.create();
    private RxEvent mDestroyed = new RxEvent();

    public interface Listener {
        Observable<String> getActiveExperimentId();
        void onPictureLabelTaken(Label label);
        RxPermissions getPermissions();
    }

    public interface ListenerProvider {
        Listener getGalleryListener();
    }

    public static Fragment newInstance() {
        return new GalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInitiallySelectedPhoto =
                savedInstanceState != null ? savedInstanceState.getInt(KEY_SELECTED_PHOTO) : -1;

        mGalleryAdapter = new GalleryItemAdapter(getActivity(), (image, selected) -> {
            mAddButtonEnabled.onNext(selected && !TextUtils.isEmpty(image));
        });

        mPermissionGranted.distinctUntilChanged()
                          .takeUntil(mDestroyed.happens())
                          .subscribe(granted -> {
                              if (granted) {
                                  loadImages();
                              } else {
                                  complainPermissions();
                              }
                          });

        mWhenLoaderManager.onSuccess(getLoaderManager());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void panesOnAttach(Context context) {
        if (context instanceof ListenerProvider) {
            mListener = ((ListenerProvider) context).getGalleryListener();
        }
        Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof ListenerProvider) {
            mListener = ((ListenerProvider) parentFragment).getGalleryListener();
        }

        super.panesOnAttach(context);
    }

    // TODO: duplicate logic with CameraFragment?
    private void requestPermission() {
        RxPermissions permissions = mListener.getPermissions();
        if (permissions == null) {
            return;
        }
        permissions.request(Manifest.permission.READ_EXTERNAL_STORAGE).subscribe(granted -> {
            mPermissionGranted.onNext(granted);
        });
    }

    @Nullable
    @Override
    public View onCreatePanesView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.gallery_fragment, null);

        RecyclerView gallery = (RecyclerView) rootView.findViewById(R.id.gallery);
        GridLayoutManager layoutManager = new GridLayoutManager(gallery.getContext(),
                gallery.getContext().getResources().getInteger(R.integer.gallery_column_count));
        gallery.setLayoutManager(layoutManager);
        gallery.setItemAnimator(new DefaultItemAnimator());
        gallery.setAdapter(mGalleryAdapter);

        requestPermission();
        return rootView;
    }

    private void complainPermissions() {
        View rootView = getView();
        if (rootView != null) {
            rootView.findViewById(R.id.gallery).setVisibility(View.GONE);
            rootView.findViewById(R.id.complaint).setVisibility(View.VISIBLE);

            RxView.clicks(rootView.findViewById(R.id.open_settings))
                  .subscribe(click -> requestPermission());
        }
    }

    public void attachAddButton(View rootView) {
        ImageButton addButton = (ImageButton) rootView.findViewById(R.id.btn_add);
        addButton.setOnClickListener(view -> {
            final long timestamp = getTimestamp(addButton.getContext());
            GoosciPictureLabelValue.PictureLabelValue labelValue =
                    new GoosciPictureLabelValue.PictureLabelValue();
            String selectedImage = mGalleryAdapter.getSelectedImage();
            if (TextUtils.isEmpty(selectedImage)) {
                // TODO: Is this possible?
                return;
            }

            mListener.getActiveExperimentId().firstElement().subscribe(experimentId -> {
                Label result = Label.newLabel(timestamp, GoosciLabel.Label.PICTURE);
                File imageFile = PictureUtils.createImageFile(getActivity(), experimentId,
                        result.getLabelId());

                try {
                    UpdateExperimentFragment.copyUriToFile(addButton.getContext(),
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
                mListener.onPictureLabelTaken(result);
                mGalleryAdapter.deselectImages();
            });
        });


        // TODO: Need to update the content description if we are recording or not.
        addButton.setEnabled(false);

        mAddButtonEnabled.subscribe(enabled -> addButton.setEnabled(enabled));
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

    private void loadImages() {
        View rootView = getView();
        if (rootView != null) {
            rootView.findViewById(R.id.gallery).setVisibility(View.VISIBLE);
            rootView.findViewById(R.id.complaint).setVisibility(View.GONE);
        }

        if (getActivity() != null) {
            mWhenLoaderManager.subscribe(
                    manager -> manager.initLoader(PHOTO_LOADER_INDEX, null, this));
        }
    }

    @Override
    public Loader<List<PhotoAsyncLoader.Image>> onCreateLoader(int i, Bundle bundle) {
        return new PhotoAsyncLoader(getActivity().getApplicationContext());
    }

    @Override
    public void onLoadFinished(Loader<List<PhotoAsyncLoader.Image>> loader,
            List<PhotoAsyncLoader.Image> images) {

        mGalleryAdapter.setImages(images);
        if (mInitiallySelectedPhoto != -1) {
            // Resume from saved state if needed.
            mGalleryAdapter.setSelectedIndex(mInitiallySelectedPhoto);
            String image = mGalleryAdapter.getSelectedImage();
            mAddButtonEnabled.onNext(!TextUtils.isEmpty(image));
            mInitiallySelectedPhoto = -1;
        }
    }

    @Override
    public void onLoaderReset(Loader<List<PhotoAsyncLoader.Image>> loader) {
        mGalleryAdapter.clearImages();
    }

    private static class GalleryItemAdapter extends
            RecyclerView.Adapter<GalleryItemAdapter.ViewHolder> {

        interface ImageClickListener {
            void onImageClicked(String image, boolean selected);
        }

        private List<PhotoAsyncLoader.Image> mImages;
        private final Context mContext;
        private final ImageClickListener mListener;
        private int mSelectedIndex = -1;
        private final String mContentDescriptionPrefix;

        public GalleryItemAdapter(Context context, ImageClickListener listener) {
            mImages = new ArrayList<>();
            mContext = context;
            mListener = listener;
            mContentDescriptionPrefix = context.getResources().getString(
                    R.string.gallery_image_content_description);
        }

        public void setImages(List<PhotoAsyncLoader.Image> images) {
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
            String path = mImages.get(position).path;

            GlideApp.with(mContext)
                    .load(path)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                Target<Drawable> target,
                                boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                Target<Drawable> target,
                                DataSource dataSource, boolean isFirstResource) {
                            holder.image.setBackground(null);
                            return false;
                        }
                    })
                    .thumbnail(.5f)
                    .into(holder.image);
            holder.image.setContentDescription(String.format(mContentDescriptionPrefix,
                    DateUtils.formatDateTime(mContext, mImages.get(position).timestampTaken,
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME |
                            DateUtils.FORMAT_ABBREV_ALL)));

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
                mListener.onImageClicked(path, newlySelected);
            });
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.image.setBackgroundColor(mContext.getResources()
                                                    .getColor(R.color.background_color));
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return mImages.size();
        }

        public String getSelectedImage() {
            if (mSelectedIndex == -1) {
                return null;
            }
            return mImages.get(mSelectedIndex).path;
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
