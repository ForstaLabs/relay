package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.securesms.dependencies.InjectableType;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

import io.forsta.securesms.dependencies.TextSecureCommunicationModule;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;

public class DeliveryReceiptJob extends ContextJob implements InjectableType {

  private static final String TAG = DeliveryReceiptJob.class.getSimpleName();
  private static final String KEY_DESTINATION = "destination";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_RELAY = "relay";

  @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  private String destination;
  private long   timestamp;
  private String relay;

  public DeliveryReceiptJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public DeliveryReceiptJob(Context context, String destination, long timestamp, String relay) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withRetryCount(50)
                                .create());

    this.destination = destination;
    this.timestamp   = timestamp;
    this.relay       = relay;
  }

  @Override
  public void onAdded() {}

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder
        .putString(KEY_DESTINATION, destination)
        .putLong(KEY_TIMESTAMP, timestamp)
        .putString(KEY_RELAY, relay)
        .build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    destination = data.getString(KEY_DESTINATION);
    timestamp = data.getLong(KEY_TIMESTAMP);
    relay = data.getString(KEY_RELAY);
  }

  @Override
  public void onRun() throws IOException {
    Log.w("DeliveryReceiptJob", "Sending delivery receipt...");
    SignalServiceMessageSender messageSender     = messageSenderFactory.create();
    SignalServiceAddress       textSecureAddress = new SignalServiceAddress(destination, Optional.fromNullable(relay));

    messageSender.sendDeliveryReceipt(textSecureAddress, timestamp);
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to send receipt after retry exhausted!");
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    Log.w(TAG, exception);
    if (exception instanceof NonSuccessfulResponseCodeException) return false;
    if (exception instanceof PushNetworkException)               return true;

    return false;
  }
}
