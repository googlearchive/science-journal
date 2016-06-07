package com.google.android.apps.forscience.whistlepunk;

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.PictureLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.TextLabel;

/**
 * For viewing existing notes.
 */
public class PreviewNoteDialog extends DialogFragment {
    public static final String TAG = "preview_note_dialog";
    public static final String KEY_SAVED_LABEL = "keySavedLabel";

    private Label mLabel;

    public static PreviewNoteDialog newInstance(Label label) {
        PreviewNoteDialog dialog = new PreviewNoteDialog();
        Bundle args = new Bundle();
        args.putParcelable(KEY_SAVED_LABEL, label);
        dialog.setArguments(args);
        return dialog;
    }

    public PreviewNoteDialog() {
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        mLabel = getArguments().getParcelable(KEY_SAVED_LABEL);

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        LinearLayout rootView = (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                R.layout.run_review_label_preview, null);
        alertDialog.setView(rootView);

        ImageView imageView = (ImageView) rootView.findViewById(R.id.picture_note_preview_image);
        final TextView previewText = (TextView) rootView.findViewById(R.id.preview_note_text);

        if (mLabel instanceof PictureLabel) {
            imageView.setVisibility(View.VISIBLE);
            previewText.setVisibility(View.GONE);
            Glide.with(getActivity())
                    .load(((PictureLabel) mLabel).getFilePath())
                    .into(imageView);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PictureUtils.launchExternalViewer(getActivity(),
                            ((PictureLabel) mLabel).getFilePath());
                }
            });
        } else if (mLabel instanceof TextLabel) {
            imageView.setVisibility(View.GONE);
            previewText.setVisibility(View.VISIBLE);
            previewText.setText(((TextLabel) mLabel).getText());
        }
        AlertDialog dialog = alertDialog.create();
        return dialog;
    }
}
