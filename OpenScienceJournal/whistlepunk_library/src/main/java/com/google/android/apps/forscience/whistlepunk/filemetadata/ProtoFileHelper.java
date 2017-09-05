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

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import io.reactivex.functions.Function;

/**
 * Helper to write Protocol Buffers written to and read them from files.
 */
// TODO: Check free storage space before writing anything?
public class ProtoFileHelper<T extends MessageNano> {
    private static final String TAG = "ProtoFileHelper";

    public T readFromFile(File file, Function<byte[], T> parseFrom) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            inputStream.read(bytes);
            return parseFrom.apply(bytes);
        }  catch (IOException ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(ex));
            }
            return null;
        } catch (Exception ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(ex));
            }
            return null;
        }
    }

    public boolean writeToFile(File file, T protoToWrite) {
        return writeToFile(file, protoToWrite, /* don't throw an error for testing */ false);
    }

    @VisibleForTesting
    boolean writeToFile(File file, T protoToWrite, boolean failWritingForTest) {
        // Do this outside the file-writing blocks. If it fails it throws a RuntimeException
        // which we don't want to have happen during reading or writing.
        byte[] protoBytes = MessageNano.toByteArray(protoToWrite);

        // Save what's in the file in case the write fails. This is a safety net because
        // the FileOutputStream clears the file and we need a way to put the data back
        // to how it was.
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            inputStream.read(bytes);
        } catch (IOException ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(ex));
            }
            return false;
        }

        // Now try to write.
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            if (failWritingForTest) {
                throw new IOException("Failing for the test");
            } else {
                outputStream.write(protoBytes);
                return true;
            }
        } catch (IOException ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(ex));
            }
            replaceContents(file, bytes);
            return false;
        }
    }

    private void replaceContents(File file, byte[] bytes) {
        // If write failed, might be the proto's fault, try to write back what we had before.
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(bytes);
        } catch (IOException ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(ex));
            }
        }
    }
}
