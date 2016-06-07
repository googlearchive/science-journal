package com.google.android.apps.forscience.whistlepunk;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Displays settings, dev options or about fragments as activity.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String KEY_TYPE = "type";
    private static final String KEY_TITLE = "title";

    // The different types of display.
    @IntDef({TYPE_SETTINGS, TYPE_DEV_OPTIONS, TYPE_ABOUT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SettingsType {}

    public static final int TYPE_SETTINGS = 0;
    public static final int TYPE_DEV_OPTIONS = 1;
    public static final int TYPE_ABOUT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        int settingsType = getIntent().getIntExtra(KEY_TYPE, TYPE_SETTINGS);
        Fragment fragment;
        switch (settingsType) {
            case TYPE_SETTINGS:
                fragment = new SettingsFragment();
                break;
            case TYPE_DEV_OPTIONS:
                fragment = DevOptionsFragment.newInstance();
                break;
            default:
                throw new IllegalArgumentException("Unknown settings type " + settingsType);
        }
        getFragmentManager().beginTransaction().replace(R.id.container, fragment)
                .commit();

        setTitle(getIntent().getExtras().getString(KEY_TITLE, getString(R.string.action_settings)));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public static Intent getLaunchIntent(Context context, CharSequence title,
                                         @SettingsType int type) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(KEY_TYPE, type);
        intent.putExtra(KEY_TITLE, title);
        return intent;
    }
}
