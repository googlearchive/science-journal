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

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

/**
 * Little class to allow listening to events, usually to trigger unsubscriptions to Rx streams.
 *
 * <p>For example, if you want to observe a stream until your activity pauses, use:
 *
 * <pre>
 * RxEvent mPause = new RxEvent();
 * // ...
 * getStream().takeUntil(mPause.happens()).subscribe(item -> doSomething(item));
 * // ...
 * public void onPause() {
 *     mPause.onHappened();
 * }
 * </pre>
 */
public class RxEvent {
  private PublishSubject<Object> happenings = PublishSubject.create();

  public Observable<Object> happens() {
    return happenings;
  }

  public void onHappened() {
    happenings.onNext(true);
  }

  public void onDoneHappening() {
    happenings.onComplete();
  }

  public Completable happensNext() {
    return happens().firstOrError().toCompletable();
  }
}
