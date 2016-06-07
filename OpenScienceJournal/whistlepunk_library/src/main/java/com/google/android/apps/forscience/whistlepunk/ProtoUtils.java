package com.google.android.apps.forscience.whistlepunk;

import android.util.Log;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.MessageNano;

import java.io.IOException;

public class ProtoUtils {
    private static final String TAG = "proto_utils";

    public static byte[] makeBlob(MessageNano proto) {
        int serializedSize = proto.getSerializedSize();
        byte[] output = new byte[serializedSize];

        CodedOutputByteBufferNano buffer = CodedOutputByteBufferNano.newInstance(output);
        try {
            proto.writeTo(buffer);
        } catch (IOException e) {
            Log.e(TAG, "Could not serialize config", e);
        }

        return output;
    }
}
