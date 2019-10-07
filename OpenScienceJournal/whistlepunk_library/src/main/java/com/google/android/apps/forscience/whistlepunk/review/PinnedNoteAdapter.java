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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.Flags;
import com.google.android.apps.forscience.whistlepunk.NoteViewHolder;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.jakewharton.rxbinding2.widget.RxTextView;
import java.io.File;

/** Adapter for a recycler view of pinned notes. */
public class PinnedNoteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final String TAG = "PinnedNoteAdapter";
  private static final int TYPE_TEXT_NOTE = 0;
  private static final int TYPE_PICTURE_NOTE = 1;
  private static final int TYPE_TRIGGER_NOTE = 2;
  private static final int TYPE_SNAPSHOT_NOTE = 3;
  private static final int TYPE_UNKNOWN = -1;
  private static final int TYPE_ADD_LABEL = 4;
  private static final int TYPE_CAPTION = 5;
  private static final int FIRST_NOTE_INDEX = 1;

  private PopupMenu popupMenu = null;

  /** An interface for listening to when a pinned note should be edited or deleted. */
  public interface ListItemEditListener {
    // When the user wants to edit a particular label's timestamp.
    void onLabelEditTime(Label item);

    // When a user deletes a particular label.
    void onLabelDelete(Label item);

    // When the user makes a change to the caption -- needs to be saved.
    void onCaptionEdit(String updatedCaption);
  }

  /** An interface for listening to when a pinned note is clicked. */
  public interface ListItemClickListener {
    // Anywhere on the label was clicked.
    void onLabelClicked(Label item);

    // The add label button was clicked.
    void onAddLabelButtonClicked();

    // The label's timestamp section was clicked.
    void onLabelTimestampClicked(Label item);
  }

  public class BlankViewHolder extends RecyclerView.ViewHolder {
    public BlankViewHolder(View v) {
      super(v);
    }
  }

  private final AppAccount appAccount;
  private final Trial trial;
  private long startTimestamp;
  private long endTimestamp;
  private final String experimentId;
  private final boolean claimExperimentsMode;
  private ListItemEditListener editListener;
  private ListItemClickListener clickListener;
  private final Fragment fragment;

  public PinnedNoteAdapter(
      Fragment fragment,
      AppAccount appAccount,
      Trial trial,
      long startTimestamp,
      long endTimestamp,
      String experimentId,
      boolean claimExperimentsMode) {
    this.fragment = fragment;
    this.appAccount = appAccount;
    this.trial = trial;
    this.startTimestamp = startTimestamp;
    this.endTimestamp = endTimestamp;
    this.experimentId = experimentId;
    this.claimExperimentsMode = claimExperimentsMode;
  }

  public void updateRunTimestamps(long startTimestamp, long endTimestamp) {
    this.startTimestamp = startTimestamp;
    this.endTimestamp = endTimestamp;
    notifyDataSetChanged();
  }

  public void setListItemModifyListener(ListItemEditListener editListener) {
    this.editListener = editListener;
  }

  public void setListItemClickListener(ListItemClickListener clickListener) {
    this.clickListener = clickListener;
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    switch (viewType) {
      case TYPE_TEXT_NOTE:
      case TYPE_PICTURE_NOTE:
      case TYPE_TRIGGER_NOTE:
      case TYPE_SNAPSHOT_NOTE:
        View v =
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.exp_card_pinned_note, parent, false);
        return new NoteViewHolder(v);
      case TYPE_ADD_LABEL:
        v =
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.add_label_button_item, parent, false);
        v.findViewById(R.id.add_note_to_timeline_button)
            .setContentDescription(
                parent.getContext().getResources().getString(R.string.add_note_button_text));
        return new BlankViewHolder(v);
      case TYPE_CAPTION:
        v = LayoutInflater.from(parent.getContext()).inflate(R.layout.caption_item, parent, false);
        return new BlankViewHolder(v);
      default:
        return null;
    }
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
    int viewType = getItemViewType(position);
    if (viewType == TYPE_ADD_LABEL) {
      if (!claimExperimentsMode && !Flags.showActionBar()) {
        holder.itemView.setOnClickListener(view -> clickListener.onAddLabelButtonClicked());
      } else {
        holder.itemView.setVisibility(View.GONE);
      }
      return;
    }

    if (viewType == TYPE_CAPTION) {
      if (!claimExperimentsMode) {
        EditText editText = (EditText) holder.itemView.findViewById(R.id.caption);
        editText.setText(trial.getCaptionText());
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setRawInputType(InputType.TYPE_CLASS_TEXT);
        RxTextView.afterTextChangeEvents(editText)
            .subscribe(event -> editListener.onCaptionEdit(editText.getText().toString()));
      }
      return;
    }

    final NoteViewHolder noteHolder = (NoteViewHolder) holder;
    final Label label = trial.getLabels().get(position - 1);
    noteHolder.setNote(label, appAccount, experimentId, claimExperimentsMode);

    // Do work specific to RunReview.
    noteHolder.relativeTimeView.setVisibility(View.GONE);
    noteHolder.durationText.setText(getNoteTimeText(label, startTimestamp));
    noteHolder.durationText.setContentDescription(
        getNoteTimeContentDescription(
            label.getTimeStamp(), startTimestamp, noteHolder.durationText.getContext()));

    final Context context = noteHolder.menuButton.getContext();
    final Intent shareIntent = getPhotoShareIntent(label, context);
    noteHolder.menuButton.setOnClickListener(
        v -> {
          popupMenu =
              new PopupMenu(
                  context,
                  noteHolder.menuButton,
                  Gravity.NO_GRAVITY,
                  R.attr.actionOverflowMenuStyle,
                  0);
          popupMenu.getMenuInflater().inflate(R.menu.menu_note, popupMenu.getMenu());
          if (claimExperimentsMode || !label.canEditTimestamp()) {
            popupMenu.getMenu().findItem(R.id.btn_edit_note_time).setVisible(false);
          }
          popupMenu
              .getMenu()
              .findItem(R.id.btn_share_photo)
              .setVisible(label.getType() == ValueType.PICTURE && shareIntent != null);
          popupMenu
              .getMenu()
              .findItem(R.id.btn_download_photo)
              .setVisible(label.getType() == ValueType.PICTURE);
          popupMenu.setOnDismissListener(menu -> popupMenu = null);
          popupMenu.setOnMenuItemClickListener(
              item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.btn_edit_note_time) {
                  editListener.onLabelEditTime(label);
                  return true;
                } else if (itemId == R.id.btn_delete_note) {
                  editListener.onLabelDelete(label);
                  return true;
                } else if (itemId == R.id.btn_share_photo) {
                  context.startActivity(
                      Intent.createChooser(
                          shareIntent,
                          context.getResources().getString(R.string.export_photo_chooser_title)));

                  return true;
                } else if (itemId == R.id.btn_download_photo) {
                  requestDownload(fragment.getActivity(), context, label);
                  return true;
                }
                return false;
              });
          popupMenu.show();
        });

    if (!claimExperimentsMode) {
      // Notes out of range are not clickable.
      if (startTimestamp <= label.getTimeStamp() && label.getTimeStamp() < endTimestamp) {
        noteHolder.durationText.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                clickListener.onLabelTimestampClicked(label);
              }
            });
        noteHolder.itemView.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                clickListener.onLabelClicked(label);
              }
            });
      }
    }
  }

  private void requestDownload(Activity activity, Context context, Label label) {
    ExportService.requestDownloadPermissions(
        () -> {
          String sourcePath = label.getPictureLabelValue().getFilePath();
          File sourceFile =
              new File(
                  PictureUtils.getExperimentImagePath(
                      context, appAccount, experimentId, sourcePath));
          Uri sourceUri = Uri.fromFile(sourceFile);
          ExportService.saveToDownloads(context, sourceUri);
        },
        activity,
        android.R.id.content,
        TrackerConstants.CATEGORY_NOTES,
        TrackerConstants.LABEL_RUN_REVIEW);
  }

  private Intent getPhotoShareIntent(Label label, Context context) {
    if (label.getType() != ValueType.PICTURE) {
      return null;
    } else {
      return FileMetadataUtil.getInstance()
          .createPhotoShareIntent(
              context,
              appAccount,
              experimentId,
              label.getPictureLabelValue().getFilePath(),
              label.getCaptionText());
    }
  }

  @Override
  public int getItemCount() {
    // The caption is temporarily removed per b/65063919 so the size is labels + 1.
    return trial.getLabelCount() + 1;

    /*
    // We always show caption and add note button, making the size the labels + 2.
    return trial.getLabelCount() + 2;
    */
  }

  @Override
  public int getItemViewType(int position) {
    if (position == 0) {
      // First item always add label
      return TYPE_ADD_LABEL;
    }
    if (position == trial.getLabelCount() + 1) {
      // Last item always the caption
      return TYPE_CAPTION;
    }
    ValueType labelType = trial.getLabels().get(position - 1).getType();
    if (labelType == ValueType.TEXT) {
      return TYPE_TEXT_NOTE;
    }
    if (labelType == ValueType.PICTURE) {
      return TYPE_PICTURE_NOTE;
    }
    if (labelType == ValueType.SENSOR_TRIGGER) {
      return TYPE_TRIGGER_NOTE;
    }
    if (labelType == ValueType.SNAPSHOT) {
      return TYPE_SNAPSHOT_NOTE;
    }
    return TYPE_UNKNOWN;
  }

  public void onLabelUpdated(Label label) {
    // If the timestamp has changed, updating only the changed position is not enough because
    // the labels have been rearranged.
    // TODO: Is there a more efficient way to update only the labels which have moved?
    notifyDataSetChanged();
  }

  // TODO (b/134097634): Update this logic once we can fully remove the "add note" button
  public int onLabelAdded(Label label) {
    if (trial.getLabelCount() == 1) {
      notifyItemChanged(FIRST_NOTE_INDEX); // First label at index 1 (0 is the "add note" button)
    } else {
      int position = findLabelIndexById(label.getLabelId());
      if (position != -1) {
        notifyItemInserted(position);
        return position;
      }
    }
    return FIRST_NOTE_INDEX;
  }

  private int findLabelIndexById(String id) {
    for (int i = 0; i < trial.getLabelCount(); i++) {
      if (TextUtils.equals(trial.getLabels().get(i).getLabelId(), id)) {
        // The 0th index item is "add note to timeline" button
        return i + 1;
      }
    }
    return -1;
  }

  public static String getNoteTimeText(Label label, long startTimestamp) {
    return getNoteTimeText(label.getTimeStamp(), startTimestamp);
  }

  public static String getNoteTimeText(long labelTimestamp, long startTimestamp) {
    long elapsedTimeSeconds =
        Math.round((labelTimestamp - startTimestamp) / RunReviewFragment.MILLIS_IN_A_SECOND);
    if (elapsedTimeSeconds < 0) {
      // String resource: Localization for negative values?
      return "-" + DateUtils.formatElapsedTime(-1 * elapsedTimeSeconds);
    }
    return DateUtils.formatElapsedTime(elapsedTimeSeconds);
  }

  public static String getNoteTimeContentDescription(
      long currentTimestamp, long runStartTimestamp, Context context) {
    return ElapsedTimeFormatter.getInstance(context)
        .formatForAccessibility(
            (currentTimestamp - runStartTimestamp) / ExternalAxisController.MS_IN_SEC);
  }

  public void onDestroy() {
    clickListener = null;
    editListener = null;
    if (popupMenu != null) {
      popupMenu.dismiss();
    }
  }
}
