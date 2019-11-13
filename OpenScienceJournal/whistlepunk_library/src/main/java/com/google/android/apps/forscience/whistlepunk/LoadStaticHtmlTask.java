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

package com.google.android.apps.forscience.whistlepunk;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Log;
import java.io.IOException;
import java.io.InputStreamReader;

/** Loads static HTML files into string data. */
public class LoadStaticHtmlTask extends AsyncTask<Void, Void, String> {

  private static final String TAG = "LoadStaticHtmlTask";

  public interface StaticHtmlLoadListener {
    void onDataLoaded(String data);
  }

  private StaticHtmlLoadListener listener;
  private int fileId;
  private Resources resources;

  public LoadStaticHtmlTask(StaticHtmlLoadListener listener, Resources resources, int fileId) {
    this.resources = resources;
    this.listener = listener;
    this.fileId = fileId;
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

      inputReader = new InputStreamReader(resources.openRawResource(fileId));
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
    listener.onDataLoaded(data);
  }
}
