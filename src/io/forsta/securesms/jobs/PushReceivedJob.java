package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.WorkerParameters;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MessagingDatabase.SyncMessageId;
import io.forsta.securesms.jobmanager.JobManager;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public abstract class PushReceivedJob extends ContextJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  public static final Object RECEIVE_LOCK = new Object();

  protected PushReceivedJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  protected PushReceivedJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  public void handle(SignalServiceEnvelope envelope, boolean sendExplicitReceipt) {
    synchronized (RECEIVE_LOCK) {
      if (envelope.isReceipt()) {
        Log.w(TAG, "Received delivery receipt");
        handleReceipt(envelope);
      } else if (envelope.isPreKeySignalMessage() || envelope.isSignalMessage()) {
        handleMessage(envelope, sendExplicitReceipt);
      } else {
        Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
      }
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope, boolean sendExplicitReceipt) {
    Recipients sender = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

    if (!sender.isBlocked()) {
      long messageId = DatabaseFactory.getPushDatabase(context).insert(envelope);
      jobManager.add(new PushDecryptJob(context, messageId, envelope.getSource()));
    } else {
      Log.w(TAG, "*** Received blocked push message, ignoring...");
    }

    if (sendExplicitReceipt) {
      jobManager.add(new DeliveryReceiptJob(context, envelope.getSource(),
                                            envelope.getTimestamp(),
                                            envelope.getRelay()));
    }
  }

  private void handleReceipt(SignalServiceEnvelope envelope) {
    DatabaseFactory.getMmsDatabase(context).incrementDeliveryReceiptCount(new SyncMessageId(envelope.getSource(),
                                                                                               envelope.getTimestamp()));
  }
}
