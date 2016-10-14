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
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.google.android.apps.forscience.javalib.Consumer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds services that implement the scalarinput API
 */
public class ScalarSensorServiceFinder extends Consumer<AppDiscoveryCallbacks> {
    public static final String INTENT_ACTION =
            "com.google.android.apps.forscience.whistlepunk.SCALAR_SENSOR";
    public static final String METADATA_KEY_CLASS_NAME_OVERRIDE = "api_service_logical_name";

    private final Context mContext;
    private final Map<String, ServiceConnection> mConnections = new HashMap<>();

    public ScalarSensorServiceFinder(Context context) {
        mContext = context;
    }

    @Override
    public void take(AppDiscoveryCallbacks callbacks) {
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(new Intent(INTENT_ACTION),
                PackageManager.GET_META_DATA);
        for (ResolveInfo info : resolveInfos) {
            ServiceInfo serviceInfo = info.serviceInfo;
            String packageName = serviceInfo.packageName;
            ComponentName name = new ComponentName(packageName, serviceInfo.name);
            Intent intent = new Intent();
            intent.setComponent(name);
            final ServiceConnection conn = makeServiceConnection(mConnections, packageName,
                    callbacks, serviceInfo.metaData);
            mConnections.put(packageName, conn);
            mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        }
        // TODO: need to figure out when to call onDiscovery done (after every service we know
        // about has connected or timed out).
    }

    @NonNull
    @VisibleForTesting
    public static ServiceConnection makeServiceConnection(
            final Map<String, ServiceConnection> connections, final String packageName,
            final AppDiscoveryCallbacks callbacks, final Bundle metaData) {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                // TODO: think harder about threading here?
                callbacks.onServiceFound(extractServiceId(name),
                        ISensorDiscoverer.Stub.asInterface(service));
            }

            private String extractServiceId(ComponentName name) {
                if (metaData != null && metaData.containsKey(METADATA_KEY_CLASS_NAME_OVERRIDE)) {
                    return name.getPackageName() + "/" + metaData.getString(
                            METADATA_KEY_CLASS_NAME_OVERRIDE);
                } else {
                    return name.flattenToShortString();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // TODO: when should disconnection happen?
                connections.remove(packageName);
            }
        };
    }
}
