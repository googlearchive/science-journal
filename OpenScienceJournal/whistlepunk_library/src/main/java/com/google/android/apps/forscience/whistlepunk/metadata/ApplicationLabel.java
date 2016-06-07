package com.google.android.apps.forscience.whistlepunk.metadata;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A label which represents a note made by the application.
 * This can be used for recording start/stop events or any background
 * events controlled by the app.
 */
public class ApplicationLabel extends Label {

    // Types of ApplicationLabel. Add more types here as needed.
    @IntDef({TYPE_RECORDING_START, TYPE_RECORDING_STOP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    public static final int TYPE_RECORDING_START = 1;
    public static final int TYPE_RECORDING_STOP = 2;

    public static final String TAG = "application";
    public static final String VALUE_PREFIX = "application_type_";

    private static final int NUM_FIELDS = 1;
    private static final int INDEX_VALUE_TYPE = 0;
    private static final String KEY_VALUE_TYPE = "value_type";

    public ApplicationLabel(String id, String startLabelId, long timestamp,
            GoosciLabelValue.LabelValue value) {
        super(id, startLabelId, timestamp, value);
    }

    // TODO: use a clock to build labels.
    public ApplicationLabel(@Type int type, String labelId, String startLabelId,
            long timestampMillis) {
        this(labelId, startLabelId, timestampMillis, createStorageValue(type));
    }

    ApplicationLabel(String value, String labelId, String startLabelId, long timestampMillis) {
        this(valueToType(value), labelId, startLabelId, timestampMillis);
    }

    private ApplicationLabel() {
        super();
    }

    private static GoosciLabelValue.LabelValue createStorageValue(@Type int type) {
        GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
        value.data = new GoosciLabelValue.LabelValue.DataEntry[NUM_FIELDS];
        value.data[INDEX_VALUE_TYPE] = new GoosciLabelValue.LabelValue.DataEntry();
        value.data[INDEX_VALUE_TYPE].key = KEY_VALUE_TYPE;
        value.data[INDEX_VALUE_TYPE].value = String.valueOf(type);
        return value;
    }

    public @Type int getType() {
        return Integer.parseInt(getValue().data[INDEX_VALUE_TYPE].value);
    }

    @Override
    public String getTag() {
        return TAG;
    }

    // This function is only used when parsing a deprecated version of ApplicationLabel,
    // for users who created labels on a database version earlier than 15.
    static public @Type int valueToType(String value) {
        String suffix = value.substring(VALUE_PREFIX.length(), value.length());
        @Type int result = Integer.parseInt(suffix);
        return result;
    }

    static boolean isTag(String tag) {
        return TAG.equalsIgnoreCase(tag);
    }

    public static final Parcelable.Creator<ApplicationLabel> CREATOR =
            new Parcelable.Creator<ApplicationLabel>() {
                public ApplicationLabel createFromParcel(Parcel in) {
                    ApplicationLabel label = new ApplicationLabel();
                    label.populateFromParcel(in);
                    return label;
                }

                @Override
                public ApplicationLabel[] newArray(int size) {
                    return new ApplicationLabel[size];
                }
    };
}
