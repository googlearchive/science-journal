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
package com.google.android.apps.forscience.whistlepunk;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.collection.ArrayMap;
import android.util.Log;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.project.experiment.UpdateExperimentFragment;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Strings;
import com.google.common.collect.Range;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipException;

/**
 * Service for importing and exporting trial and experiment data with different options. Can be
 * bound for status updates using {@link #bind(Context)}. Export trial data using {@link
 * #exportTrial(Context, AppAccount, String, String, boolean, String[])} Export experiment data
 * using {@link #exportExperiment(Context, AppAccount, String)} Import experiment data using {@link
 * #importExperiment(Context, AppAccount, Uri)} TODO: Rename to ImportExportService
 */
public class ExportService extends Service {
  private static final String TAG = "ExportService";

  private static final String ACTION_EXPORT_TRIAL =
      "com.google.android.apps.forscience.whistlepunk.action.EXPORT_TRIAL";

  private static final String ACTION_EXPORT_EXPERIMENT =
      "com.google.android.apps.forscience.whistlepunk.action.EXPORT_EXPERIMENT";

  private static final String ACTION_IMPORT_EXPERIMENT =
      "com.google.android.apps.forscience.whistlepunk.action.IMPORT_EXPERIMENT";

  private static final String ACTION_CLEAN_OLD_FILES =
      "com.google.android.apps.forscience.whistlepunk.action.CLEAN_OLD_FILES";

  private static final String EXTRA_ACCOUNT_KEY =
      "com.google.android.apps.forscience.whistlepunk.extra.ACCOUNT_KEY";
  private static final String EXTRA_EXPERIMENT_ID =
      "com.google.android.apps.forscience.whistlepunk.extra.EXPERIMENT_ID";
  private static final String EXTRA_TRIAL_ID =
      "com.google.android.apps.forscience.whistlepunk.extra.TRIAL_ID";
  private static final String EXTRA_RELATIVE_TIME =
      "com.google.android.apps.forscience.whistlepunk.extra.RELATIVE_TIME";
  private static final String EXTRA_SENSOR_IDS =
      "com.google.android.apps.forscience.whistlepunk.extra.SENSOR_IDS";
  private static final String EXTRA_IMPORT_URI =
      "com.google.android.apps.forscience.whistlepunk.extra.IMPORT_URI";
  private static final String EXTRA_SAVE_LOCALLY =
      "com.google.android.apps.forscience.whistlepunk.extra.SAVE_LOCALLY";

  private final IBinder binder = new ExportServiceBinder();

  // Make static so that all instances of this service can reach it.
  private static final BehaviorSubject<ExportProgress> progressSubject =
      BehaviorSubject.createDefault(new ExportProgress("", ExportProgress.NOT_EXPORTING, 0));

  // Copied from IntentService: basically we do everything the same except wait to call stopSelf
  // until subscriptions finish.
  private volatile Looper serviceLooper;
  private volatile ServiceHandler serviceHandler;

  private final class ServiceHandler extends Handler {
    public ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      onHandleIntent((Intent) msg.obj, msg.arg1);
    }
  }

  public class ExportServiceBinder extends Binder {
    ExportService getService() {
      return ExportService.this;
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    HandlerThread thread = new HandlerThread("ExportService");
    thread.start();
    serviceLooper = thread.getLooper();
    serviceHandler = new ServiceHandler(serviceLooper);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Message msg = serviceHandler.obtainMessage();
    msg.arg1 = startId;
    msg.obj = intent;
    serviceHandler.sendMessage(msg);

    return START_NOT_STICKY;
  }

  /**
   * Starts this service to perform action export trial with the given parameters. If the service is
   * already performing a task this action will be queued.
   */
  public static void exportTrial(
      Context context,
      AppAccount appAccount,
      String experimentId,
      String trialId,
      boolean relativeTime,
      boolean saveLocally,
      String[] sensorIds) {
    Intent intent = new Intent(context, ExportService.class);
    intent.setAction(ACTION_EXPORT_TRIAL);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    intent.putExtra(EXTRA_TRIAL_ID, trialId);
    intent.putExtra(EXTRA_RELATIVE_TIME, relativeTime);
    intent.putExtra(EXTRA_SENSOR_IDS, sensorIds);
    intent.putExtra(EXTRA_SAVE_LOCALLY, saveLocally);
    startService(context, intent, TrackerConstants.ACTION_EXPORT_TRIAL);
  }

  /**
   * Starts this service to perform action export experiment with the given parameters. If the
   * service is already performing a task this action will be queued.
   */
  public static void exportExperiment(Context context, AppAccount appAccount, String experimentId) {
    Intent intent = new Intent(context, ExportService.class);
    intent.setAction(ACTION_EXPORT_EXPERIMENT);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    startService(context, intent, TrackerConstants.ACTION_EXPORT_EXPERIMENT);
  }

  /**
   * Starts this service to perform action import experiment with the given parameters. If the
   * service is already performing a task this action will be queued.
   */
  public static void importExperiment(Context context, AppAccount appAccount, Uri file) {
    Intent intent = new Intent(context, ExportService.class);
    intent.setAction(ACTION_IMPORT_EXPERIMENT);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_IMPORT_URI, file.toString());
    startService(context, intent, TrackerConstants.ACTION_IMPORT_EXPERIMENT);
  }

  /** Starts this service to clean up old files. */
  public static void cleanOldFiles(Context context, AppAccount appAccount) {
    Intent intent = new Intent(context, ExportService.class);
    intent.setAction(ACTION_CLEAN_OLD_FILES);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    startService(context, intent, TrackerConstants.ACTION_CLEAN_OLD_EXPORT_FILES);
  }

  // TODO(b/139755550): Move startService to an appropriate utility class.
  private static void startService(Context context, Intent intent, String trackerAction) {
    try {
      context.getApplicationContext().startService(intent);
    } catch (IllegalStateException e) {
      // The application is in a state where the service can not be started (such as not in the
      // foreground in a state when services are allowed).
      String labelFromStackTrace = TrackerConstants.createLabelFromStackTrace(e);
      WhistlePunkApplication.getUsageTracker(context)
          .trackEvent(TrackerConstants.CATEGORY_FAILURE, trackerAction, labelFromStackTrace, 0);
    }
  }

  private AppAccount getAppAccount(Intent intent) {
    return WhistlePunkApplication.getAccount(getApplicationContext(), intent, EXTRA_ACCOUNT_KEY);
  }

  private void onHandleIntent(Intent intent, int startId) {
    if (intent != null) {
      final String action = intent.getAction();
      if (ACTION_EXPORT_TRIAL.equals(action)) {
        AppAccount appAccount = getAppAccount(intent);
        final String experimentId = intent.getStringExtra(EXTRA_EXPERIMENT_ID);
        final String trialId = intent.getStringExtra(EXTRA_TRIAL_ID);
        final boolean relativeTime = intent.getBooleanExtra(EXTRA_RELATIVE_TIME, false);
        final String[] sensorIds = intent.getStringArrayExtra(EXTRA_SENSOR_IDS);
        handleActionExportTrial(
            appAccount, experimentId, trialId, relativeTime, sensorIds, startId);
      } else if (ACTION_EXPORT_EXPERIMENT.equals(action)) {
        AppAccount appAccount = getAppAccount(intent);
        final String experimentId = intent.getStringExtra(EXTRA_EXPERIMENT_ID);
        handleActionExportExperiment(appAccount, experimentId, startId);
      } else if (ACTION_CLEAN_OLD_FILES.equals(action)) {
        AppAccount appAccount = getAppAccount(intent);
        handleCleanOldFiles(appAccount, startId);
      } else if (ACTION_IMPORT_EXPERIMENT.equals(action)) {
        AppAccount appAccount = getAppAccount(intent);
        final Uri file = Uri.parse(intent.getStringExtra(EXTRA_IMPORT_URI));
        handleActionImportExperiment(appAccount, file, startId);
      }
    }
  }

  @Override
  public void onDestroy() {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Destroying service");
    }
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public BehaviorSubject<ExportProgress> getProgressSubject() {
    return progressSubject;
  }

  public static class ExportProgress {
    public static final int NOT_EXPORTING = 0;
    public static final int ERROR = 1;
    public static final int EXPORTING = 2;
    public static final int EXPORT_COMPLETE = 3;

    private final String id;
    private final int state;
    private final int progress;

    private Throwable error;
    private Uri fileUri;

    // id should be a UUID, like a trialId or an experimentId.
    public ExportProgress(String id, int state, int progress) {
      this.id = id;
      this.state = state;
      this.progress = progress;
    }

    public String getId() {
      return id;
    }

    public int getState() {
      return state;
    }

    public int getProgress() {
      return progress;
    }

    public Throwable getError() {
      return error;
    }

    public Uri getFileUri() {
      return fileUri;
    }

    @Override
    public String toString() {
      return "State: " + state + " progress " + progress;
    }

    public static ExportProgress getComplete(String id, Uri fileUri) {
      ExportProgress progress = new ExportProgress(id, EXPORT_COMPLETE, 0);
      progress.fileUri = fileUri;
      return progress;
    }

    public static ExportProgress fromThrowable(String id, Throwable throwable) {
      ExportProgress progress = new ExportProgress(id, ERROR, 0);
      progress.error = throwable;
      return progress;
    }
  }

  public static void resetProgress(String id) {
    progressSubject.onNext(new ExportProgress(id, ExportProgress.NOT_EXPORTING, 0));
  }

  public static Observable<ExportProgress> bind(Context context) {
    final Context appContext = context.getApplicationContext();
    final PublishSubject<ExportProgress> progressPublishSubject = PublishSubject.create();

    final ServiceConnection conn =
        new ServiceConnection() {

          @Override
          public void onServiceConnected(ComponentName name, IBinder service) {
            ExportServiceBinder exporter = (ExportServiceBinder) service;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
              Log.d(TAG, "binding service " + exporter);
            }
            exporter.getService().getProgressSubject().subscribe(progressPublishSubject);
          }

          @Override
          public void onServiceDisconnected(ComponentName name) {}
        };
    Intent intent = new Intent(appContext, ExportService.class);
    if (appContext.bindService(intent, conn, BIND_AUTO_CREATE)) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "trying to bind service.");
      }
    }

    return progressPublishSubject.doOnDispose(
        () -> {
          if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "unbinding service ");
          }
          appContext.unbindService(conn);
        });
  }

  /** Handle action export trial in the provided background thread with the provided parameters. */
  private void handleActionExportTrial(
      AppAccount appAccount,
      String experimentId,
      String trialId,
      boolean relativeTime,
      String[] sensorIds,
      int startId) {
    // Blocking gets OK: this is already background threaded.
    DataController dc = getDataController(appAccount).blockingGet();
    Experiment experiment = RxDataController.getExperimentById(dc, experimentId).blockingGet();
    Trial trial = experiment.getTrial(trialId);

    String fileName = makeCSVExportFilename(experiment.getDisplayTitle(this), trial.getTitle(this));
    // Start observing sensor data from here, while grouping them into timestamp equal rows.
    // Then write the rows out.
    Range<Long> range = Range.closed(trial.getFirstTimestamp(), trial.getLastTimestamp());
    dc.createScalarObservable(trialId, sensorIds, TimeRange.oldest(range), 0 /* resolution
        tier */)
        .doOnComplete(() -> stopSelf(startId))
        .observeOn(Schedulers.io())
        .subscribe(
            new TrialDataWriter(
                trialId,
                fileName,
                relativeTime,
                sensorIds,
                trial.getFirstTimestamp(),
                trial.getLastTimestamp()));
  }

  /**
   * Handle action export experiment in the provided background thread with the provided parameters.
   */
  private void handleActionExportExperiment(
      AppAccount appAccount, String experimentId, int startId) {
    // Blocking gets OK: this is already background threaded.
    DataController dc = getDataController(appAccount).blockingGet();
    Experiment experiment = RxDataController.getExperimentById(dc, experimentId).blockingGet();
    experiment.cleanTrials(this, appAccount);
    File file =
        FileMetadataUtil.getInstance()
            .getFileForExport(getApplicationContext(), appAccount, experiment, dc)
            .blockingGet();

    updateProgress(
        ExportProgress.getComplete(experimentId, getExperimentFileUri(appAccount, file.getName())));
  }

  /**
   * Handle action import experiment in the provided background thread with the provided parameters.
   */
  private void handleActionImportExperiment(AppAccount appAccount, Uri fileUri, int startId) {
    // Blocking gets OK: this is already background threaded.
    AppSingleton.getInstance(getApplicationContext())
        .getDataController(appAccount)
        .importExperimentFromZip(
            fileUri,
            getApplicationContext().getContentResolver(),
            new MaybeConsumer<String>() {
              @Override
              public void success(String experimentId) {
                updateProgress(ExportProgress.getComplete(experimentId, fileUri));
              }

              @Override
              public void fail(Exception e) {
                AppSingleton.getInstance(getApplicationContext()).setExportServiceBusy(false);
                if (e instanceof ZipException) {
                  showSnackbar(R.string.import_failed_file);
                  Log.e(TAG, "SJ file format exception", e);
                } else {
                  showSnackbar(R.string.import_failed);
                  Log.e(TAG, "Unknown import error", e);
                }
              }
            });
  }

  private void showSnackbar(int stringResource) {
    AppSingleton.getInstance(getApplicationContext())
        .onNextActivity()
        .subscribe(
            activity -> {
              AccessibilityUtils.makeSnackbar(
                      activity.findViewById(R.id.drawer_layout),
                      getResources().getString(stringResource),
                      Snackbar.LENGTH_SHORT)
                  .show();
            });
  }

  private Single<DataController> getDataController(AppAccount appAccount) {
    return DataService.bind(this)
        .map(
            appSingleton -> {
              return appSingleton.getDataController(appAccount);
            });
  }

  @NonNull
  @VisibleForTesting
  public static String makeCSVExportFilename(String experimentName, String trialName) {
    // 40 chars of experimentName + 35 chars of run title + " " + ".csv" = 80 chars
    return sanitizeFilename(truncate(experimentName, 40) + " " + truncate(trialName, 35) + ".csv");
  }

  @NonNull
  @VisibleForTesting
  public static String makeSJExportFilename(String experimentName) {
    // 70 chars of experimentName + ".sj"
    return sanitizeFilename(truncate(experimentName, 70) + ".sj");
  }


  public static String truncate(String string, int maxLength) {
    int hexLength = 8;
    if (string.length() < maxLength) {
      return string;
    }

    // Use a hash of the overflow characters to minimize odds of collision.
    int cutoff = maxLength - hexLength;
    String hexString = Integer.toHexString(string.substring(cutoff).hashCode());
    return string.substring(0, cutoff) + hexString;
  }

  static String sanitizeFilename(String inputName) {
    return inputName.replaceAll("[^ a-zA-Z0-9-_\\.]", "_");
  }

  private void updateProgress(ExportProgress exportProgress) {
    if (Log.isLoggable(TAG, Log.DEBUG)
        && (exportProgress.getState() != ExportProgress.EXPORTING
            || exportProgress.getProgress() % 20 == 0)) {
      Log.d(TAG, "Updating progress " + exportProgress + " from " + this);
    }
    progressSubject.onNext(exportProgress);
  }

  @NonNull
  private File getStorageDir() {
    return new File(getFilesDir().getPath(), "exported_run_files");
  }

  @NonNull
  private Uri getFileUri(String fileName) {
    return Uri.parse("content://" + getPackageName() + "/exported_runs/" + Uri.encode(fileName));
  }

  private Uri getExperimentFileUri(AppAccount appAccount, String fileName) {
    try {
      return FileProvider.getUriForFile(
          getApplicationContext(),
          getPackageName(),
          new File(
              FileMetadataUtil.getInstance().getExperimentExportDirectory(appAccount), fileName));
    } catch (IOException ioe) {
      Log.e(TAG, "Error getting export file", ioe);
      return null;
    }
  }

  /** Removes old exported run files on the IO thread and then stops when done. */
  private void handleCleanOldFiles(AppAccount appAccount, int startId) {
    Observable.just(startId)
        .observeOn(Schedulers.io())
        .doOnComplete(() -> stopSelf(startId))
        .subscribe(
            id -> {
              File storageDir = getStorageDir();
              deleteAllFiles(storageDir);

              File exportExperimentDir =
                  new File(FileMetadataUtil.getInstance().getExperimentExportDirectory(appAccount));
              deleteAllFiles(exportExperimentDir);
            });
  }

  private void deleteAllFiles(File exportExperimentDir) {
    if (exportExperimentDir.exists()) {
      for (File file : exportExperimentDir.listFiles()) {
        file.delete();
      }
    }
  }

  public static boolean canShare(Context context, AppAccount appAccount) {
    return appAccount.isSignedIn();
  }

  public static void handleExperimentExportClick(
      Context context, AppAccount appAccount, String experimentId, boolean saveLocally) {
    AppSingleton.getInstance(context)
        .getDataController(appAccount)
        .getExperimentById(
            experimentId,
            new LoggingConsumer<Experiment>(TAG, "load experiment with ID = " + experimentId) {
              @Override
              public void success(Experiment experiment) {
                if (isExperimentFullyDownloaded(appAccount, experimentId, experiment)) {
                  startExperimentExport(context, appAccount, experimentId, saveLocally);
                } else {
                  int messageId =
                      saveLocally
                          ? R.string.experiment_missing_data_message
                          : R.string.experiment_not_finished_downloading_message;
                  int positiveButtonId =
                      saveLocally
                          ? R.string.experiment_not_finished_downloading_confirm_download_button
                          : R.string.experiment_not_finished_downloading_confirm_button;
                  AlertDialog.Builder builder = new AlertDialog.Builder(context);
                  builder.setTitle(R.string.experiment_not_finished_downloading_title);
                  builder.setMessage(messageId);
                  builder.setPositiveButton(
                      positiveButtonId,
                      (DialogInterface dialog, int which) -> {
                        startExperimentExport(context, appAccount, experimentId, saveLocally);
                        dialog.dismiss();
                      });
                  builder.setNegativeButton(
                      R.string.experiment_not_finished_downloading_cancel_button,
                      (DialogInterface dialog, int which) -> {
                        AppSingleton.getInstance(context).setExportServiceBusy(false);
                        dialog.dismiss();
                      });
                  builder.setOnDismissListener(
                      (DialogInterface dialog) -> {
                        AppSingleton.getInstance(context).setExportServiceBusy(false);
                      });
                  builder.create().show();
                }
              }
            });
  }

  private static boolean isExperimentFullyDownloaded(
      AppAccount appAccount, String experimentId, Experiment experiment) {
    java.io.File experimentDirectory =
        FileMetadataUtil.getInstance().getExperimentDirectory(appAccount, experimentId);
    List<String> filesToVerify = new ArrayList<>();
    for (Trial t : experiment.getTrials()) {
      for (Label l : t.getLabels()) {
        if (l.getType() == GoosciLabel.Label.ValueType.PICTURE) {
          filesToVerify.add(l.getPictureLabelValue().getFilePath());
        }
      }
    }
    for (Label l : experiment.getLabels()) {
      if (l.getType() == GoosciLabel.Label.ValueType.PICTURE) {
        filesToVerify.add(l.getPictureLabelValue().getFilePath());
      }
    }
    filesToVerify.add(experiment.getImagePath());

    for (String imagePath : filesToVerify) {
      if (!Strings.isNullOrEmpty(imagePath)) {
        File file = new File(experimentDirectory, imagePath);
        if (!file.exists()) {
          return false;
        }
      }
    }
    return true;
  }

  private static void startExperimentExport(
      Context context, AppAccount appAccount, String experimentId, boolean saveLocally) {
    AppSingleton appSingleton = AppSingleton.getInstance(context);
    appSingleton.setExportServiceBusy(true);
    ExportService.bind(context)
        // Only look at events for this trial or the default value
        .filter(
            progress ->
                Objects.equals(progress.getId(), experimentId) || progress.getId().equals(""))
        .observeOn(AndroidSchedulers.mainThread())
        .filter(progress -> progress.getState() == ExportService.ExportProgress.EXPORT_COMPLETE)
        // Get just the next success
        .firstElement()
        .doOnSuccess(
            progress -> {
              // Reset the progress only after the UI has consumed this.
              ExportService.resetProgress(experimentId);
            })
        .subscribe(
            progress -> {
              Uri fileUri = progress.getFileUri();
              if (saveLocally) {
                appSingleton
                    .onNextActivity()
                    .subscribe(activity -> saveToDownloads(activity, fileUri));
              } else {
                appSingleton
                    .onNextActivity()
                    .subscribe(activity -> launchExportChooser(activity, appAccount, fileUri));
              }
            });

    ExportService.exportExperiment(context, appAccount, experimentId);
  }

  public static void launchExportChooser(Context context, AppAccount appAccount, Uri fileUri) {
    Intent shareIntent =
        FileMetadataUtil.getInstance().getShareIntent(context, appAccount, fileUri);
    AppSingleton.getInstance(context).setExportServiceBusy(false);
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getResources().getString(R.string.export_experiment_chooser_title)));
  }

  public static void handleExperimentImport(
      Context context, AppAccount appAccount, Uri experimentFile) {
    AppSingleton.getInstance(context).setExportServiceBusy(true);
    ExportService.bind(context)
        // Only look at events for this uri or the default value
        .filter(
            progress ->
                Objects.equals(progress.getFileUri(), experimentFile)
                    || progress.getId().equals(""))
        .observeOn(AndroidSchedulers.mainThread())
        .filter(progress -> progress.getState() == ExportService.ExportProgress.EXPORT_COMPLETE)
        // Get just the next success
        .firstElement()
        .doOnSuccess(
            progress -> {
              // Reset the progress only after the UI has consumed this.
              ExportService.resetProgress(experimentFile.toString());
            })
        .subscribe(
            progress -> {
              AppSingleton.getInstance(context).setExportServiceBusy(false);
              AppSingleton.getInstance(context)
                  .onNextActivity()
                  .subscribe(
                      activity -> {
                        activity.startActivity(
                            WhistlePunkApplication.getLaunchIntentForPanesActivity(
                                context,
                                appAccount,
                                progress.getId(),
                                false /* claimExperimentsMode */));
                      });
            });

    ExportService.importExperiment(context, appAccount, experimentFile);
  }

  /**
   * Downloads the file to the Downloads folder on the device
   *
   * @param context
   * @param fileUri The Uri of the file to download
   */
  public static void saveToDownloads(Context context, Uri fileUri) {
    if (Strings.isNullOrEmpty(fileUri.getPath())) {
      AppSingleton.getInstance(context).setExportServiceBusy(false);
      return;
    }

    if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
      File fileToDownload = new File(fileUri.getPath());
      File file = generateFileDestination(fileToDownload);
      copyToDownload(context, fileUri, fileToDownload.getName(), file);
    }
  }

  /** Enables caller to define what action to take when storage permissions are granted. */
  public interface DownloadPermissionListener {
    void onPermissionGranted();
  }

  public static void requestDownloadPermissions(
      DownloadPermissionListener listener,
      Activity activity,
      int snackbarView,
      String trackerCategory,
      String trackerLabel) {
    PermissionUtils.tryRequestingPermission(
        activity,
        PermissionUtils.REQUEST_WRITE_EXTERNAL_STORAGE,
        new PermissionUtils.PermissionListener() {
          @Override
          public void onPermissionGranted() {
            showSuccessSnackbar(activity, snackbarView);
            listener.onPermissionGranted();
          }

          @Override
          public void onPermissionDenied() {
            showErrorSnackbar(activity, snackbarView);
          }

          @Override
          public void onPermissionPermanentlyDenied() {
            showErrorSnackbar(activity, snackbarView);
          }

          private void showErrorSnackbar(Activity activity, int snackbarView) {
            if (!activity.isDestroyed()) {
              AccessibilityUtils.makeSnackbar(
                      activity.findViewById(snackbarView),
                      activity.getString(R.string.storage_permission_needed),
                      Snackbar.LENGTH_LONG)
                  .show();
            }
          }

          private void showSuccessSnackbar(Activity activity, int snackbarView) {
            if (!activity.isDestroyed()) {
              AccessibilityUtils.makeSnackbar(
                      activity.findViewById(snackbarView),
                      activity.getString(R.string.download_started),
                      Snackbar.LENGTH_LONG)
                  .show();
            }
          }
        });
    if (trackerCategory != null && trackerLabel != null) {
      WhistlePunkApplication.getUsageTracker(activity)
          .trackEvent(trackerCategory, TrackerConstants.ACTION_DOWNLOAD_REQUESTED, trackerLabel, 0);
    }
  }

  private static File generateFileDestination(File fileToDownload) {
    String fileName = fileToDownload.getName();
    File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    int i = 1;
    int indexOfExtension = fileName.lastIndexOf('.');
    String name = fileName.substring(0, indexOfExtension);
    String ext = fileName.substring(indexOfExtension);
    File destination = new File(directory, fileName);
    while (destination.exists()) {
      fileName = name + " (" + i + ")" + ext;
      destination = new File(directory, fileName);
      i++;
    }
    return destination;
  }

  private static String getMimeTypeFromFileName(String fileName) {
    String ext = fileName.substring(fileName.lastIndexOf('.'));
    if (ext.equals(".jpg")) {
      return "image/jpeg";
    } else if (ext.equals(".csv")) {
      return "text/csv";
    } else if (ext.equals(".sj")) {
      return "application/octet-stream";
    }
    return "";
  }

  @TargetApi(VERSION_CODES.O)
  private static void copyToDownload(
      Context context, Uri sourceUri, String fileName, File destination) {
    try {
      if (AndroidVersionUtils.isApiLevelAtLeastOreo()) {
        Files.copy(
            context.getContentResolver().openInputStream(sourceUri),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING);
      } else {
        UpdateExperimentFragment.copyUriToFile(context, sourceUri, destination);
        // Inform DownloadManager of completed download so the file shows in Downloads app
        DownloadManager downloadManager =
            (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadManager.addCompletedDownload(
            destination.getName(),
            destination.getName(),
            true,
            getMimeTypeFromFileName(fileName),
            destination.getAbsolutePath(),
            destination.length(),
            false);
      }
      Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
      PendingIntent pendingIntent =
          PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_ONE_SHOT);
      ((NotificationManager)
              context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE))
          .notify(
              NotificationIds.SAVED_TO_DEVICE,
              new NotificationCompat.Builder(
                      context.getApplicationContext(), NotificationChannels.SAVE_TO_DEVICE_CHANNEL)
                  .setContentTitle(fileName)
                  .setContentText(context.getString(R.string.saved_to_device_text))
                  .setSubText(context.getString(R.string.save_to_device_channel_description))
                  .setSmallIcon(R.drawable.ic_notification_24dp)
                  .setContentIntent(pendingIntent)
                  .setAutoCancel(true)
                  .build());
    } catch (IOException ioe) {
      AppSingleton.getInstance(context)
          .onNextActivity()
          .subscribe(
              activity -> {
                AccessibilityUtils.makeSnackbar(
                        activity.findViewById(android.R.id.content),
                        context.getResources().getString(R.string.saved_to_device_error),
                        Snackbar.LENGTH_SHORT)
                    .show();
              });
      ioe.printStackTrace();
      if (destination.exists()) {
        destination.delete();
      }
    } finally {
      AppSingleton.getInstance(context).setExportServiceBusy(false);
    }
  }

  private class TrialDataWriter implements Observer<ScalarReading> {

    private final long firstTimeStamp;
    private final long lastTimeStamp;
    private long currentTimestamp = -1;
    private long firstTimeStampWritten = -1;

    private ArrayMap<String, Double> currentRow = new ArrayMap<>();
    private OutputStreamWriter outputStreamWriter;
    private final String fileName;
    private final boolean relativeTime;
    private final String[] sensorIds;
    private final String trialId;

    public TrialDataWriter(
        String trialId,
        String fileName,
        boolean relativeTime,
        String[] sensorIds,
        long firstTimeStamp,
        long lastTimeStamp) {
      this.trialId = trialId;
      this.fileName = fileName;
      this.relativeTime = relativeTime;
      this.sensorIds = sensorIds;
      this.firstTimeStamp = firstTimeStamp;
      this.lastTimeStamp = lastTimeStamp;
    }

    @Override
    public void onSubscribe(Disposable disposable) {
      // Start writing stream.
      File storageDir = getStorageDir();

      // Create the storage directory if it does not exist
      if (!storageDir.exists()) {
        if (!storageDir.mkdirs()) {
          Log.e(TAG, "failed to create directory");
          onError(new IOException("Could not create dir " + storageDir.getAbsolutePath()));
          return;
        }
      }

      File file = new File(storageDir.getPath(), fileName);
      FileOutputStream fs;
      try {
        fs = new FileOutputStream(file);
      } catch (FileNotFoundException e) {
        onError(e);
        return;
      }

      outputStreamWriter = new OutputStreamWriter(fs);
      try {
        outputStreamWriter.write(relativeTime ? "relative_time" : "timestamp");
        // Loop through sensor IDs and output them here as column names.
        for (int index = 0, length = sensorIds.length; index < length; ++index) {
          outputStreamWriter.write(",");
          outputStreamWriter.write(sensorIds[index].replace(",", "_"));
        }
        outputStreamWriter.write("\n");
      } catch (IOException e) {
        onError(e);
        return;
      }
      updateProgress(new ExportProgress(trialId, ExportProgress.EXPORTING, 0));
    }

    @Override
    public void onNext(ScalarReading scalarReading) {
      // Check if we have a different timestamp than the current row.
      if (scalarReading.getCollectedTimeMillis() != currentTimestamp) {
        if (currentRow != null && currentTimestamp != -1) {
          writeRow();
        }
        currentRow.clear();
      }
      // If not, just add to current row.
      currentRow.put(scalarReading.getSensorTag(), scalarReading.getValue());
      if (currentTimestamp == -1) {
        firstTimeStampWritten = scalarReading.getCollectedTimeMillis();
      }
      currentTimestamp = scalarReading.getCollectedTimeMillis();
      int progress =
          (int)
              (((currentTimestamp - firstTimeStamp) / (double) (lastTimeStamp - firstTimeStamp))
                  * 100);
      updateProgress(new ExportProgress(trialId, ExportProgress.EXPORTING, progress));
    }

    @Override
    public void onError(Throwable throwable) {
      // End writing stream.
      closeStreamIfNecessary();
      updateProgress(ExportProgress.fromThrowable(trialId, throwable));
    }

    @Override
    public void onComplete() {
      // Write the last row if necessary.
      if (!currentRow.isEmpty()) {
        writeRow();
      }

      // End writing stream.
      closeStreamIfNecessary();
      updateProgress(ExportProgress.getComplete(trialId, getFileUri(fileName)));
    }

    private void writeRow() {
      // If so, if current row, then write it out (the old one)!
      try {
        if (outputStreamWriter == null) {
          onError(new IllegalStateException("Output stream closed."));
        }
        outputStreamWriter.write(getTimestampString(currentTimestamp));
        for (int index = 0, length = sensorIds.length; index < length; ++index) {
          String value = "";
          if (currentRow.containsKey(sensorIds[index])) {
            value = Double.toString(currentRow.get(sensorIds[index]));
          }
          outputStreamWriter.write(",");
          outputStreamWriter.write(value);
        }
        outputStreamWriter.write("\n");
      } catch (IOException e) {
        onError(e);
      }
    }

    private String getTimestampString(long time) {
      return Long.toString(relativeTime ? time - firstTimeStampWritten : time);
    }

    private void closeStreamIfNecessary() {
      if (outputStreamWriter != null) {
        try {
          outputStreamWriter.close();
        } catch (IOException e) {
          Log.e(TAG, "File close failed: " + e.toString());
          onError(e);
          return;
        } finally {
          outputStreamWriter = null;
        }
      }
    }
  }
}
