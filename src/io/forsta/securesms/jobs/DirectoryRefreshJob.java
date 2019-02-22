package io.forsta.securesms.jobs;

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.SecurityEvent;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DirectoryRefreshJob extends ContextJob {
  private static String TAG = DirectoryRefreshJob.class.getSimpleName();

  @Nullable private transient Recipients recipients;
  @Nullable private transient MasterSecret masterSecret;

  public DirectoryRefreshJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public DirectoryRefreshJob(@NonNull Context context) {
    this(context, null, null);
  }

  public DirectoryRefreshJob(@NonNull Context context,
                             @Nullable MasterSecret masterSecret,
                             @Nullable Recipients recipients)
  {
    super(context, JobParameters.newBuilder()
                                .withGroupId(DirectoryRefreshJob.class.getSimpleName())
                                .withNetworkRequirement()
                                .create());

    this.recipients   = recipients;
    this.masterSecret = masterSecret;
  }

  @Override
  public void onAdded() {}

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {

  }

  @Override
  public void onRun() throws IOException {
    Log.w(TAG, "DirectoryRefreshJob.onRun()");
    PowerManager          powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock     = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Directory Refresh");

    try {
      wakeLock.acquire();
      if (recipients == null) {
        DirectoryHelper.refreshDirectory(context);
      } else {
        DirectoryHelper.refreshDirectoryFor(context, masterSecret, recipients);
      }
    } finally {
      if (wakeLock.isHeld()) wakeLock.release();
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {}
}
