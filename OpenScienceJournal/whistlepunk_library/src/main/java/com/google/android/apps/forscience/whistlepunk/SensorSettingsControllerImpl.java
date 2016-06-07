package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ActiveBundle;
import com.google.android.apps.forscience.whistlepunk.sensorapi.OptionsListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;


public class SensorSettingsControllerImpl extends ActiveSettingsController implements
        SensorSettingsController {
    private Context mContext;

    public SensorSettingsControllerImpl(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void launchOptionsDialog(SensorChoice source, final SensorPresenter presenter,
            NewOptionsStorage storage, final OptionsListener commitListener,
            FailureListener failureListener) {
        Resources resources = mContext.getResources();
        String name = getSourceName(source);
        String optionsTitle = resources.getString(R.string.sensor_options);
        OptionsCallbacks callbacks = new OptionsCallbacks() {
            @Override
            public View buildOptionsView(ActiveBundle activeBundle) {
                return presenter.getOptionsPresenter().buildOptionsView(activeBundle, mContext);
            }

            @Override
            public void previewOptions(ReadableSensorOptions newOptions) {
                presenter.getOptionsPresenter().applyOptions(newOptions);
            }

            @Override
            public void commitOptions(ReadableSensorOptions newOptions) {
                previewOptions(newOptions);
                commitListener.applyOptions(newOptions);
            }
        };
        super.launchOptionsDialog(callbacks, name, optionsTitle, storage.load(failureListener));
    }

    private String getSourceName(SensorChoice source) {
        return AppSingleton.getInstance(mContext)
                .getSensorAppearanceProvider()
                .getAppearance(source.getId())
                .getName(mContext);
    }
}
