package io.forsta.securesms.jobs;

import android.content.Context;
import android.util.Log;


import io.forsta.securesms.dependencies.InjectableType;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

import io.forsta.securesms.dependencies.TextSecureCommunicationModule;

public class DeliveryReceiptJob extends ContextJob implements InjectableType {

  private static final String TAG = DeliveryReceiptJob.class.getSimpleName();

  @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  private final String destination;
  private final long   timestamp;
  private final String relay;

  public DeliveryReceiptJob(Context context, String destination, long timestamp, String relay) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withRetryCount(50)
                                .create());

    this.destination = destination;
    this.timestamp   = timestamp;
    this.relay       = relay;
  }

  @Override
  public void onAdded() {}

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
