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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import android.util.Log;
import com.google.android.apps.forscience.javalib.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds services that implement the scalarinput API */
public class ScalarSensorServiceFinder extends Consumer<AppDiscoveryCallbacks> {
  public static final String INTENT_ACTION =
      "com.google.android.apps.forscience.whistlepunk.SCALAR_SENSOR";
  public static final String METADATA_KEY_CLASS_NAME_OVERRIDE = "api_service_logical_name";
  private static final String TAG = "ScalarFinder";

  private final Context context;
  private final Map<String, ServiceConnection> connections = new HashMap<>();

  public ScalarSensorServiceFinder(Context context) {
    this.context = context;
  }

  @Override
  public void take(AppDiscoveryCallbacks callbacks) {
    List<ResolveInfo> resolveInfos = getResolveInfos();
    if (resolveInfos == null) {
      // b/32122408
      return;
    }
    for (ResolveInfo info : resolveInfos) {
      ServiceInfo serviceInfo = info.serviceInfo;
      String packageName = serviceInfo.packageName;
      ComponentName name = new ComponentName(packageName, serviceInfo.name);
      Intent intent = new Intent();
      intent.setComponent(name);
      if (versionCheck(packageName)) {
        final ServiceConnection conn =
            makeServiceConnection(connections, name, callbacks, serviceInfo.metaData);
        connections.put(packageName, conn);
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE);
      }
    }
    // TODO: need to figure out when to call onDiscovery done (after every service we know
    // about has connected or timed out).
  }

  private boolean versionCheck(String packageName) {
    try {
      int myVersion =
          Versions.getScalarApiVersion(context.getPackageName(), context.getResources());
      int packageVersion =
          Versions.getScalarApiVersion(
              packageName, context.getPackageManager().getResourcesForApplication(packageName));
      return versionCheck(myVersion, packageVersion);
    } catch (PackageManager.NameNotFoundException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Can't resolve package " + packageName, e);
      }
      return false;
    }
  }

  private boolean versionCheck(int myVersion, int packageVersion) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "App scalar API version: " + myVersion + ", package version: " + packageVersion);
    }
    // TODO: get more complicated when we add another version in the future.
    boolean isOK =
        (myVersion == Versions.FIRST_RELEASE_SCALAR_API_VERSION
            && packageVersion == Versions.FIRST_RELEASE_SCALAR_API_VERSION);
    if (!isOK) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Incompatible versions. app=" + myVersion + ", pkg=" + packageVersion);
      }
    }
    return isOK;
  }

  protected List<ResolveInfo> getResolveInfos() {
    PackageManager pm = context.getPackageManager();
    return pm.queryIntentServices(new Intent(INTENT_ACTION), PackageManager.GET_META_DATA);
  }

  @NonNull
  @VisibleForTesting
  public static ServiceConnection makeServiceConnection(
      final Map<String, ServiceConnection> connections,
      final ComponentName name,
      final AppDiscoveryCallbacks callbacks,
      final Bundle metaData) {
    final String serviceId = extractServiceId(name, metaData);
    ServiceConnection connection =
        new ServiceConnection() {
          @Override
          public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO: think harder about threading here?
            callbacks.onServiceFound(serviceId, ISensorDiscoverer.Stub.asInterface(service));
          }

          @Override
          public void onServiceDisconnected(ComponentName name) {
            // TODO: when should disconnection happen?
            connections.remove(serviceId);
          }
        };
    connections.put(serviceId, connection);
    return connection;
  }

  private static String extractServiceId(ComponentName name, Bundle metaData) {
    if (metaData != null && metaData.containsKey(METADATA_KEY_CLASS_NAME_OVERRIDE)) {
      return name.getPackageName() + "/" + metaData.getString(METADATA_KEY_CLASS_NAME_OVERRIDE);
    } else {
      return name.flattenToShortString();
    }
  }
}
