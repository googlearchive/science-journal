package com.google.android.apps.forscience.whistlepunk.review;

import android.app.DialogFragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RunReviewOverlay;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

/**
 * A dialogfragment for editing a note's timestamp.
 */
public class EditTimeDialog extends DialogFragment implements RunReviewOverlay.OnTimestampChangeListener {
    public static final String TAG = "edit_time_dialog";
    public static final String KEY_LABEL = "savedLabel";
    public static final String KEY_LABEL_TYPE = "savedLabelType";
    public static final String KEY_INITIAL_TIMESTAMP = "savedFirstTimestamp";
    public static final String KEY_RUN_START_TIMESTAMP = "savedRunStartTimestamp";
    public static final String KEY_SELECTED_VALUE = "savedLabelValue";

    public static final long NO_TIMESTAMP_SELECTED = -1;

    public interface EditTimeDialogListener {
        void onEditTimeDialogDismissedEdit(Label label, GoosciLabelValue.LabelValue selectedValue,
                long selectedTimestamp);
        void onEditTimeDialogDismissedAdd(GoosciLabelValue.LabelValue selectedValue, int labelType,
                long selectedTimestamp);
    }

    // The timestamp to initially show to the user.
    private long mInitialTimestamp;

    // The currently selected timestamp. This will be mInitialTimestamp after onCreateView,
    // but we initialize it to NO_TIMESTAMP_SELECTED in case onCreateView is not yet called.
    private long mCurrentTimestamp = NO_TIMESTAMP_SELECTED;

    // The first timestamp in this run, used for formatting the timestamp string.
    private long mRunStartTimestamp;

    private TextView mTimeView;
    private boolean timestampSelected = false;
    private boolean mDismissed = false;

    public static EditTimeDialog newInstance(Label label, GoosciLabelValue.LabelValue selectedValue,
                                             long initialTimestamp, long runStartTimestamp) {
        EditTimeDialog dialog = newInstanceHelper(selectedValue, initialTimestamp,
                runStartTimestamp);
        dialog.getArguments().putParcelable(KEY_LABEL, label);
        return dialog;
    }

    public static EditTimeDialog newInstance(GoosciLabelValue.LabelValue selectedValue,
            int labelType, long initialTimestamp, long runStartTimestamp) {
        EditTimeDialog dialog = newInstanceHelper(selectedValue, initialTimestamp,
                runStartTimestamp);
        dialog.getArguments().putInt(KEY_LABEL_TYPE, labelType);
        return dialog;
    }

    private static EditTimeDialog newInstanceHelper(GoosciLabelValue.LabelValue selectedValue,
            long initialTimestamp, long runStartTimestamp) {
        EditTimeDialog dialog = new EditTimeDialog();
        Bundle args = new Bundle();
        args.putLong(KEY_INITIAL_TIMESTAMP, initialTimestamp);
        args.putLong(KEY_RUN_START_TIMESTAMP, runStartTimestamp);
        args.putByteArray(KEY_SELECTED_VALUE, ProtoUtils.makeBlob(selectedValue));
        dialog.setArguments(args);
        return dialog;
    }

    public EditTimeDialog() {
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mInitialTimestamp = getArguments().getLong(KEY_INITIAL_TIMESTAMP);
        mRunStartTimestamp = getArguments().getLong(KEY_RUN_START_TIMESTAMP);
        View rootView = inflater.inflate(R.layout.edit_time_dialog, container, false);
        mTimeView = (TextView) rootView.findViewById(R.id.edit_note_time);
        timestampSelected = false;

        onTimestampChanged(mInitialTimestamp);
        ImageButton button = (ImageButton) rootView.findViewById(R.id.submit_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timestampSelected = true;
                dismiss();
            }
        });
        AccessibilityUtils.setTouchDelegateToMinAccessibleSize(button);
        return rootView;
    }

    @Override
    public void dismiss() {
        // Avoid double-callbacks by checking if this was already dismissed.
        if (!mDismissed) {
            mDismissed = true;
            GoosciLabelValue.LabelValue selectedValue = new GoosciLabelValue.LabelValue();
            try {
                selectedValue = GoosciLabelValue.LabelValue.parseFrom(
                        getArguments().getByteArray(KEY_SELECTED_VALUE));
            } catch (InvalidProtocolBufferNanoException ex) {
                Log.wtf(TAG, "Unable to parse label value");
            }
            if (getArguments().containsKey(KEY_LABEL)) {
                // Then this came from editing an existing label.
                ((EditTimeDialogListener) getParentFragment()).onEditTimeDialogDismissedEdit(
                        (Label) getArguments().getParcelable(KEY_LABEL), selectedValue,
                        timestampSelected ? mCurrentTimestamp : mInitialTimestamp);
            } else {
                // Then this came from adding a new label.
                ((EditTimeDialogListener) getParentFragment()).onEditTimeDialogDismissedAdd(
                        selectedValue, getArguments().getInt(KEY_LABEL_TYPE),
                        timestampSelected ? mCurrentTimestamp : mInitialTimestamp);
            }
        }
        super.dismiss();
    }

    public long getCurrentTimestamp() {
        // If we haven't created the view yet, get the original timestamp from the args.
        if (mCurrentTimestamp == NO_TIMESTAMP_SELECTED) {
            return getArguments().getLong(KEY_INITIAL_TIMESTAMP);
        }
        return mCurrentTimestamp;
    }

    public Label getLabel() {
        return getArguments().getParcelable(KEY_LABEL);
    }

    @Override
    public void onTimestampChanged(long timestamp) {
        mCurrentTimestamp = timestamp;
        if (mTimeView != null) {
            mTimeView.setText(
                    PinnedNoteAdapter.getNoteTimeText(mCurrentTimestamp, mRunStartTimestamp));
        }
    }

}
