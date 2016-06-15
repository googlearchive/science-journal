package com.google.android.apps.forscience.whistlepunk;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import java.util.Arrays;

/**
 * DialogFragment to display audio settings to the user.
 */
public class AudioSettingsDialog extends DialogFragment {

    public interface AudioSettingsDialogListener {
        void onAudioSettingsPreview(String previewSonificationType, String cardSensorId);
        void onAudioSettingsApplied(String newSonificationType, String cardSensorId);
        void onAudioSettingsCanceled(String originalSonificationType, String cardSensorId);
    }

    public static final String TAG = "AudioSettingsDialog";
    private static final String KEY_SONIFICATION_TYPE = "sonification_type";
    private static final String KEY_SENSOR_ID = "sensor_id";

    private String mSonificationType;

    public static AudioSettingsDialog newInstance(String sonificationType, String sensorId) {
        AudioSettingsDialog dialog = new AudioSettingsDialog();
        Bundle args = new Bundle();
        args.putString(KEY_SONIFICATION_TYPE, sonificationType);
        args.putString(KEY_SENSOR_ID, sensorId);
        dialog.setArguments(args);
        return dialog;
    }

    public AudioSettingsDialog() {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SONIFICATION_TYPE, mSonificationType);
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        final String originalSonificationType = getArguments().getString(KEY_SONIFICATION_TYPE, "");
        if (savedInstanceState != null) {
            // Use the most recent version if possible.
            mSonificationType = savedInstanceState.getString(
                    KEY_SONIFICATION_TYPE, originalSonificationType);
        } else {
            mSonificationType = originalSonificationType;
        }
        final String sensorId = getArguments().getString(KEY_SENSOR_ID, "");

        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());

        alertDialog.setTitle(getResources().getString(R.string.card_options_audio_settings));

        final String[] sonificationTypesHumanReadable;
        final String[] sonificationTypesAliases = getActivity().getResources().getStringArray(
                R.array.sonification_type_aliases);

        // Add additional audio options only if the testing options setting is enabled.
        if (DevOptionsFragment.getEnableAdditionalSonificationTypes(getActivity())) {
            String[] humanReadableDev = getActivity().getResources()
                    .getStringArray(R.array.sonification_types_dev);
            String[] humanReadableProd = getActivity().getResources().getStringArray(
                    R.array.sonification_types_prod);
            sonificationTypesHumanReadable = Arrays.copyOf(humanReadableProd,
                    humanReadableProd.length + humanReadableDev.length);
            System.arraycopy(humanReadableDev, 0, sonificationTypesHumanReadable,
                    humanReadableProd.length, humanReadableDev.length);
        } else {
            sonificationTypesHumanReadable = getActivity().getResources().getStringArray(
                    R.array.sonification_types_prod);
        }

        int selectedIndex = findSelection(sonificationTypesAliases, mSonificationType);
        alertDialog.setSingleChoiceItems(sonificationTypesHumanReadable, selectedIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSonificationType = sonificationTypesAliases[which];
                        if (getParentFragment() != null) {
                            ((AudioSettingsDialogListener) getParentFragment())
                                    .onAudioSettingsPreview(mSonificationType, sensorId);
                        }
                    }
                });

        alertDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (getParentFragment() != null) {
                    ((AudioSettingsDialogListener) getParentFragment())
                            .onAudioSettingsApplied(mSonificationType, sensorId);
                }
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        if (getParentFragment() != null) {
                            ((AudioSettingsDialogListener) getParentFragment())
                                    .onAudioSettingsCanceled(originalSonificationType, sensorId);
                        }
                    }
                });
        alertDialog.setCancelable(true);
        AlertDialog dialog = alertDialog.create();
        return dialog;
    }

    private int findSelection(String[] types, String sonificationType) {
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(sonificationType)) {
                return i;
            }
        }
        return 0;
    }
}
