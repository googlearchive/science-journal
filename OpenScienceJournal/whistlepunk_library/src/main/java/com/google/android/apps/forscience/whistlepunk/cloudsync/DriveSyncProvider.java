package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.base.Supplier;
import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/** An cloud sync provider which is backed by Google Drive. */
public class DriveSyncProvider implements CloudSyncProvider {
  private static final String TAG = "DriveSyncProvider";

  private static final boolean ENABLE_HTTP_TRANSPORT_LOGGING = false;

  private final HashMap<AppAccount, DriveSyncManager> accountMap;
  private final Context applicationContext;
  private final Supplier<DriveApi> driveSupplier;

  public DriveSyncProvider(Context context, Supplier<DriveApi> driveSupplier) {
    applicationContext = context.getApplicationContext();
    accountMap = new HashMap<>();

    this.driveSupplier = driveSupplier;

    if (ENABLE_HTTP_TRANSPORT_LOGGING) {
      Logger logger = Logger.getLogger(HttpTransport.class.getName());
      logger.setLevel(Level.ALL);
      ConsoleHandler logHandler = new ConsoleHandler();
      logHandler.setLevel(Level.ALL);
      logger.addHandler(logHandler);
    }
  }

  @Override
  public CloudSyncManager getServiceForAccount(AppAccount appAccount) {
    if (accountMap.get(appAccount) == null) {
      addServiceForAccount(appAccount);
    }

    return accountMap.get(appAccount);
  }

  private void addServiceForAccount(AppAccount appAccount) {


    HttpTransport transport = AndroidHttp.newCompatibleTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    accountMap.put(
        appAccount,
        new DriveSyncManager(
            appAccount,
            AppSingleton.getInstance(applicationContext).getDataController(appAccount),
            transport,
            jsonFactory,
            applicationContext,
            driveSupplier,
            AppSingleton.getInstance(applicationContext)
                .getSensorEnvironment()
                .getDataController(appAccount)));
  }
}
