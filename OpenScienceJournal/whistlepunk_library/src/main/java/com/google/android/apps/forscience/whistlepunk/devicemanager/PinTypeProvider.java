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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.util.Log;

public class PinTypeProvider {
  public static final PinType DEFAULT_PIN = new PinType(PinSignalType.ANALOG, 0);

  private PinType[] pins;

  public PinTypeProvider() {
    pins =
        new PinType[] {
          DEFAULT_PIN,
          new PinType(PinSignalType.ANALOG, 1),
          new PinType(PinSignalType.ANALOG, 2),
          new PinType(PinSignalType.ANALOG, 3),
          new PinType(PinSignalType.ANALOG, 4),
          new PinType(PinSignalType.ANALOG, 5),
          new PinType(PinSignalType.ANALOG, 6),
          new PinType(PinSignalType.ANALOG, 7),
          new PinType(PinSignalType.ANALOG, 8),
          new PinType(PinSignalType.ANALOG, 9),
          new PinType(PinSignalType.ANALOG, 10),
          new PinType(PinSignalType.ANALOG, 11),
          new PinType(PinSignalType.DIGITAL, 2),
          new PinType(PinSignalType.DIGITAL, 3),
          new PinType(PinSignalType.DIGITAL, 4),
          new PinType(PinSignalType.DIGITAL, 5),
          new PinType(PinSignalType.DIGITAL, 6),
          new PinType(PinSignalType.DIGITAL, 7),
          new PinType(PinSignalType.DIGITAL, 8),
          new PinType(PinSignalType.DIGITAL, 9),
          new PinType(PinSignalType.DIGITAL, 10),
          new PinType(PinSignalType.DIGITAL, 11),
          new PinType(PinSignalType.DIGITAL, 12),
          new PinType(PinSignalType.DIGITAL, 13),
        };
  }

  public enum PinSignalType {
    ANALOG,
    DIGITAL,
    VIRTUAL,
  }

  public static class PinType {
    private String TAG = "PinType";
    private PinSignalType pinSignalType;
    private int pinNumber;

    PinType(PinSignalType pinSignalType, int pinNumber) {
      this.pinSignalType = pinSignalType;
      this.pinNumber = pinNumber;
    }

    public PinSignalType getPinSignalType() {
      return pinSignalType;
    }

    public int getPinNumber() {
      return pinNumber;
    }

    @Override
    public String toString() {
      String prefix;
      switch (pinSignalType) {
        case ANALOG:
          prefix = "A";
          break;
        case DIGITAL:
          prefix = "D";
          break;
        case VIRTUAL:
          prefix = "V";
          break;
        default:
          Log.wtf(TAG, "Unexpected enum value: " + pinSignalType);
          prefix = "X";
      }
      return prefix + pinNumber;
    }
  }

  public PinType[] getPins() {
    return pins;
  }

  /* Parse a pin in the format "A0", "D1", or "V10".  Returns null on parse failure. */
  public PinType parsePinName(String pinName) {
    if (pinName.isEmpty()) {
      // default
      return new PinType(PinSignalType.ANALOG, 0);
    } else if (pinName.startsWith("A")) {
      return new PinType(PinSignalType.ANALOG, Integer.valueOf(pinName.substring(1)));
    } else if (pinName.startsWith("D")) {
      return new PinType(PinSignalType.DIGITAL, Integer.valueOf(pinName.substring(1)));
    } else if (pinName.startsWith("V")) {
      return new PinType(PinSignalType.VIRTUAL, Integer.valueOf(pinName.substring(1)));
    } else {
      return null;
    }
  }
}
