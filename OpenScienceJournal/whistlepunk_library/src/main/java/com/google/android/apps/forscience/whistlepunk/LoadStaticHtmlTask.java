package com.google.android.apps.forscience.whistlepunk;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Loads static HTML files into string data.
 */
public class LoadStaticHtmlTask extends AsyncTask<Void, Void, String> {

    private static final String TAG = "LoadStaticHtmlTask";

    public interface StaticHtmlLoadListener {
        void onDataLoaded(String data);
    }

    private StaticHtmlLoadListener mListener;
    private int mFileId;
    private Resources mResources;

    public LoadStaticHtmlTask(StaticHtmlLoadListener listener, Resources resources, int fileId) {
        mResources = resources;
        mListener = listener;
        mFileId = fileId;
    }

    @Override
    protected String doInBackground(Void... params) {
        InputStreamReader inputReader = null;
        // This will store the HTML file as a string. 4kB seems comfortable: this string will
        // get released when we're done with this activity.
        StringBuilder data = new StringBuilder(4096);
        try {
            // Read the resource in 2kB chunks.
            char[] tmp = new char[2048];
            int numRead;

            inputReader = new InputStreamReader(mResources.openRawResource(mFileId));
            while ((numRead = inputReader.read(tmp)) >= 0) {
                data.append(tmp, 0, numRead);
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not read static HTML page", e);
        } finally {
            try {
                if (inputReader != null) {
                    inputReader.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not close stream", e);
            }
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Loaded string of " + data.length());
        }
        return data.toString();
    }


    @Override
    protected void onPostExecute(String data) {
        mListener.onDataLoaded(data);
    }
}
