package com.google.android.apps.forscience.whistlepunk;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.wireapi.IRecorderController;
import com.google.android.apps.forscience.whistlepunk.wireapi.ISensorStatusListener;

/**
 * Exports the current main-thread RecorderController as a service that other applications can bind
 * to.  Currently only supports startObserving and stopObserving for a single sensor
 */
public class PublicRecorderService extends Service implements FailureListener,
        ProxyRecorderController.BindingPolicy {
    private static final String TAG = "ExportedService";

    private IRecorderController.Stub mController;

    @Nullable
    @Override
    public IRecorderController.Stub onBind(Intent intent) {
        return getController();
    }

    private IRecorderController.Stub getController() {
        if (mController == null) {
            mController = createController();
        }
        return mController;
    }

    // TODO: unit tests for this behavior
    private IRecorderController.Stub createController() {
        final RecorderController rc = AppSingleton.getInstance(this).getRecorderController();
        return new ProxyRecorderController(rc, this, this);
    }

    /**
     * Check that the connecting user is one we'd like to talk to.
     *
     * Note that this method only returns valid results when called from within methods defined
     * on the Binder class, not methods like onBind on the service itself.
     */
    public void checkBinderAllowed() {
        int uid = Binder.getCallingUid();
        String bindingPackage = getPackageManager().getNameForUid(uid);
        String thisPackage = getPackageName();
        int check = getPackageManager().checkSignatures(thisPackage, bindingPackage);
        // See docs: 0 and up are various "OK" states, negative values are various errnos
        if (check < 0) {
            throw new IllegalStateException(
                    "Cannot bind to service from package, wrong signatures: " + bindingPackage
                            + ", returned: " + check);
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
