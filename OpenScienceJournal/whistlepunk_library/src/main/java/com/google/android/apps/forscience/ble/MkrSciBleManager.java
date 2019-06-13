package com.google.android.apps.forscience.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * This is the entry point for subscribing a sensor and receiving data from an Arduino MKR SCI
 * external board.
 *
 * <p>subscribe() and unsubscribe() methods allow to start and stop receiving data from the external
 * board through a BLE connection. When subscribing, following information need to be passed:
 *
 * <ul>
 *   <li>external device address;
 *   <li>characteristic (sensor) to be subscribed (valid ones are available as constants in the
 *       class);
 *   <li>a listener for receiving values from subscribed characteristic.
 * </ul>
 */
public class MkrSciBleManager {

  public static final String SERVICE_UUID = "555a0001-0000-467a-9538-01f0652c74e8";

  private static final String VERSION_UUID = "555a0001-0001-467a-9538-01f0652c74e8";

  public static final String INPUT_1_UUID = "555a0001-2001-467a-9538-01f0652c74e8";
  public static final String INPUT_2_UUID = "555a0001-2002-467a-9538-01f0652c74e8";
  public static final String INPUT_3_UUID = "555a0001-2003-467a-9538-01f0652c74e8";
  public static final String VOLTAGE_UUID = "555a0001-4001-467a-9538-01f0652c74e8";
  public static final String CURRENT_UUID = "555a0001-4002-467a-9538-01f0652c74e8";
  public static final String RESISTANCE_UUID = "555a0001-4003-467a-9538-01f0652c74e8";
  public static final String ACCELEROMETER_UUID = "555a0001-5001-467a-9538-01f0652c74e8";
  public static final String GYROSCOPE_UUID = "555a0001-5002-467a-9538-01f0652c74e8";
  public static final String MAGNETOMETER_UUID = "555a0001-5003-467a-9538-01f0652c74e8";

  private static final double MAX_VALUE = 2000000000D;
  private static final double MIN_VALUE = -2000000000D;

  private static final Handler handler = new Handler(Looper.getMainLooper());

  // device bt address > gatt handler
  private static final Map<String, GattHandler> gattHandlers = new HashMap<>();

  public static void subscribe(
      Context context, String address, String characteristic, Listener listener) {
    synchronized (gattHandlers) {
      GattHandler gattHandler = gattHandlers.get(address);
      if (gattHandler == null) {
        BluetoothManager manager =
            (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
          return;
        }
        BluetoothAdapter adapter = manager.getAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(address);
        gattHandler = new GattHandler();
        gattHandlers.put(address, gattHandler);
        device.connectGatt(context, true /* autoConnect */, gattHandler);
      }
      gattHandler.subscribe(characteristic, listener);
    }
  }

  public static void unsubscribe(String address, String characteristic, Listener listener) {
    synchronized (gattHandlers) {
      GattHandler gattHandler = gattHandlers.get(address);
      if (gattHandler != null) {
        gattHandler.unsubscribe(characteristic, listener);
        handler.postDelayed(
            () -> {
              synchronized (gattHandlers) {
                if (!gattHandler.hasSubscribers()) {
                  gattHandlers.remove(address);
                  gattHandler.disconnect();
                }
              }
            },
            2000L);
      }
    }
  }

  private static class GattHandler extends BluetoothGattCallback {

    private static final UUID NOTIFICATION_DESCRIPTOR =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Map<String, List<Listener>> listenersMap = new HashMap<>();

    private BluetoothGatt gatt;

    private final List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();

    private final List<Runnable> gattActions = new ArrayList<>();

    private boolean readyForAction = false;

    private boolean busy = false;

    private long firmwareVersion = -1;

    private void disconnect() {
      if (gatt != null) {
        gatt.disconnect();
      }
    }

    private void subscribe(String characteristicUuid, Listener listener) {
      boolean subscribe = false;
      synchronized (listenersMap) {
        List<Listener> listeners = listenersMap.get(characteristicUuid);
        if (listeners == null) {
          listeners = new ArrayList<>();
          listenersMap.put(characteristicUuid, listeners);
          subscribe = true;
        }
        listeners.add(listener);
        if (firmwareVersion > -1) {
          listener.onFirmwareVersion(firmwareVersion);
        }
      }
      if (subscribe) {
        enqueueGattAction(
            () -> {
              BluetoothGattCharacteristic c = getCharacteristic(characteristicUuid);
              if (c != null) {
                gatt.setCharacteristicNotification(c, true);
                BluetoothGattDescriptor d = c.getDescriptor(NOTIFICATION_DESCRIPTOR);
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(d);
              }
            });
      }
    }

    private void unsubscribe(String characteristicUuid, Listener listener) {
      boolean unsubscribe = false;
      synchronized (listenersMap) {
        List<Listener> listeners = listenersMap.get(characteristicUuid);
        if (listeners != null) {
          listeners.remove(listener);
          if (listeners.size() == 0) {
            listenersMap.remove(characteristicUuid);
            unsubscribe = true;
          }
        }
      }
      if (unsubscribe) {
        enqueueGattAction(
            () -> {
              BluetoothGattCharacteristic c = getCharacteristic(characteristicUuid);
              if (c != null) {
                gatt.setCharacteristicNotification(c, true);
                BluetoothGattDescriptor d = c.getDescriptor(NOTIFICATION_DESCRIPTOR);
                d.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(d);
              }
            });
      }
    }

    private boolean hasSubscribers() {
      synchronized (listenersMap) {
        return listenersMap.size() > 0;
      }
    }

    private BluetoothGattCharacteristic getCharacteristic(String uuid) {
      for (BluetoothGattCharacteristic aux : characteristics) {
        if (Objects.equals(uuid, aux.getUuid().toString())) {
          return aux;
        }
      }
      return null;
    }

    private void enqueueGattAction(Runnable action) {
      synchronized (gattActions) {
        if (readyForAction && !busy) {
          busy = true;
          action.run();
        } else {
          gattActions.add(action);
        }
      }
    }

    private void onGattActionCompleted() {
      synchronized (gattActions) {
        if (readyForAction && gattActions.size() > 0) {
          busy = true;
          gattActions.remove(0).run();
        } else {
          busy = false;
        }
      }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
        this.gatt = gatt;
        characteristics.clear();
        gatt.discoverServices();
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        readyForAction = false;
        gatt.disconnect();
      }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      BluetoothGattService service = this.gatt.getService(UUID.fromString(SERVICE_UUID));
      if (service != null) {
        characteristics.addAll(service.getCharacteristics());
      }
      BluetoothGattCharacteristic c = getCharacteristic(VERSION_UUID);
      if (c != null) {
        this.gatt.readCharacteristic(c);
      }
    }

    @Override
    public void onDescriptorRead(
        BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
      onGattActionCompleted();
    }

    @Override
    public void onDescriptorWrite(
        BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
      onGattActionCompleted();
    }

    @Override
    public void onCharacteristicRead(
        BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      final String uuid = characteristic.getUuid().toString();
      if (VERSION_UUID.equals(uuid) && firmwareVersion == -1) {
        final byte[] value = characteristic.getValue();
        if (value.length == 4) {
          final ByteBuffer buffer = ByteBuffer.allocate(8);
          buffer.put((byte) 0);
          buffer.put((byte) 0);
          buffer.put((byte) 0);
          buffer.put((byte) 0);
          buffer.put(value[3]);
          buffer.put(value[2]);
          buffer.put(value[1]);
          buffer.put(value[0]);
          buffer.position(0);
          firmwareVersion = buffer.getLong();
          // delivering to listener(s)
          synchronized (listenersMap) {
            for (List<Listener> listeners : listenersMap.values()) {
              if (listeners != null) {
                for (Listener l : listeners) {
                  l.onFirmwareVersion(firmwareVersion);
                }
              }
            }
          }
        }
        readyForAction = true;
      }
      onGattActionCompleted();
    }

    @Override
    public void onCharacteristicWrite(
        BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      onGattActionCompleted();
    }

    @Override
    public void onCharacteristicChanged(
        BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      final String uuid = characteristic.getUuid().toString();
      final ValueType type;
      switch (uuid) {
        case INPUT_1_UUID:
        case INPUT_2_UUID:
        case INPUT_3_UUID:
          type = ValueType.UINT16;
          break;
        case VOLTAGE_UUID:
        case CURRENT_UUID:
        case RESISTANCE_UUID:
          type = ValueType.SFLOAT;
          break;
        case ACCELEROMETER_UUID:
        case GYROSCOPE_UUID:
        case MAGNETOMETER_UUID:
          type = ValueType.SFLOAT_ARR;
          break;
        default:
          type = null;
      }
      if (type != null) {
        final double[] values = parse(type, characteristic.getValue());
        if (values != null) {
          // filter to avoid too large values blocking the UI
          for (int i = 0; i < values.length; i++) {
            if (values[i] > MAX_VALUE) {
              values[i] = MAX_VALUE;
            } else if (values[i] < MIN_VALUE) {
              values[i] = MIN_VALUE;
            }
          }
          // delivering to listener(s)
          synchronized (listenersMap) {
            List<Listener> listeners = listenersMap.get(uuid);
            if (listeners != null) {
              for (Listener l : listeners) {
                l.onValuesUpdated(values);
              }
            }
          }
        }
      }
    }
  }

  private static double[] parse(ValueType valueType, byte[] value) {
    if (ValueType.UINT8.equals(valueType)) {
      if (value.length < 1) {
        return null;
      }
      final ByteBuffer buffer = ByteBuffer.allocate(4);
      buffer.put((byte) 0);
      buffer.put((byte) 0);
      buffer.put((byte) 0);
      buffer.put(value[0]);
      buffer.position(0);
      return new double[] {buffer.getInt()};
    }
    if (ValueType.UINT16.equals(valueType)) {
      if (value.length < 2) {
        return null;
      }
      final ByteBuffer buffer = ByteBuffer.allocate(4);
      buffer.put((byte) 0);
      buffer.put((byte) 0);
      buffer.put(value[1]);
      buffer.put(value[0]);
      buffer.position(0);
      return new double[] {buffer.getInt()};
    }
    if (ValueType.UINT32.equals(valueType)) {
      if (value.length < 4) {
        return null;
      }
      final ByteBuffer buffer = ByteBuffer.allocate(8);
      buffer.put((byte) 0);
      buffer.put((byte) 0);
      buffer.put((byte) 0);
      buffer.put((byte) 0);
      buffer.put(value[3]);
      buffer.put(value[2]);
      buffer.put(value[1]);
      buffer.put(value[0]);
      buffer.position(0);
      return new double[] {buffer.getLong()};
    }
    if (ValueType.SFLOAT.equals(valueType)) {
      if (value.length < 4) {
        return null;
      }
      final ByteBuffer buffer = ByteBuffer.allocate(4);
      buffer.put(value[3]);
      buffer.put(value[2]);
      buffer.put(value[1]);
      buffer.put(value[0]);
      buffer.position(0);
      return new double[] {buffer.getFloat()};
    }
    if (ValueType.SFLOAT_ARR.equals(valueType)) {
      final int size = value.length / 4;
      final double[] array = new double[size];
      final ByteBuffer buffer = ByteBuffer.allocate(4);
      for (int i = 0; i < size; i++) {
        final int offset = 4 * i;
        buffer.position(0);
        buffer.put(value[3 + offset]);
        buffer.put(value[2 + offset]);
        buffer.put(value[1 + offset]);
        buffer.put(value[offset]);
        buffer.position(0);
        array[i] = buffer.getFloat();
      }
      return array;
    }
    return null;
  }

  private enum ValueType {
    UINT8,
    UINT16,
    UINT32,
    SFLOAT,
    SFLOAT_ARR
  }

  /**
   * Values read from a subscribed characteristic/sensor available in the external board are passed
   * through implementations of this interface.
   */
  public interface Listener {
    void onFirmwareVersion(long firmwareVersion);

    void onValuesUpdated(double[] values);
  }
}
