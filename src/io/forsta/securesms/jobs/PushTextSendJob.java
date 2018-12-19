package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.EncryptingSmsDatabase;
import io.forsta.securesms.database.NoSuchMessageException;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.model.SmsMessageRecord;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.ExpiringMessageManager;
import io.forsta.securesms.transport.InsecureFallbackApprovalException;
import io.forsta.securesms.transport.RetryLaterException;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

import javax.inject.Inject;

import io.forsta.securesms.dependencies.TextSecureCommunicationModule;

public class PushTextSendJob extends PushSendJob implements InjectableType {

  private static final long serialVersionUID = 1L;
  private static final String TAG = PushTextSendJob.class.getSimpleName();
  private static final String KEY_MESSAGE_ID = "message_id";

  @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  private long messageId;

  public PushTextSendJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public PushTextSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);
    smsDatabase.markAsSending(messageId);
    smsDatabase.markAsPush(messageId);
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    messageId = data.getLong(KEY_MESSAGE_ID);
  }

  @Override
  protected @NonNull
  Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_MESSAGE_ID, messageId).build();
  }

  @Override
  public void onSend(MasterSecret masterSecret) throws NoSuchMessageException, RetryLaterException {
    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    EncryptingSmsDatabase  database          = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsMessageRecord record            = database.getMessage(masterSecret, messageId);

    try {
      Log.w(TAG, "Sending message: " + messageId);
      processMessage(database, record, expirationManager);
    } catch (InsecureFallbackApprovalException e) {
      Log.w(TAG, e);
      // XXX For now, there should be no insecure messages. Notify user by checking Recipients cache before submitting.
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      Log.w(TAG, "Text message. Auto handling untrusted identity");
      Recipients recipients  = RecipientFactory.getRecipientsFromString(context, e.getE164Number(), false);
      long       recipientId = recipients.getPrimaryRecipient().getRecipientId();
      IdentityKey identityKey    = e.getIdentityKey();
      DatabaseFactory.getIdentityDatabase(context).saveIdentity(recipientId, identityKey);
      try {
        processMessage(database, record, expirationManager);
      } catch (UntrustedIdentityException e1) {
        e1.printStackTrace();
      } catch (InsecureFallbackApprovalException e1) {
        e1.printStackTrace();
      }
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof RetryLaterException) return true;

    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);

    long       threadId   = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    if (threadId != -1 && recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }

  private void deliver(SmsMessageRecord message)
      throws UntrustedIdentityException, InsecureFallbackApprovalException, RetryLaterException
  {
    try {
      SignalServiceAddress       address           = getPushAddress(message.getIndividualRecipient().getAddress());
      Log.d(TAG, "Sending message...");
      Log.d(TAG, address.getNumber());
      Log.d(TAG, address.getRelay().toString());
      Log.d(TAG, message.getDisplayBody().toString());
      SignalServiceMessageSender messageSender     = messageSenderFactory.create();
      SignalServiceDataMessage   textSecureMessage = SignalServiceDataMessage.newBuilder()
                                                                             .withTimestamp(message.getDateSent())
                                                                             .withBody(message.getBody().getBody())
                                                                             .withExpiration((int)(message.getExpiresIn() / 1000))
                                                                             .asEndSessionMessage(message.isEndSession())
                                                                             .build();

      // TODO modify this send to allow multiple recipients? See PushMediaSendJob.
      messageSender.sendMessage(address, textSecureMessage);
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      throw new InsecureFallbackApprovalException(e);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new RetryLaterException(e);
    }
  }

  private void processMessage(SmsDatabase database, SmsMessageRecord record, ExpiringMessageManager expirationManager)
      throws RetryLaterException, UntrustedIdentityException, InsecureFallbackApprovalException {
    deliver(record);
    database.markAsPush(messageId);
    database.markAsSecure(messageId);
    database.markAsSent(messageId);

    if (record.getExpiresIn() > 0) {
      database.markExpireStarted(messageId);
      expirationManager.scheduleDeletion(record.getId(), record.isMms(), record.getExpiresIn());
    }
  }
}
