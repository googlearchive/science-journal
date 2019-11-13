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

package com.google.android.apps.forscience.whistlepunk.review;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import com.google.android.apps.forscience.whistlepunk.R;

/** DialogFragment for deleting a metadata item. */
public class DeleteMetadataItemDialog extends DialogFragment {
  public static final String TAG = "delete_item_dialog";

  private static final String ARG_TITLE_ID = "title_id";
  private static final String ARG_MESSAGE_ID = "message_id";
  private static final String ARG_EXTRAS = "extras";

  public static final String KEY_ITEM_ID = "item_id";
  public static final String KEY_REMOVE_COVER_IMAGE = "remove_cover_image";

  public interface DeleteDialogListener {

    /**
     * Called when the user has confirmed they would like to delete.
     *
     * @param extras Extras bundle passed in to {@link #newInstance(int, int, Bundle)}.
     */
    void requestDelete(Bundle extras);
  }

  public static DeleteMetadataItemDialog newInstance(int titleId, int messageId) {
    return newInstance(titleId, messageId, new Bundle());
  }

  public static DeleteMetadataItemDialog newInstance(int titleId, int messageId, String itemId) {
    Bundle extras = new Bundle();
    extras.putString(KEY_ITEM_ID, itemId);
    return newInstance(titleId, messageId, extras);
  }

  public static DeleteMetadataItemDialog newInstance(
      int titleId, int messageId, boolean removeCoverImage) {
    Bundle extras = new Bundle();
    extras.putBoolean(KEY_REMOVE_COVER_IMAGE, removeCoverImage);
    return newInstance(titleId, messageId, extras);
  }

  public static DeleteMetadataItemDialog newInstance(int titleId, int messageId, Bundle extras) {
    DeleteMetadataItemDialog dialog = new DeleteMetadataItemDialog();
    Bundle args = new Bundle();
    args.putInt(ARG_TITLE_ID, titleId);
    args.putInt(ARG_MESSAGE_ID, messageId);
    args.putBundle(ARG_EXTRAS, extras);
    dialog.setArguments(args);
    return dialog;
  }

  public DeleteMetadataItemDialog() {}

  public AlertDialog onCreateDialog(Bundle savedInstanceState) {
    AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    alertDialog.setTitle(getArguments().getInt(ARG_TITLE_ID));
    alertDialog.setMessage(getArguments().getInt(ARG_MESSAGE_ID));

    alertDialog.setPositiveButton(
        R.string.action_delete,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            ((DeleteDialogListener) getParentFragment())
                .requestDelete(getArguments().getBundle(ARG_EXTRAS));
          }
        });
    alertDialog.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.cancel();
          }
        });
    alertDialog.setCancelable(true);
    return alertDialog.create();
  }
}
