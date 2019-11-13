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

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import androidx.annotation.Nullable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

/**
 * This is deprecated; we will be using an application-global AppState class instead.
 *
 * <p>A main-process service that provides global data connectivity to any activity
 *
 * <p>Over time, using this should replace AppSingleton, so that eventually Android can better
 * garbage collect global resources when unneeded.
 *
 * <p>To bind from an activity, use:
 *
 * <pre>
 * class MyActivity extends Activity {
 *     public void scanForDevicesOnce() {
 *         DataService.bind(this).subscribe(appSingleton ->
 *             appSingleton.getBleClient().scanForDevices(...);
 *         );
 *     }
 * }
 * </pre>
 *
 * Following the RxJava style, this separates the users of a stream from how it is produced. The
 * current implementation binds to the service just long enough to satisfy the immediate subscriber,
 * and then unbinds again. If this turns out to have a poor performance impact, we can switch the
 * implementation here to cache the connection without effecting users of the interface.
 */
public class DataService extends Service {
  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return new DataBinder(AppSingleton.getInstance(this));
  }

  private class DataBinder extends Binder {
    private AppSingleton singleton;

    public DataBinder(AppSingleton singleton) {
      this.singleton = singleton;
    }

    public AppSingleton getData() {
      return singleton;
    }
  }

  public static Single<AppSingleton> bind(Context context) {
    Context appContext = context.getApplicationContext();

    return Single.create(
        emitter -> {
          ServiceConnection conn =
              new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                  DataBinder binder = (DataBinder) service;
                  emitter.onSuccess(binder.getData());
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {}
              };
          Intent intent = new Intent(appContext, DataService.class);
          if (appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
            emitter.setDisposable(
                new Disposable() {
                  public boolean disposed = false;

                  @Override
                  public void dispose() {
                    appContext.unbindService(conn);
                    disposed = true;
                  }

                  @Override
                  public boolean isDisposed() {
                    return disposed;
                  }
                });
          } else {
            emitter.onError(new Exception("Could not bind DataService"));
          }
        });
  }
}
