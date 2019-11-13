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

import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AddNoteDialogTest {
  @Test
  public void connectExperimentIdDirect() {
    AddNoteDialog.whenExperimentId("id", null).test().assertValue("id");
  }

  @Test
  public void connectExperimentIdIndirect() {
    AddNoteDialog.whenExperimentId(
            null,
            new AddNoteDialog.AddNoteDialogListener() {
              @Override
              public Single<String> whenExperimentId() {
                return Single.just("b");
              }
            })
        .test()
        .assertValue("b");
  }
}
