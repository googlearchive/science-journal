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

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import androidx.annotation.Nullable;
import android.util.Log;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.wireapi.IRecorderController;

// TODO(b/79583008): Remove this class.

/**
 * Exports the current main-thread RecorderController as a service that other applications can bind
 * to. Currently only supports startObserving and stopObserving for a single sensor
 */
public class PublicRecorderService extends Service
    implements FailureListener, ProxyRecorderController.BindingPolicy {
  private static final String TAG = "ExportedService";

  private IRecorderController.Stub controller;

  @Nullable
  @Override
  public IRecorderController.Stub onBind(Intent intent) {
    return getController();
  }

  private IRecorderController.Stub getController() {
    if (controller == null) {
      controller = createController();
    }
    return controller;
  }

  // TODO: unit tests for this behavior
  private IRecorderController.Stub createController() {
    AppAccount appAccount = NonSignedInAccount.getInstance(this);
    final RecorderController rc = AppSingleton.getInstance(this).getRecorderController(appAccount);
    final SensorRegistry registry = AppSingleton.getInstance(this).getSensorRegistry();
    return new ProxyRecorderController(rc, this, this, registry);
  }

  /**
   * Check that the connecting user is one we'd like to talk to.
   *
   * <p>Note that this method only returns valid results when called from within methods defined on
   * the Binder class, not methods like onBind on the service itself.
   */
  public void checkBinderAllowed() {
    int uid = Binder.getCallingUid();
    String bindingPackage = getPackageManager().getNameForUid(uid);
    String thisPackage = getPackageName();
    int check = getPackageManager().checkSignatures(thisPackage, bindingPackage);
    // See docs: 0 and up are various "OK" states, negative values are various errnos
    if (check < 0) {
      throw new IllegalStateException(
          "Cannot bind to service from package, wrong signatures: "
              + bindingPackage
              + ", returned: "
              + check);
    }
  }

  private void complain(Exception e) {
    if (Log.isLoggable(TAG, Log.ERROR)) {
      Log.e(TAG, "remote exception", e);
    }
  }

  @Override
  public void fail(Exception e) {
    complain(e);
  }
}
