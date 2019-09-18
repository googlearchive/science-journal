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

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Provides pictures for access outside the SJ app. TODO: Add thumbnails. */
public class ScienceJournalDocsProvider extends DocumentsProvider {
  private static final String TAG = "DocumentsProvider";

  private static final String[] DEFAULT_ROOT_PROJECTION =
      new String[] {
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_MIME_TYPES,
        Root.COLUMN_FLAGS,
        Root.COLUMN_ICON,
        Root.COLUMN_TITLE,
        Root.COLUMN_DOCUMENT_ID,
      };

  private static final String[] DEFAULT_DOCUMENT_PROJECTION =
      new String[] {
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
    final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));

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
  public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
      throws FileNotFoundException {
    Set<AppAccount> accounts =
        WhistlePunkApplication.getAppServices(getContext()).getAccountsProvider().getAccounts();
    Set<String> accountKeys = new HashSet<>();
    for (AppAccount appAccount : accounts) {
      accountKeys.add(appAccount.getAccountKey());
    }
    final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
    if (TextUtils.equals(parentDocumentId, ROOT_DIRECTORY_ID)) {
      // The sub-directories are accounts. Use their account key as document ID.
      for (AppAccount appAccount : accounts) {
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, appAccount.getAccountKey());
        String accountName = appAccount.getAccountName();
        if (accountName.isEmpty()) {
          accountName = getContext().getResources().getString(R.string.unclaimed_experiments);
        }
        addAccountToRow(row, accountName);
      }
    } else if (accountKeys.contains(parentDocumentId)) {
      // The sub-directories are experiments. Use their experiment id as document ID.
      // TODO: Use notifyChange to load data off-thread and update only when it is available.
      for (AppAccount appAccount : accounts) {
        if (appAccount.getAccountKey().equals(parentDocumentId)) {
          List<ExperimentOverviewPojo> overviews =
              AppSingleton.getInstance(getContext())
                  .getDataController(appAccount)
                  .blockingGetExperimentOverviews(true);
          for (ExperimentOverviewPojo overview : overviews) {
            MatrixCursor.RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, overview.getExperimentId());
            addExperimentToRow(row, overview);
          }
        }
      }
    } else {
      // The sub-files are all within the assets folder of this experiment
      File assetsDir =
          FileMetadataUtil.getInstance().getAssetsDirectory(
              getAppAccountFromDocumentId(parentDocumentId), parentDocumentId);
      for (File file : assetsDir.listFiles()) {
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(
            Document.COLUMN_DOCUMENT_ID,
            parentDocumentId + "/" + FileMetadataManager.ASSETS_DIRECTORY + "/" + file.getName());
        addAssetToRow(row, file);
      }
    }
    return result;
  }

  @Override
  public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
    AppAccount appAccount = getAppAccountFromDocumentId(documentId);

    // Create a cursor with the requested projection, or the default projection.
    final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
    MatrixCursor.RowBuilder row = result.newRow();
    row.add(Document.COLUMN_DOCUMENT_ID, documentId);

    if (TextUtils.equals(documentId, ROOT_DIRECTORY_ID)) {
      row.add(
          Document.COLUMN_DISPLAY_NAME, getContext().getResources().getString(R.string.app_name));
      row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
      row.add(Document.COLUMN_LAST_MODIFIED, null); // Not sure
      row.add(Document.COLUMN_SIZE, null);

    } else if (WhistlePunkApplication.getAppServices(getContext())
        .getAccountsProvider().isAppAccount(documentId)) {
      // It is an app account
      addAccountToRow(row, appAccount.getAccountName());

    } else if (!documentId.contains("/")) {
      // It is an experiment directory
      // TODO: Use notifyChange to load data off-thread and update only when it is available.
      List<ExperimentOverviewPojo> overviews =
          AppSingleton.getInstance(getContext())
              .getDataController(appAccount)
              .blockingGetExperimentOverviews(true);
      for (ExperimentOverviewPojo overview : overviews) {
        if (TextUtils.equals(overview.getExperimentId(), documentId)) {
          addExperimentToRow(row, overview);
          break;
        }
      }

    } else {
      // It is a file
      File file =
          new File(
              FileMetadataUtil.getInstance().getExperimentsRootDirectory(appAccount)
                  + "/"
                  + documentId);
      addAssetToRow(row, file);
    }
    return result;
  }

  // From
  // http://www.programcreek.com/java-api-examples/index.php?source_dir=PSD-master/ANDROID%20PSD/aFileChooser/src/com/ianhanniballake/localstorage/LocalStorageProvider.java
  @Override
  public ParcelFileDescriptor openDocument(
      final String documentId, final String mode, CancellationSignal signal)
      throws FileNotFoundException {
    Log.v(TAG, "openDocument, mode: " + mode);

    AppAccount appAccount = getAppAccountFromDocumentId(documentId);

    final File file =
        new File(
            FileMetadataUtil.getInstance().getExperimentsRootDirectory(appAccount)
                + "/"
                + documentId);
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

  private AppAccount getAppAccountFromDocumentId(String documentId) {
    // Figure out which account this documentId belongs to.
    AccountsProvider accountsProvider = WhistlePunkApplication.getAppServices(getContext())
        .getAccountsProvider();
    if (accountsProvider.isAppAccount(documentId)) {
      return accountsProvider.getAccountByKey(documentId);
    }
    // Else, let's see if it belongs to an experiment or the associated images.
    Set<AppAccount> accounts = accountsProvider.getAccounts();
    for (AppAccount appAccount : accounts) {
      List<ExperimentOverviewPojo> overviews =
          AppSingleton.getInstance(getContext())
              .getDataController(appAccount)
              .blockingGetExperimentOverviews(true);
      for (ExperimentOverviewPojo overview : overviews) {
        // If the documentId is an experiment overview or starts with the experiment overview id,
        // return the associated app account. The documentId would start with the experiment
        // overview id if the documentId was an image in the experiment's folder.
        if (overview.getExperimentId().equals(documentId)
            || documentId.startsWith(overview.getExperimentId())) {
          return appAccount;
        }
      }
    }
    // Couldn't find any app accounts with that documentId.
    return null;
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

  // From
  // http://www.programcreek.com/java-api-examples/index.php?source_dir=PSD-master/ANDROID%20PSD/aFileChooser/src/com/ianhanniballake/localstorage/LocalStorageProvider.java
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

  private void addAccountToRow(MatrixCursor.RowBuilder row, String accountName) {
    row.add(Document.COLUMN_DISPLAY_NAME, accountName);
    row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
    row.add(Document.COLUMN_LAST_MODIFIED, null);
    row.add(Document.COLUMN_SIZE, null);
  }

  private void addExperimentToRow(MatrixCursor.RowBuilder row, ExperimentOverviewPojo overview) {
    row.add(
        Document.COLUMN_DISPLAY_NAME,
        Experiment.getDisplayTitle(getContext(), overview.getTitle()));
    row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
    row.add(Document.COLUMN_LAST_MODIFIED, overview.getLastUsedTimeMs());
    row.add(Document.COLUMN_SIZE, null);
  }

  private void addAssetToRow(MatrixCursor.RowBuilder row, File file) {
    row.add(Document.COLUMN_DISPLAY_NAME, Experiment.getDisplayTitle(getContext(), file.getName()));
    row.add(Document.COLUMN_MIME_TYPE, getMimeType(file));
    row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
    row.add(Document.COLUMN_SIZE, file.length());
  }
}
