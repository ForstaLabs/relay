package io.forsta.securesms.jobs;

import android.content.Context;
import android.util.Log;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.NoSuchMessageException;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.documents.NetworkFailure;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.mms.MediaConstraints;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.RecipientFormattingException;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.ExpiringMessageManager;
import io.forsta.securesms.transport.InsecureFallbackApprovalException;
import io.forsta.securesms.transport.RetryLaterException;
import io.forsta.securesms.transport.UndeliverableMessageException;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import io.forsta.securesms.dependencies.TextSecureCommunicationModule;
import io.forsta.securesms.util.TextSecurePreferences;
import ws.com.google.android.mms.MmsException;

public class PushMediaSendJob extends PushSendJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;

  public PushMediaSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    mmsDatabase.markAsSending(messageId);
    mmsDatabase.markAsPush(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws RetryLaterException, MmsException, IOException, NoSuchMessageException,
             UndeliverableMessageException
  {
    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage outgoingMessage = database.getOutgoingMessage(masterSecret, messageId);
    long threadId = database.getThreadIdForMessage(messageId);
    ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
    String distributionUniversal = thread.getDistribution();
    ForstaDistribution distribution = CcsmApi.getMessageDistribution(context, distributionUniversal);
    // This could potentially have new userIds because of tag updates. Update the thread recipients?

    List<OutgoingMediaMessage> messageQueue = new ArrayList<>();
    messageQueue.add(outgoingMessage);
    if (distribution.hasMonitors()) {
      Recipients monitors = RecipientFactory.getRecipientsFromStrings(context, distribution.getMonitors(context), false);
      OutgoingMediaMessage monitorMessage = new OutgoingMediaMessage(monitors, outgoingMessage.getBody(), outgoingMessage.getAttachments(), System.currentTimeMillis(), -1, 0, ThreadDatabase.DistributionTypes.CONVERSATION);
      messageQueue.add(monitorMessage);
    }

    for (OutgoingMediaMessage message : messageQueue) {
      try {
        deliver(masterSecret, message, message.getRecipients());
        database.markAsPush(messageId);
        database.markAsSecure(messageId);
        database.markAsSent(messageId);
        markAttachmentsUploaded(messageId, message.getAttachments());

        if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
          database.markExpireStarted(messageId);
          expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
        }
      } catch (EncapsulatedExceptions e) {
        Log.w(TAG, e);
        List<NetworkFailure> failures = new LinkedList<>();
        for (NetworkFailureException nfe : e.getNetworkExceptions()) {
          Recipient recipient = RecipientFactory.getRecipientsFromString(context, nfe.getE164number(), false).getPrimaryRecipient();
          failures.add(new NetworkFailure(recipient.getRecipientId()));
        }

        List<String> untrustedRecipients = new ArrayList<>();
        for (UntrustedIdentityException uie : e.getUntrustedIdentityExceptions()) {
          untrustedRecipients.add(uie.getE164Number());
          acceptIndentityKey(uie);
        }

        if (untrustedRecipients.size() > 0) {
          Recipients failedRecipients = RecipientFactory.getRecipientsFromStrings(context, untrustedRecipients, false);
          try {
            deliver(masterSecret, message, failedRecipients);
          } catch (InvalidNumberException | EncapsulatedExceptions | RecipientFormattingException e1) {
            e1.printStackTrace();
          }
        }

        if (e.getUnregisteredUserExceptions().size() > 0) {
          for (UnregisteredUserException uue : e.getUnregisteredUserExceptions()) {
            Log.w(TAG, "Unregistered User: " + uue.getE164Number());
          }
        }

        database.addFailures(messageId, failures);
        database.markAsPush(messageId);

        if (e.getNetworkExceptions().isEmpty()) {
          database.markAsSecure(messageId);
          database.markAsSent(messageId);
          markAttachmentsUploaded(messageId, message.getAttachments());
        } else {
          database.markAsSentFailed(messageId);
          notifyMediaMessageDeliveryFailed(context, messageId);
        }
      } catch (InvalidNumberException | RecipientFormattingException | UndeliverableMessageException e) {
        Log.w(TAG, e);
        database.markAsSentFailed(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);
      } catch (IllegalStateException e) {
        Log.w(TAG, "Something went wildly wrong: " +  e.getMessage());
      } catch (Exception e) {
        Log.w(TAG, "General exception: " + e.getMessage());
      }
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof RequirementNotMetException) return true;
    if (exception instanceof RetryLaterException)        return true;

    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }

  private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message, Recipients recipients)
      throws IOException, RecipientFormattingException, InvalidNumberException,
      EncapsulatedExceptions, UndeliverableMessageException
  {
    if (recipients == null || recipients.getPrimaryRecipient() == null || recipients.getPrimaryRecipient().getNumber() == null) {
      throw new UndeliverableMessageException("No destination address.");
    }

    SignalServiceMessageSender messageSender = messageSenderFactory.create();
    SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(masterSecret, message);
    List<SignalServiceAddress> addresses = getPushAddresses(recipients);
    Log.w(TAG, "Sending message: " + messageId);
    messageSender.sendMessage(addresses, mediaMessage);
  }

  private void acceptIndentityKey(UntrustedIdentityException uie) {
    Log.w(TAG, uie);
    Log.w(TAG, "Media Message. Auto handling untrusted identity.");
    Recipients recipients  = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
    long recipientId = recipients.getPrimaryRecipient().getRecipientId();
    IdentityKey identityKey    = uie.getIdentityKey();
    DatabaseFactory.getIdentityDatabase(context).saveIdentity(recipientId, identityKey);
  }

  private SignalServiceDataMessage createSignalServiceDataMessage(MasterSecret masterSecret, OutgoingMediaMessage message) throws UndeliverableMessageException {
    List<Attachment>              scaledAttachments = scaleAttachments(masterSecret, MediaConstraints.PUSH_CONSTRAINTS, message.getAttachments());
    List<SignalServiceAttachment> attachmentStreams = getAttachmentsFor(masterSecret, scaledAttachments);
    SignalServiceDataMessage      mediaMessage      = SignalServiceDataMessage.newBuilder()
        .withBody(message.getBody())
        .withAttachments(attachmentStreams)
        .withTimestamp(message.getSentTimeMillis())
        .withExpiration((int)(message.getExpiresIn() / 1000))
        .asExpirationUpdate(message.isExpirationUpdate())
        .build();
    return mediaMessage;
  }

  private List<SignalServiceAddress> getPushAddresses(Recipients recipients) throws InvalidNumberException {
    List<SignalServiceAddress> addresses = new LinkedList<>();

    for (Recipient recipient : recipients.getRecipientsList()) {
      String localUid = TextSecurePreferences.getLocalNumber(context);
      if (!localUid.equals(recipient.getNumber())) {
        addresses.add(getPushAddress(recipient.getNumber()));
      }
    }

    return addresses;
  }
}
