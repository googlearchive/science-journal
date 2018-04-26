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

import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.BehaviorSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * RxJava can have sometimes confusing documentation. We can put tests here to make sure that we get
 * what we expect.
 */
@RunWith(RobolectricTestRunner.class)
public class RxJavaCharacterizationTests {
  @Test
  public void firstElementAsBehaviorSubjectChanges() {
    BehaviorSubject<String> subject = BehaviorSubject.create();
    TestObserver<String> beforeTest = subject.firstElement().test();

    subject.onNext("A");
    TestObserver<String> firstTest = subject.firstElement().test();
    firstTest.assertValue("A").assertComplete();
    beforeTest.assertValue("A").assertComplete();

    subject.onNext("B");
    TestObserver<String> secondTest = subject.firstElement().test();
    secondTest.assertValue("B").assertComplete();
  }
}
