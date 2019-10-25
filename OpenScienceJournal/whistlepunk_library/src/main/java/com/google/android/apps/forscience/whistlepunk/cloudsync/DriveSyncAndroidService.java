package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LocalSyncManager;
import com.google.android.material.snackbar.Snackbar;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.common.base.Throwables;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * An Android service that can run in the background in order to sync Science Journal experiments
 * and experiment libraries to Google Drive. If the Science Journal application closes while sync is
 * in progress, the service will survive and complete the sync action. This also lets us ensure that
 * network traffic doesn't block the SJ application itself.
 */
public class DriveSyncAndroidService extends Service {
  private static final String TAG = "DriveSyncAndroidService";

  private static final String ACTION_SYNC_LIBRARY =
      "com.google.android.apps.forscience.whistlepunk.action.SYNC_LIBRARY";

  private static final String ACTION_SYNC_EXPERIMENT_PROTO =
      "com.google.android.apps.forscience.whistlepunk.action.SYNC_EXPERIMENT_PROTO";

  private static final String EXTRA_ACCOUNT_KEY =
      "com.google.android.apps.forscience.whistlepunk.extra.ACCOUNT_KEY";

  private static final String EXTRA_EXPERIMENT_ID =
      "com.google.android.apps.forscience.whistlepunk.extra.EXPERIMENT_ID";

  private static final String EXPERIMENT_PROTO = "experiment_library.proto";

  private static final String REASON_USER_RATE_LIMIT_EXCEEDED = "userRateLimitExceeded";

  private static final BehaviorSubject<DriveSyncProgress> progressSubject =
      BehaviorSubject.createDefault(new DriveSyncProgress("", DriveSyncProgress.NOT_SYNCING, 0));

  private final IBinder binder = new DriveSyncAndroidServiceBinder();

  private volatile Looper serviceLooper;
  private volatile DriveSyncAndroidService.ServiceHandler serviceHandler;

  private final class ServiceHandler extends Handler {
    public ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      onHandleIntent((Intent) msg.obj, msg.arg1);
    }
  }

  /** Guice binder for DriveSync service. */
  public class DriveSyncAndroidServiceBinder extends Binder {
    DriveSyncAndroidService getService() {
      return DriveSyncAndroidService.this;
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    HandlerThread thread = new HandlerThread("DriveSyncAndroidService");
    thread.start();
    serviceLooper = thread.getLooper();
    serviceHandler = new DriveSyncAndroidService.ServiceHandler(serviceLooper);
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
   * Starts this service to perform action sync experiment library file with the given parameters,
   * unless the given account is not signed in or a recording is happening.
   * If the service is already performing a task this action will be queued.
   * @return true if the sync service is started, false otherwise.
   */
  public static boolean syncExperimentLibraryFile(Context context, AppAccount appAccount) {
    if (!appAccount.isSignedIn()) {
      return false;
    }
    // This will never actually block, as recordingStatus always has a value, from creation time.
    if (AppSingleton.getInstance(context)
        .getRecorderController(appAccount)
        .watchRecordingStatus()
        .blockingFirst()
        .isRecording()) {
      return false;
    }
    Intent intent = new Intent(context, DriveSyncAndroidService.class);
    intent.setAction(ACTION_SYNC_LIBRARY);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    return startService(context, intent, TrackerConstants.ACTION_SYNC_EXPERIMENT_LIBRARY_FILE);
  }

  /**
   * Starts this service to perform action sync experiment proto file with the given parameters. If
   * the service is already performing a task this action will be queued.
   */
  public static void syncExperimentProtoFile(
      Context context, String experimentId, AppAccount appAccount) {
    // This will never actually block, as recordingStatus always has a value, from creation time.
    if (AppSingleton.getInstance(context)
        .getRecorderController(appAccount)
        .watchRecordingStatus()
        .blockingFirst()
        .isRecording()) {
      return;
    }
    Intent intent = new Intent(context, DriveSyncAndroidService.class);
    intent.setAction(ACTION_SYNC_EXPERIMENT_PROTO);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    startService(context, intent, TrackerConstants.ACTION_SYNC_EXPERIMENT_PROTO_FILE);
  }

  private static boolean startService(Context context, Intent intent, String trackerAction) {
    try {
      context.getApplicationContext().startService(intent);
      return true;
    } catch (IllegalStateException e) {
      // The application is in a state where the service can not be started (such as not in the
      // foreground in a state when services are allowed).
      String labelFromStackTrace = TrackerConstants.createLabelFromStackTrace(e);
      WhistlePunkApplication.getUsageTracker(context)
          .trackEvent(TrackerConstants.CATEGORY_FAILURE, trackerAction, labelFromStackTrace, 0);
      return false;
    }
  }

  private AppAccount getAppAccount(Intent intent) {
    return WhistlePunkApplication.getAccount(getApplicationContext(), intent, EXTRA_ACCOUNT_KEY);
  }

  private DriveSyncManager getDriveSyncService(Intent intent) {
    return (DriveSyncManager)
        WhistlePunkApplication.getCloudSyncProvider(getApplicationContext())
            .getServiceForAccount(getAppAccount(intent));
  }

  private void onHandleIntent(Intent intent, int startId) {
    if (intent != null) {
      final String action = intent.getAction();
      DriveSyncManager driveApi = getDriveSyncService(intent);
      AppAccount account = getAppAccount(intent);
      AppSingleton appSingleton = AppSingleton.getInstance(getApplicationContext());
      ExperimentLibraryManager elm = appSingleton.getExperimentLibraryManager(account);
      LocalSyncManager lsm = appSingleton.getLocalSyncManager(account);
      if (ACTION_SYNC_LIBRARY.equals(action)) {
        handleActionSyncLibrary(driveApi, intent.getStringExtra(EXTRA_ACCOUNT_KEY), elm, lsm);
      } else if (ACTION_SYNC_EXPERIMENT_PROTO.equals(action)) {
        handleActionSyncExperimentProtoFile(
            driveApi,
            intent.getStringExtra(EXTRA_ACCOUNT_KEY),
            intent.getStringExtra(EXTRA_EXPERIMENT_ID),
            elm,
            lsm);
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

  public BehaviorSubject<DriveSyncProgress> getProgressSubject() {
    return progressSubject;
  }

  /** Class for reporting progress of DriveSync actions to calling code. */
  public static class DriveSyncProgress {
    public static final int NOT_SYNCING = 0;
    public static final int ERROR = 1;
    public static final int SYNCING = 2;
    public static final int SYNC_COMPLETE = 3;

    private final String id;
    private final int state;
    private final int progress;

    private Throwable error;

    // id should be a concatenation of a UUID, like accountKey or ExperimentId, and a file name.
    public DriveSyncProgress(String id, int state, int progress) {
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

    @Override
    public String toString() {
      return "State: " + state + " progress " + progress;
    }

    public static DriveSyncProgress getComplete(String id) {
      DriveSyncProgress progress = new DriveSyncProgress(id, SYNC_COMPLETE, 0);
      return progress;
    }

    public static DriveSyncProgress fromThrowable(String id, Throwable throwable) {
      DriveSyncProgress progress = new DriveSyncAndroidService.DriveSyncProgress(id, ERROR, 0);
      progress.error = throwable;
      return progress;
    }

    public static void resetProgress(String id) {
      progressSubject.onNext(new DriveSyncProgress(id, DriveSyncProgress.NOT_SYNCING, 0));
    }
  }

  public static Observable<DriveSyncProgress> bind(Context context) {
    final Context appContext = context.getApplicationContext();
    final PublishSubject<DriveSyncProgress> progressPublishSubject = PublishSubject.create();

    final ServiceConnection conn =
        new ServiceConnection() {

          @Override
          public void onServiceConnected(ComponentName name, IBinder service) {
            DriveSyncAndroidServiceBinder exporter = (DriveSyncAndroidServiceBinder) service;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
              Log.d(TAG, "binding service " + exporter);
            }
            exporter.getService().getProgressSubject().subscribe(progressPublishSubject);
          }

          @Override
          public void onServiceDisconnected(ComponentName name) {}
        };

    Intent intent = new Intent(appContext, DriveSyncAndroidService.class);
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

  /**
   * Handle action sync experiment library in the provided background thread with the provided
   * parameters.
   */
  private void handleActionSyncLibrary(
      DriveSyncManager driveApi,
      String accountKey,
      ExperimentLibraryManager elm,
      LocalSyncManager lsm) {
    String progressKey = accountKey + EXPERIMENT_PROTO;
    try {
      driveApi.syncExperimentLibraryInBackgroundThread(getApplicationContext(), elm, lsm);
      updateProgress(DriveSyncProgress.getComplete(progressKey));
    } catch (UserRecoverableAuthIOException e) {
      updateProgress(DriveSyncProgress.fromThrowable(progressKey, e));
      Intent intent = e.getIntent();
      // Need to start a new task, since we're calling this from a service
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } catch (UnknownHostException uhe) {
      updateProgress(DriveSyncProgress.fromThrowable(progressKey, uhe));
      AppSingleton.getInstance(getApplicationContext()).setSyncServiceBusy(false);
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "UnknownHost Exception", uhe);
      }
    } catch (IOException ioe) {
      Log.e(TAG, ioe.getClass().toString());
      updateProgress(DriveSyncProgress.fromThrowable(progressKey, ioe));
      AppSingleton.getInstance(getApplicationContext()).setSyncServiceBusy(false);
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "IO Exception", ioe);
      }
      String labelFromStackTrace = TrackerConstants.createLabelFromStackTrace(ioe);
      UsageTracker usageTracker = WhistlePunkApplication.getUsageTracker(getApplicationContext());
      usageTracker.trackEvent(
          TrackerConstants.CATEGORY_SYNC,
          TrackerConstants.ACTION_SYNC_FAILED,
          labelFromStackTrace,
          0);
      if (Throwables.getStackTraceAsString(ioe).contains(REASON_USER_RATE_LIMIT_EXCEEDED)) {
        usageTracker.trackEvent(
            TrackerConstants.CATEGORY_FAILURE,
            TrackerConstants.ACTION_SYNC_FAILED_USER_RATE_LIMIT_EXCEEDED,
            labelFromStackTrace,
            0);
      } else {
        usageTracker.trackEvent(
            TrackerConstants.CATEGORY_FAILURE,
            TrackerConstants.ACTION_SYNC_FAILED,
            labelFromStackTrace,
            0);
      }
      makeSnackbar(R.string.sync_failed);
    }
  }

  @SuppressLint("CheckResult")
  private void makeSnackbar(int stringRes) {
    AppSingleton.getInstance(getApplicationContext())
        .onNextActivity()
        .subscribe(
            activity -> {
              AccessibilityUtils.makeSnackbar(
                      activity.findViewById(R.id.drawer_layout),
                      getResources().getString(stringRes),
                      Snackbar.LENGTH_SHORT)
                  .show();
            });
  }

  /**
   * Handle action sync experiment in the provided background thread with the provided parameters.
   */
  private void handleActionSyncExperimentProtoFile(
      DriveSyncManager driveApi,
      String accountKey,
      String experimentId,
      ExperimentLibraryManager elm,
      LocalSyncManager lsm) {
    // Blocking gets OK: this is already background threaded.
    String progressKey = accountKey + experimentId;
    try {
      driveApi.syncExperimentProtoFileInBackgroundThread(
          getApplicationContext(), experimentId, -1, elm, lsm);
      updateProgress(DriveSyncProgress.getComplete(progressKey));
    } catch (IOException ioe) {
      updateProgress(DriveSyncProgress.fromThrowable(progressKey, ioe));
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "IO Exception", ioe);
      }
    }
  }

  private void updateProgress(DriveSyncProgress syncProgress) {
    if (Log.isLoggable(TAG, Log.DEBUG)
        && (syncProgress.getState() != DriveSyncProgress.SYNCING
            || syncProgress.getProgress() % 20 == 0)) {
      Log.d(TAG, "Updating progress " + syncProgress + " from " + this);
    }
    progressSubject.onNext(syncProgress);
    if (syncProgress.getState() != DriveSyncProgress.SYNCING) {
      notifyNewExperimentSynced();
    }
  }

  private void notifyNewExperimentSynced() {
    AppSingleton.getInstance(getApplicationContext()).notifyNewExperimentSynced();
  }
}
