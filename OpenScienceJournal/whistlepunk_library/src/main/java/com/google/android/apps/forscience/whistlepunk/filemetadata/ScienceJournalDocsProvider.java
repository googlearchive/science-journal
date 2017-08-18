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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Provides pictures for access outside the SJ app.
 * TODO: Add thumbnails.
 */
public class ScienceJournalDocsProvider extends DocumentsProvider {
    private static final String TAG = "DocumentsProvider";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
    };

    private static final String ROOT_DIRECTORY_ID = "ScienceJournalRoot";

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        // Use a MatrixCursor to build a cursor
        // with either the requested fields, or the default
        // projection if "projection" is null.
        final MatrixCursor result =
                new MatrixCursor(resolveRootProjection(projection));

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, DEFAULT_ROOT_PROJECTION);

        // TODO: Implement Root.FLAG_SUPPORTS_RECENTS and Root.FLAG_SUPPORTS_SEARCH.
        // This will mean documents will show up in the "recents" category and be searchable.

        // COLUMN_TITLE is the root title (e.g. Gallery, Drive).
        row.add(Root.COLUMN_TITLE, getContext().getString(R.string.app_name));

        // This document id cannot change after it's shared.
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_DIRECTORY_ID);

        // The child MIME types are used to filter the roots and only present to the
        // user those roots that contain the desired type somewhere in their file hierarchy.
        row.add(Root.COLUMN_MIME_TYPES, "image/*");

        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);

        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
            String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new
                MatrixCursor(resolveDocumentProjection(projection));
        if (TextUtils.equals(parentDocumentId, ROOT_DIRECTORY_ID)) {
            // The sub-directories are all experiments. Use their experiment ID as document ID.
            // TODO: Use notifyChange to load data off-thread and update only when it is available.
            List<GoosciUserMetadata.ExperimentOverview> overviews =
                    AppSingleton.getInstance(getContext()).getDataController()
                            .blockingGetExperimentOverviews(true);
            for (GoosciUserMetadata.ExperimentOverview overview : overviews) {
                MatrixCursor.RowBuilder row = result.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, overview.experimentId);
                addExperimentToRow(row, overview);
            }
        } else {
            // The sub-files are all within the assets folder of this experiment
            File assetsDir = FileMetadataManager.getAssetsDirectory(getContext(), parentDocumentId);
            for (File file : assetsDir.listFiles()) {
                MatrixCursor.RowBuilder row = result.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, parentDocumentId + "/" +
                        FileMetadataManager.ASSETS_DIRECTORY + "/" + file.getName());
                addAssetToRow(row, file);
            }
        }
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws
            FileNotFoundException {
        // Create a cursor with the requested projection, or the default projection.
        final MatrixCursor result = new
                MatrixCursor(resolveDocumentProjection(projection));
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);

        if (TextUtils.equals(documentId, ROOT_DIRECTORY_ID)) {
            row.add(Document.COLUMN_DISPLAY_NAME,
                    getContext().getResources().getString(R.string.app_name));
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
            row.add(Document.COLUMN_LAST_MODIFIED, null); // Not sure
            row.add(Document.COLUMN_SIZE, null);
        } else if (!documentId.contains("/")) {
            // It is an experiment directory
            // TODO: Use notifyChange to load data off-thread and update only when it is available.
            List<GoosciUserMetadata.ExperimentOverview> overviews =
                    AppSingleton.getInstance(getContext()).getDataController()
                            .blockingGetExperimentOverviews(true);
            for (GoosciUserMetadata.ExperimentOverview overview : overviews) {
                if (TextUtils.equals(overview.experimentId, documentId)) {
                    addExperimentToRow(row, overview);
                    break;
                }
            }

        } else {
            // It is a file
            File file = new File(FileMetadataManager.getExperimentsRootDirectory(getContext()) +
                    "/" + documentId);
            addAssetToRow(row, file);
        }
        return result;
    }

    // From http://www.programcreek.com/java-api-examples/index.php?source_dir=PSD-master/ANDROID%20PSD/aFileChooser/src/com/ianhanniballake/localstorage/LocalStorageProvider.java
    @Override
    public ParcelFileDescriptor openDocument(final String documentId, final String mode,
            CancellationSignal signal) throws FileNotFoundException {
        Log.v(TAG, "openDocument, mode: " + mode);

        final File file = new File(
                FileMetadataManager.getExperimentsRootDirectory(getContext()) + "/" + documentId);
        final boolean isWrite = (mode.indexOf('w') != -1);
        if (isWrite) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        } else {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }

    @Override
    public boolean onCreate() {
        // return true if the provider was successfully loaded
        return true;
    }

    private String[] resolveRootProjection(String[] projection) {
        // It's possible to have multiple roots (e.g. for multiple accounts in the
        // same app) -- just add multiple cursor rows.
        // When we allow multiple sign-in, implement this.
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    // From http://www.programcreek.com/java-api-examples/index.php?source_dir=PSD-master/ANDROID%20PSD/aFileChooser/src/com/ianhanniballake/localstorage/LocalStorageProvider.java
    private String getMimeType(File file) {
        final int lastDot = file.getName().lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = file.getName().substring(lastDot + 1);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    private void addExperimentToRow(MatrixCursor.RowBuilder row,
            GoosciUserMetadata.ExperimentOverview overview) {
        row.add(Document.COLUMN_DISPLAY_NAME, Experiment.getDisplayTitle(getContext(),
                overview.title));
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        row.add(Document.COLUMN_LAST_MODIFIED, overview.lastUsedTimeMs);
        row.add(Document.COLUMN_SIZE, null);
    }

    private void addAssetToRow(MatrixCursor.RowBuilder row, File file) {
        row.add(Document.COLUMN_DISPLAY_NAME, Experiment.getDisplayTitle(getContext(),
                file.getName()));
        row.add(Document.COLUMN_MIME_TYPE, getMimeType(file));
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_SIZE, file.length());
    }
}
