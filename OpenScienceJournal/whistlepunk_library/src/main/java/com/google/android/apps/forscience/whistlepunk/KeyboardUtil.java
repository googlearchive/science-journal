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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import io.reactivex.Single;

public class KeyboardUtil {
  /** Returns Observable that will receive true if the keyboard is closed */
  public static Single<Boolean> closeKeyboard(Activity activity) {
    View view = activity.getCurrentFocus();
    if (view != null) {
      InputMethodManager imm =
          (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

      return Single.create(
          s -> {
            imm.hideSoftInputFromWindow(
                view.getWindowToken(),
                0,
                new ResultReceiver(null) {
                  @Override
                  protected void onReceiveResult(int resultCode, Bundle resultData) {
                    s.onSuccess(resultCode == InputMethodManager.RESULT_HIDDEN);
                    super.onReceiveResult(resultCode, resultData);
                  }
                });
          });
    } else {
      return Single.just(false);
    }
  }
}
