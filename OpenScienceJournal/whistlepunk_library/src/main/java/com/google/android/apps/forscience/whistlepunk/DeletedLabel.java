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
package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.support.design.widget.Snackbar;
import android.view.View;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelListHolder;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;

public class DeletedLabel {
    private static final String TAG = "DeletedLabel";
    private Label mLabel;
    private Consumer<Context> mAssetDeleter;

    // This is a little longer than the value of SnackbarManager#LONG_DURATION_MS.  We set it
    // custom so we can try to be sure when it has disappeared.
    private static final int UNDO_DELAY_MS = 3000;


    public DeletedLabel(Label label, Consumer<Context> assetDeleter) {
        mLabel = label;
        mAssetDeleter = assetDeleter;
    }

    public Label getLabel() {
        return mLabel;
    }

    public void deleteAndDisplayUndoBar(View view, final String experimentId,
            LabelListHolder labelHolder, Runnable uiChangeOnUndo) {
        Context context = view.getContext();
        final DataController dc = AppSingleton.getInstance(context).getDataController();
        Snackbar bar = AccessibilityUtils.makeSnackbar(view,
                context.getResources().getString(R.string.snackbar_note_deleted), UNDO_DELAY_MS);

        RxEvent undone = new RxEvent();

        // On undo, re-add the item to the database and the pinned note list.
        bar.setAction(R.string.snackbar_undo, new View.OnClickListener() {
            boolean mUndone = false;
            @Override
            public void onClick(View v) {
                if (mUndone) {
                    return;
                }
                undone.onHappened();
                mUndone = true;
                labelHolder.addLabel(mLabel);
                dc.updateExperiment(experimentId, new LoggingConsumer<Success>(TAG,
                        "re-add deleted label") {
                    @Override
                    public void success(Success value) {
                        uiChangeOnUndo.run();
                    }
                });
            }
        });
        bar.show();

        // Add just a bit of extra time to be sure
        long delayWithBuffer = (long) (bar.getDuration() * 1.1);

        Context appContext = view.getContext().getApplicationContext();
        Observable.timer(delayWithBuffer, TimeUnit.MILLISECONDS)
                  .takeUntil(undone.happens())
                  .observeOn(AndroidSchedulers.mainThread())
                  .subscribe(o -> mAssetDeleter.accept(appContext));
    }

}
