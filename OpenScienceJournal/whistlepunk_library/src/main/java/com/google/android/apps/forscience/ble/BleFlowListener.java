package com.google.android.apps.forscience.ble;

import java.util.UUID;

/**
 * Listener used by the BleFlow class.
 */
public abstract class BleFlowListener {

    public abstract void onSuccess();

    public abstract void onFailure(Exception error);

    public abstract void onCharacteristicRead(UUID characteristic, int flags, byte[] value);

    public abstract void onNotification(UUID characteristic, int flags, byte[] value);

    public abstract void onDisconnect();

    public abstract void onConnect();

    public abstract void onNotificationSubscribed();

    public abstract void onNotificationUnsubscribed();
}