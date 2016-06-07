package com.google.android.apps.forscience.whistlepunk;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;

/**
 * Displays open source licenses in a dialog.
 */
public class LicenseFragment extends DialogFragment {

    private static final String TAG = "LicenseFragment";

    private WebView mWebView;

    public LicenseFragment() {}

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.licenses, null, false);
        mWebView = (WebView) view.findViewById(R.id.license_web_view);
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(view)
                .setTitle(R.string.settings_open_source_title)
                .setCancelable(true)
                .create();
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        new LoadStaticHtmlTask(new LoadStaticHtmlTask.StaticHtmlLoadListener() {
            @Override
            public void onDataLoaded(String data) {
                mWebView.loadData(data, "text/html", "UTF-8");
            }
        }, getResources(), R.raw.licenses).execute();
    }
}
