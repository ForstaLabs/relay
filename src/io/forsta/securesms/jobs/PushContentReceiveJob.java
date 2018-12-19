package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;

public class PushContentReceiveJob extends PushReceivedJob {

  private static final String TAG = PushContentReceiveJob.class.getSimpleName();
  private static final String KEY_DATA = "data";

  private String data;

  public PushContentReceiveJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public PushContentReceiveJob(Context context) {
    super(context, JobParameters.newBuilder().create());
    this.data = null;
  }

  public PushContentReceiveJob(Context context, String data) {
    super(context, JobParameters.newBuilder()
                                .create());

    this.data = data;
  }

  @Override
  public void onAdded() {}

  @Override
  protected void initialize(@NonNull SafeData data) {
    this.data = data.getString(KEY_DATA);
  }

  @Override
  protected @NonNull
  Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putString(KEY_DATA, data).build();
  }

  @Override
  public void onRun() {
    try {
      String                sessionKey = TextSecurePreferences.getSignalingKey(context);
      SignalServiceEnvelope envelope   = new SignalServiceEnvelope(data, sessionKey);

      handle(envelope, true);
    } catch (IOException | InvalidVersionException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }
}
