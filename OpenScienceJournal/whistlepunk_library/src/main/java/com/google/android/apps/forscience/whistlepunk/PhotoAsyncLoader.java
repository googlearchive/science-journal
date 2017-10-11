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

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Asynchronous photo loader tool to get photos from the gallery.
 * Inspired by https://github.com/rexstjohn/UltimateAndroidCameraGuide/
 */
class PhotoAsyncLoader extends AsyncTaskLoader<List<PhotoAsyncLoader.Image>> {

    public class Image {
        public String path;
        public long timestampTaken;

        public Image(String path, long timestampTaken) {
            this.path = path;
            this.timestampTaken = timestampTaken;
        }
    }

    private List<Image> mItems;

    public PhotoAsyncLoader(Context context) {
        super(context);
    }

    @Override
    protected void onStartLoading() {
        // Don't reload if we already have the result.
        if (mItems != null && mItems.size() > 0) {
            deliverResult(mItems);
        } else {
            forceLoad();
        }
    }

    @Override
    public List<Image> loadInBackground() {
        // TODO: Maybe add date taken to results for content description
        final String[] projection = {MediaStore.Images.Media.DATA,
                MediaStore.Images.Media._ID, MediaStore.Images.ImageColumns.DATE_TAKEN};
        String sortOrder = MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC";

        Cursor cursor = getContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, // Which columns to return
                null,       // Return all rows
                null,
                sortOrder); // In order of date taken

        mItems = new ArrayList<>(cursor.getCount());

        if (cursor.moveToFirst()) {
            do {
                mItems.add(new Image(uriToFullImage(cursor), timestampCreated(cursor)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return mItems;
    }

    /**
     * Get the path to the full image for a given thumbnail.
     */
    private static String uriToFullImage(Cursor mediaCursor) {
        String filePath = "file://" + mediaCursor.getString(0);
        return filePath;
    }

    /**
     * Gets the timestamp created for a given thumbnail.
     */
    private static long timestampCreated(Cursor mediaCursor) {
        return mediaCursor.getLong(2);
    }
}
