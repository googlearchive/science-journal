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
import android.view.View;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelListHolder;
import com.google.android.material.snackbar.Snackbar;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import java.util.concurrent.TimeUnit;

public class DeletedLabel {
  private static final String TAG = "DeletedLabel";
  private Label label;
  private Consumer<Context> assetDeleter;

  // This is a little longer than the value of SnackbarManager#LONG_DURATION_MS.  We set it
  // custom so we can try to be sure when it has disappeared.
  private static final int UNDO_DELAY_MS = 3000;

  public DeletedLabel(Label label, Consumer<Context> assetDeleter) {
    this.label = label;
    this.assetDeleter = assetDeleter;
  }

  public Label getLabel() {
    return label;
  }

  public void deleteAndDisplayUndoBar(
      View view,
      AppAccount appAccount,
      final Experiment experiment,
      LabelListHolder labelHolder,
      Runnable uiChangeOnUndo) {
    Context context = view.getContext();
    final DataController dc = AppSingleton.getInstance(context).getDataController(appAccount);
    Snackbar bar =
        AccessibilityUtils.makeSnackbar(
            view, context.getResources().getString(R.string.snackbar_note_deleted), UNDO_DELAY_MS);

    RxEvent undoneEvent = new RxEvent();

    // On undo, re-add the item to the database and the pinned note list.
    bar.setAction(
        R.string.snackbar_undo,
        new View.OnClickListener() {
          boolean undone = false;

          @Override
          public void onClick(View v) {
            if (this.undone) {
              return;
            }
            undoneEvent.onHappened();
            this.undone = true;
            labelHolder.addLabel(experiment, label);

            dc.updateExperiment(
                experiment.getExperimentId(),
                new LoggingConsumer<Success>(TAG, "re-add deleted label") {
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
        .takeUntil(undoneEvent.happens())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(o -> assetDeleter.accept(appContext));
  }
}
