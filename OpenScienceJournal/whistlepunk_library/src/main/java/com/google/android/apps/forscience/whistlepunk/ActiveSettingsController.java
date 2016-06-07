package com.google.android.apps.forscience.whistlepunk;


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.support.design.widget.Snackbar;
import android.view.View;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ActiveBundle;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;


/**
 * Controller for settings dialogs that make live changes to ActiveBundles.
 *
 * TODO: all SettingsControllers switch to use this API
 */
public class ActiveSettingsController {
    private final Context mContext;

    // TODO: this doesn't really need to be a peristent object.
    public ActiveSettingsController(Context context) {
        mContext = context;
    }

    public interface OptionsCallbacks {
        /**
         * @param activeBundle contains both the initial options, and active methods for changing
         *                     them in response to user actions
         * @return a View with controls to change the given options.
         */
        View buildOptionsView(ActiveBundle activeBundle);

        /**
         * If possible, perform a light-weight refresh of the UI to adjust to the new options.
         * This may be called many times while the user changes options, so this should
         * concentrate on changes that can be done in a single UI-thread frame, without IPC,
         * database access, or communication.
         *
         * @param newOptions
         */
        void previewOptions(ReadableSensorOptions newOptions);

        /**
         * User has committed to the new options.  Begin any required changes, including
         * expensive operations (IPC, DB, BLE).
         *
         * @param newOptions
         */
        void commitOptions(ReadableSensorOptions newOptions);
    }

    public void launchOptionsDialog(final OptionsCallbacks callbacks, String name,
            String optionsTitle, WriteableSensorOptions options) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        final Resources resources = mContext.getResources();
        if (callbacks == null) {
            builder.setTitle(resources.getString(R.string.no_options_title, name));
            builder.setMessage(resources.getString(R.string.no_options_message, name));
            builder.setCancelable(true);
            builder.create().show();
            return;
        }

        final OptionsConsumer optionsConsumer = new OptionsConsumer(callbacks);
        final View settingsView =
                callbacks.buildOptionsView(new ActiveBundle(options, optionsConsumer, new
                        ActiveBundle.OnErrorListener() {
                            @Override
                            public void onError(String error, View relevantView) {
                                AccessibilityUtils.makeSnackbar(relevantView, error,
                                        Snackbar.LENGTH_SHORT).show();
                            }
                        }));
        builder.setTitle(optionsTitle);
        builder.setMessage(resources.getString(R.string.options_message, name));
        builder.setView(settingsView);

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                d.cancel();
            }
        });

        builder.setCancelable(true);
        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface di) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final ReadableSensorOptions options = optionsConsumer.getOptions();
                                if (options != null) {
                                    callbacks.commitOptions(options);
                                }
                                di.dismiss();
                            }
                        });
            }
        });

        dialog.show();
    }

    private static class OptionsConsumer extends Consumer<ReadableSensorOptions> {
        private final OptionsCallbacks mCallbacks;
        private ReadableSensorOptions mSettings;

        public OptionsConsumer(OptionsCallbacks callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public void take(ReadableSensorOptions bundle) {
            mCallbacks.previewOptions(bundle);
            mSettings = bundle;
        }

        public ReadableSensorOptions getOptions() {
            return mSettings;
        }
    }
}
