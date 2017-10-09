package io.forsta.securesms.jobs;

import android.content.Context;
import android.util.Log;

import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.NoSuchMessageException;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.mms.MediaConstraints;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
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
      throws RetryLaterException, MmsException, NoSuchMessageException,
             UndeliverableMessageException
  {
    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MmsDatabase            database          = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage message           = database.getOutgoingMessage(masterSecret, messageId);

    try {
      Log.w(TAG, "Sending message: " + messageId);
      processMessage(masterSecret, database, message, expirationManager);
    } catch (InsecureFallbackApprovalException ifae) {
      Log.w(TAG, ifae);
      // XXX For now, there should be no insecure messages. Notify user by checking Recipients cache before submitting.
    } catch (UntrustedIdentityException uie) {
      Log.w(TAG, uie);
      Log.w(TAG, "Media Message. Auto handling untrusted identity, Single recipient.");
      Recipients recipients  = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
      long       recipientId = recipients.getPrimaryRecipient().getRecipientId();
      IdentityKey identityKey    = uie.getIdentityKey();
      DatabaseFactory.getIdentityDatabase(context).saveIdentity(recipientId, identityKey);
      try {
        // Message was not sent to any recipients. Reprocess.
        processMessage(masterSecret, database, message, expirationManager);
      } catch (InsecureFallbackApprovalException e) {
        e.printStackTrace();
      } catch (EncapsulatedExceptions encapsulatedExceptions) {
        encapsulatedExceptions.printStackTrace();
      } catch (UntrustedIdentityException e) {
        e.printStackTrace();
      }
    } catch (EncapsulatedExceptions ee) {
      handleMultipleUntrustedIdentities(ee, masterSecret, message);
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

  private void deliver(MasterSecret masterSecret, OutgoingMediaMessage message)
      throws RetryLaterException, InsecureFallbackApprovalException, UntrustedIdentityException,
             UndeliverableMessageException, EncapsulatedExceptions
  {
    if (message.getRecipients() == null                       ||
        message.getRecipients().getPrimaryRecipient() == null ||
        message.getRecipients().getPrimaryRecipient().getNumber() == null)
    {
      throw new UndeliverableMessageException("No destination address.");
    }

    SignalServiceMessageSender messageSender = messageSenderFactory.create();

    try {
      SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(masterSecret, message);

      if (!message.getRecipients().isSingleRecipient()) {
        List<SignalServiceAddress> addresses = getPushAddresses(message.getRecipients());
        messageSender.sendMessage(addresses, mediaMessage);
      } else {
        SignalServiceAddress address = getPushAddress(message.getRecipients().getPrimaryRecipient().getNumber());
        messageSender.sendMessage(address, mediaMessage);
      }
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      throw new InsecureFallbackApprovalException(e);
    } catch (FileNotFoundException e) {
      Log.w(TAG, e);
      throw new UndeliverableMessageException(e);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new RetryLaterException(e);
    } catch (Exception e) {
      Log.e(TAG, "Fatal message send exception." + e.getMessage());
    }
  }

  private void processMessage(MasterSecret masterSecret, MmsDatabase database, OutgoingMediaMessage message, ExpiringMessageManager expirationManager)
      throws EncapsulatedExceptions, RetryLaterException, UndeliverableMessageException, UntrustedIdentityException, InsecureFallbackApprovalException {
    deliver(masterSecret, message);
    database.markAsPush(messageId);
    database.markAsSecure(messageId);
    database.markAsSent(messageId);
    markAttachmentsUploaded(messageId, message.getAttachments());

    if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
      database.markExpireStarted(messageId);
      expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
    }
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

  private void handleMultipleUntrustedIdentities(EncapsulatedExceptions ee, MasterSecret masterSecret, OutgoingMediaMessage message) {
    Log.w(TAG, "Media message. Auto handling untrusted identity. Multiple recipients.");
    if (ee.getUntrustedIdentityExceptions().size() > 0) {
      List<UntrustedIdentityException> untrustedIdentities = ee.getUntrustedIdentityExceptions();
      List<String> untrustedRecipients = new ArrayList<>();
      for (UntrustedIdentityException uie : untrustedIdentities) {
        untrustedRecipients.add(uie.getE164Number());
        Recipients identityRecipients = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
        long uieRecipientId = identityRecipients.getPrimaryRecipient().getRecipientId();
        IdentityKey identityKey    = uie.getIdentityKey();
        DatabaseFactory.getIdentityDatabase(context).saveIdentity(uieRecipientId, identityKey);
      }

      try {
        // Resend message to each untrusted identity.
        if (untrustedRecipients.size() > 0) {
          Recipients resendRecipients = RecipientFactory.getRecipientsFromStrings(context, untrustedRecipients, false);
          List<SignalServiceAddress> addresses = getPushAddresses(resendRecipients);
          SignalServiceMessageSender messageSender = messageSenderFactory.create();
          SignalServiceDataMessage dataMessage = createSignalServiceDataMessage(masterSecret, message);
          messageSender.sendMessage(addresses, dataMessage);
        }
      } catch (EncapsulatedExceptions encapsulatedExceptions) {
        encapsulatedExceptions.printStackTrace();
      } catch (InvalidNumberException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (UndeliverableMessageException e) {
        e.printStackTrace();
      }
    }
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
