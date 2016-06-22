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
