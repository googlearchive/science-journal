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

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.PopupMenu;

import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.NoteViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.jakewharton.rxbinding2.widget.RxTextView;

/**
 * Adapter for a recycler view of pinned notes.
 */
public class PinnedNoteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "PinnedNoteAdapter";
    private static final int TYPE_TEXT_NOTE = 0;
    private static final int TYPE_PICTURE_NOTE = 1;
    private static final int TYPE_TRIGGER_NOTE = 2;
    private static final int TYPE_SNAPSHOT_NOTE = 3;
    private static final int TYPE_UNKNOWN = -1;
    private static final int TYPE_ADD_LABEL = 4;
    private static final int TYPE_CAPTION = 5;

    /**
     * An interface for listening to when a pinned note should be edited or deleted.
     */
    public interface ListItemEditListener {
        // When the user wants to edit a particular label's timestamp.
        void onLabelEditTime(Label item);

        // When a user deletes a particular label.
        void onLabelDelete(Label item);

        // When the user makes a change to the caption -- needs to be saved.
        void onCaptionEdit(String updatedCaption);
    }

    /**
     * An interface for listening to when a pinned note is clicked.
     */
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

    private Trial mTrial;
    private long mStartTimestamp;
    private long mEndTimestamp;
    private String mExperimentId;
    private ListItemEditListener mEditListener;
    private ListItemClickListener mClickListener;

    public PinnedNoteAdapter(Trial trial, long startTimestamp, long endTimestamp,
            String experimentId) {
        mTrial = trial;
        mStartTimestamp = startTimestamp;
        mEndTimestamp = endTimestamp;
        mExperimentId = experimentId;
    }

    public void updateRunTimestamps(long startTimestamp, long endTimestamp) {
        mStartTimestamp = startTimestamp;
        mEndTimestamp = endTimestamp;
        notifyDataSetChanged();
    }

    public void setListItemModifyListener(ListItemEditListener editListener) {
        mEditListener = editListener;
    }

    public void setListItemClickListener(ListItemClickListener clickListener) {
        mClickListener = clickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_TEXT_NOTE:
            case TYPE_PICTURE_NOTE:
            case TYPE_TRIGGER_NOTE:
            case TYPE_SNAPSHOT_NOTE:
                View v = LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.exp_card_pinned_note, parent, false);
                return new NoteViewHolder(v);
            case TYPE_ADD_LABEL:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.add_label_button_item,
                        parent, false);
                return new BlankViewHolder(v);
            case TYPE_CAPTION:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.caption_item,
                        parent, false);
                return new BlankViewHolder(v);
            default:
                return null;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_ADD_LABEL) {
            holder.itemView.setOnClickListener(view -> mClickListener.onAddLabelButtonClicked());
            return;
        }

        if (viewType == TYPE_CAPTION) {
            EditText editText = (EditText) holder.itemView.findViewById(R.id.caption);
            editText.setText(mTrial.getCaptionText());
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setRawInputType(InputType.TYPE_CLASS_TEXT);
            RxTextView.afterTextChangeEvents(editText)
                    .subscribe(event -> mEditListener.onCaptionEdit(editText.getText().toString()));
            return;
        }

        final NoteViewHolder noteHolder = (NoteViewHolder) holder;
        final Label label = mTrial.getLabels().get(position - 1);
        noteHolder.setNote(label, mExperimentId);

        // Do work specific to RunReview.
        noteHolder.relativeTimeView.setVisibility(View.GONE);
        noteHolder.durationText.setText(getNoteTimeText(label, mStartTimestamp));
        noteHolder.durationText.setContentDescription(getNoteTimeContentDescription(
                label.getTimeStamp(), mStartTimestamp, noteHolder.durationText.getContext()));


        if (mEditListener != null) {
            noteHolder.menuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Context context = noteHolder.menuButton.getContext();
                    PopupMenu popup = new PopupMenu(context, noteHolder.menuButton);
                    popup.getMenuInflater().inflate(R.menu.menu_note, popup.getMenu());
                    if (!label.canEditTimestamp()) {
                        popup.getMenu().findItem(R.id.btn_edit_note_time).setVisible(false);
                    }
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            int itemId = item.getItemId();
                            if (itemId == R.id.btn_edit_note_time) {
                                mEditListener.onLabelEditTime(label);
                                return true;
                            } else if (itemId == R.id.btn_delete_note) {
                                mEditListener.onLabelDelete(label);
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.show();
                }
            });
        }
        // Notes out of range are not clickable.
        if (mStartTimestamp <= label.getTimeStamp() && label.getTimeStamp() < mEndTimestamp) {
            noteHolder.durationText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mClickListener.onLabelTimestampClicked(label);
                }
            });
            noteHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mClickListener.onLabelClicked(label);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        // The caption is temporarily removed per b/65063919 so the size is labels + 1.
        return mTrial.getLabelCount() + 1;

        /*
        // We always show caption and add note button, making the size the labels + 2.
        return mTrial.getLabelCount() + 2;
        */
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            // First item always add label
            return TYPE_ADD_LABEL;
        }
        if (position == mTrial.getLabelCount() + 1) {
            // Last item always the caption
            return TYPE_CAPTION;
        }
        int labelType = mTrial.getLabels().get(position - 1).getType();
        if (labelType == GoosciLabel.Label.TEXT) {
            return TYPE_TEXT_NOTE;
        }
        if (labelType == GoosciLabel.Label.PICTURE) {
            return TYPE_PICTURE_NOTE;
        }
        if (labelType == GoosciLabel.Label.SENSOR_TRIGGER) {
            return TYPE_TRIGGER_NOTE;
        }
        if (labelType == GoosciLabel.Label.SNAPSHOT) {
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

    public void onLabelAdded(Label label) {
        if (mTrial.getLabelCount() == 1) {
            notifyItemChanged(1); // First label at index 1 (0 is the "add note" button)
        } else {
            int position = findLabelIndexById(label.getLabelId());
            if (position != -1) {
                notifyItemInserted(position);
            }
        }
    }

    private int findLabelIndexById(String id) {
        for (int i = 0; i < mTrial.getLabelCount(); i++) {
            if (TextUtils.equals(mTrial.getLabels().get(i).getLabelId(), id)) {
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
        long elapsedTimeSeconds = Math.round(
                (labelTimestamp - startTimestamp) / RunReviewFragment.MILLIS_IN_A_SECOND);
        if (elapsedTimeSeconds < 0) {
            // TOOD: String resource: Localization for negative values?
            return "-" + DateUtils.formatElapsedTime(-1 * elapsedTimeSeconds);
        }
        return DateUtils.formatElapsedTime(elapsedTimeSeconds);
    }

    public static String getNoteTimeContentDescription(long currentTimestamp,
            long runStartTimestamp, Context context) {
        return ElapsedTimeFormatter.getInstance(context).formatForAccessibility(
                (currentTimestamp - runStartTimestamp) / ExternalAxisController.MS_IN_SEC);
    }

    public void onDestroy() {
        mClickListener = null;
        mEditListener = null;
    }
}
