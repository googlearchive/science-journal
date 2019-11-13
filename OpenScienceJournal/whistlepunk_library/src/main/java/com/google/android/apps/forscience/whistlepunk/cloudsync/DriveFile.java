package com.google.android.apps.forscience.whistlepunk.cloudsync;

/**
 * A wrapper around the Drive API's File class. File is a final class, and thus impossible to mock.
 * Also, I'd prefer to not expose Drive classes outside of the DriveApi.
 */
public interface DriveFile {

  long getVersion();

  String getId();

  String getTitle();
}
