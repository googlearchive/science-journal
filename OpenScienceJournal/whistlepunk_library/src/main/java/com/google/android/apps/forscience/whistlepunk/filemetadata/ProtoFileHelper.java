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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.util.Log;

import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Helper to write Protocol Buffers written to and read them from files.
 */
// TODO: Check free storage space before writing anything?
public class ProtoFileHelper<T extends MessageNano> {
    private static final String TAG = "ProtoFileHelper";

    public boolean readFromFile(File file, T protoToPopulate) {
        FileInputStream inputStream;
        try {
            inputStream = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            inputStream.read(bytes);
            MessageNano.mergeFrom(protoToPopulate, bytes);
            return true;
        }  catch (IOException ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(ex));
            }
            return false;
        }
    }

    public boolean writeToFile(File file, T protoToWrite) {
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(MessageNano.toByteArray(protoToWrite));
            outputStream.close();
            return true;
        } catch (IOException ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(ex));
            }
            return false;
        }
    }
}
