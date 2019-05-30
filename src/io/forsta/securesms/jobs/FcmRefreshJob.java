/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.securesms.jobs;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import androidx.work.WorkerParameters;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.PlayServicesProblemActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.gcm.FcmUtil;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.notifications.NotificationChannels;
import io.forsta.securesms.transport.RetryLaterException;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.jobmanager.JobParameters;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;

import javax.inject.Inject;
import androidx.work.Data;


public class FcmRefreshJob extends ContextJob implements InjectableType {

  private static final String TAG = FcmRefreshJob.class.getSimpleName();

  @Inject transient ForstaServiceAccountManager textSecureAccountManager;

  public FcmRefreshJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public FcmRefreshJob(Context context) {
    super(context, JobParameters.newBuilder()
        .withGroupId(FcmRefreshJob.class.getSimpleName())
        .withDuplicatesIgnored(true)
        .withNetworkRequirement()
        .withRetryCount(1)
        .create());
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  public void onRun() throws Exception {
    if (TextSecurePreferences.isFcmDisabled(context)) return;

    Log.i(TAG, "Reregistering FCM...");

    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);

    if (result != ConnectionResult.SUCCESS) {
      notifyFcmFailure();
    } else {
      Optional<String> token = FcmUtil.getToken();

      if (token.isPresent()) {
        textSecureAccountManager.setGcmId(token);
        TextSecurePreferences.setFcmToken(context, token.get());
        TextSecurePreferences.setFcmTokenLastSetTime(context, System.currentTimeMillis());
        TextSecurePreferences.setWebsocketRegistered(context, true);
      } else {
        throw new RetryLaterException(new IOException("Failed to retrieve a token."));
      }
    }
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "GCM reregistration failed after retry attempt exhaustion!");
  }

  @Override
  public boolean onShouldRetry(Exception throwable) {
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    return true;
  }

  private void notifyFcmFailure() {
    Intent                     intent        = new Intent(context, PlayServicesProblemActivity.class);
    PendingIntent              pendingIntent = PendingIntent.getActivity(context, 1122, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    NotificationCompat.Builder builder       = new NotificationCompat.Builder(context, NotificationChannels.FAILURES);

    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
        R.drawable.ic_action_warning_red));
    builder.setContentTitle(context.getString(R.string.GcmRefreshJob_Permanent_Signal_communication_failure));
    builder.setContentText(context.getString(R.string.GcmRefreshJob_Signal_was_unable_to_register_with_Google_Play_Services));
    builder.setTicker(context.getString(R.string.GcmRefreshJob_Permanent_Signal_communication_failure));
    builder.setVibrate(new long[] {0, 1000});
    builder.setContentIntent(pendingIntent);

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(12, builder.build());
  }

}