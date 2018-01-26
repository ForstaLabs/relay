package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.ccsm.util.InvalidMessagePayloadException;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.DeviceActivity;
import io.forsta.securesms.attachments.DatabaseAttachment;
import io.forsta.securesms.attachments.PointerAttachment;
import io.forsta.securesms.crypto.IdentityKeyUtil;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.crypto.MasterSecretUtil;
import io.forsta.securesms.crypto.SecurityEvent;
import io.forsta.securesms.crypto.storage.SignalProtocolStoreImpl;
import io.forsta.securesms.crypto.storage.TextSecureSessionStore;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.EncryptingSmsDatabase;
import io.forsta.securesms.database.MessagingDatabase.SyncMessageId;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.NoSuchMessageException;
import io.forsta.securesms.database.PushDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.groups.GroupMessageProcessor;
import io.forsta.securesms.mms.IncomingMediaMessage;
import io.forsta.securesms.mms.OutgoingExpirationUpdateMessage;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.mms.OutgoingSecureMediaMessage;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.push.TextSecureCommunicationFactory;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.sms.IncomingEncryptedMessage;
import io.forsta.securesms.sms.IncomingEndSessionMessage;
import io.forsta.securesms.sms.IncomingPreKeyBundleMessage;
import io.forsta.securesms.sms.IncomingTextMessage;
import io.forsta.securesms.util.Base64;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;

import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ws.com.google.android.mms.MmsException;

public class PushDecryptJob extends ContextJob {

  private static final long serialVersionUID = 2L;

  public static final String TAG = PushDecryptJob.class.getSimpleName();

  private final long messageId;
  private final long smsMessageId;

  public PushDecryptJob(Context context, long pushMessageId, String sender) {
    this(context, pushMessageId, -1, sender);
  }

  public PushDecryptJob(Context context, long pushMessageId, long smsMessageId, String sender) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId("__PUSH_DECRYPT_JOB__")
                                .withWakeLock(true, 5, TimeUnit.SECONDS)
                                .create());
    this.messageId    = pushMessageId;
    this.smsMessageId = smsMessageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws NoSuchMessageException {

    if (!IdentityKeyUtil.hasIdentityKey(context)) {
      Log.w(TAG, "Skipping job, waiting for migration...");
      MessageNotifier.updateNotification(context, null, true, -2);
      return;
    }

    MasterSecret          masterSecret         = KeyCachingService.getMasterSecret(context);
    PushDatabase          database             = DatabaseFactory.getPushDatabase(context);
    SignalServiceEnvelope envelope             = database.get(messageId);
    Optional<Long>        optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) :
                                                                 Optional.<Long>absent();

    MasterSecretUnion masterSecretUnion;

    if (masterSecret == null) masterSecretUnion = new MasterSecretUnion(MasterSecretUtil.getAsymmetricMasterSecret(context, null));
    else                      masterSecretUnion = new MasterSecretUnion(masterSecret);

    handleMessage(masterSecretUnion, envelope, optionalSmsMessageId);
    database.delete(messageId);
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleMessage(MasterSecretUnion masterSecret, SignalServiceEnvelope envelope, Optional<Long> smsMessageId) {
    try {
      SignalProtocolStore  axolotlStore = new SignalProtocolStoreImpl(context);
      SignalServiceAddress localAddress = new SignalServiceAddress(TextSecurePreferences.getLocalNumber(context));
      SignalServiceCipher  cipher       = new SignalServiceCipher(localAddress, axolotlStore);

      SignalServiceContent content = null;
      try {
        content = cipher.decrypt(envelope);
      } catch (UntrustedIdentityException e) {
        SignalServiceCipher updatedCypher = autoHandleUntrustedIdentity(envelope, localAddress, axolotlStore);
        content = updatedCypher.decrypt(envelope);
      }

      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message = content.getDataMessage().get();

        if (message.isExpirationUpdate())         handleExpirationUpdate(masterSecret, envelope, message, smsMessageId);
        else                                           handleMediaMessage(masterSecret, envelope, message, smsMessageId);
      } else if (content.getSyncMessage().isPresent()) {
        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())    handleSynchronizeSentMessage(masterSecret, envelope, syncMessage.getSent().get(), smsMessageId);
        else if (syncMessage.getRequest().isPresent()) handleSynchronizeRequestMessage(masterSecret, syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent())    handleSynchronizeReadMessage(masterSecret, syncMessage.getRead().get(), envelope.getTimestamp());
        else                                           Log.w(TAG, "Contains no known sync types...");
      }

      if (envelope.isPreKeySignalMessage()) {
        ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob(context));
      }
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
//      handleInvalidVersionMessage(masterSecret, envelope, smsMessageId);
    } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | MmsException e) {
      Log.w(TAG, e);
//      handleCorruptMessage(masterSecret, envelope, smsMessageId);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
//      handleNoSessionMessage(masterSecret, envelope, smsMessageId);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
//      handleLegacyMessage(masterSecret, envelope, smsMessageId);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
//      handleDuplicateMessage(masterSecret, envelope, smsMessageId);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
//      handleUntrustedIdentityMessage(masterSecret, envelope, smsMessageId);
    } catch (InvalidMessagePayloadException e) {
      Log.e(TAG, "Invalid Forsta message body");
      e.printStackTrace();
    }
  }

  private SignalServiceCipher autoHandleUntrustedIdentity(SignalServiceEnvelope envelope, SignalServiceAddress localAddress, SignalProtocolStore axolotlStore) throws InvalidVersionException, InvalidMessageException {
    Log.w(TAG, "Auto handling untrusted identity");
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
    long recipientId = recipients.getPrimaryRecipient().getRecipientId();
    Log.w(TAG, "From recipient: " + recipients.getPrimaryRecipient().getAddress() + " " + recipients.getPrimaryRecipient().getName());
    byte[] encryptedContent = (!envelope.hasLegacyMessage() && envelope.hasContent()) ? envelope.getContent() : envelope.getLegacyMessage();
    PreKeySignalMessage whisperMessage = new PreKeySignalMessage(encryptedContent);
    IdentityKey identityKey = whisperMessage.getIdentityKey();
    DatabaseFactory.getIdentityDatabase(context).saveIdentity(recipientId, identityKey);
    return new SignalServiceCipher(localAddress, axolotlStore);
  }

  private void handleExpirationUpdate(@NonNull MasterSecretUnion masterSecret,
                                      @NonNull SignalServiceEnvelope envelope,
                                      @NonNull SignalServiceDataMessage message,
                                      @NonNull Optional<Long> smsMessageId)
      throws MmsException, InvalidMessagePayloadException {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    String               localNumber  = TextSecurePreferences.getLocalNumber(context);
    String                body       = message.getBody().isPresent() ? message.getBody().get() : "";
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, envelope.getSource(),
                                                                 localNumber, message.getTimestamp(), -1,
                                                                 message.getExpiresInSeconds() * 1000, true,
                                                                 Optional.fromNullable(envelope.getRelay()),
                                                                 message.getBody(), message.getGroupInfo(),
                                                                 Optional.<List<SignalServiceAttachment>>absent());

    ForstaMessage forstaMessage = ForstaMessageManager.fromMessagBodyString(body);
    ForstaDistribution distribution = CcsmApi.getMessageDistribution(context, forstaMessage.getUniversalExpression());
    Recipients recipients = getDistributionRecipients(distribution);
    long threadId = DatabaseFactory.getThreadDatabase(context).getOrAllocateThreadId(recipients, forstaMessage, distribution);

    database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, threadId);
    DatabaseFactory.getThreadPreferenceDatabase(context).setExpireMessages(threadId, message.getExpiresInSeconds());

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleSynchronizeSentMessage(@NonNull MasterSecretUnion masterSecret,
                                            @NonNull SignalServiceEnvelope envelope,
                                            @NonNull SentTranscriptMessage message,
                                            @NonNull Optional<Long> smsMessageId)
      throws MmsException, InvalidMessagePayloadException {
    Long threadId;

    if (message.getMessage().isExpirationUpdate()) {
      threadId = handleSynchronizeSentExpirationUpdate(masterSecret, message, smsMessageId);
    } else {
      threadId = handleSynchronizeSentMediaMessage(masterSecret, message, smsMessageId);
    }

    if (threadId != -1) {
      DatabaseFactory.getThreadDatabase(getContext()).setRead(threadId);
      MessageNotifier.updateNotification(getContext(), masterSecret.getMasterSecret().orNull());
    }
  }

  private void handleSynchronizeRequestMessage(@NonNull MasterSecretUnion masterSecret,
                                               @NonNull RequestMessage message)
  {
    if (message.isContactsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(getContext()));
    }

    if (message.isGroupsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceGroupUpdateJob(getContext()));
    }

    if (message.isBlockedListRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceBlockedUpdateJob(getContext()));
    }
  }

  private void handleSynchronizeReadMessage(@NonNull MasterSecretUnion masterSecret,
                                            @NonNull List<ReadMessage> readMessages,
                                            long envelopeTimestamp)
  {
    for (ReadMessage readMessage : readMessages) {
      List<Pair<Long, Long>> expiringText = DatabaseFactory.getSmsDatabase(context).setTimestampRead(new SyncMessageId(readMessage.getSender(), readMessage.getTimestamp()), envelopeTimestamp);
      List<Pair<Long, Long>> expiringMedia = DatabaseFactory.getMmsDatabase(context).setTimestampRead(new SyncMessageId(readMessage.getSender(), readMessage.getTimestamp()), envelopeTimestamp);

      for (Pair<Long, Long> expiringMessage : expiringText) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first, false, envelopeTimestamp, expiringMessage.second);
      }

      for (Pair<Long, Long> expiringMessage : expiringMedia) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first, true, envelopeTimestamp, expiringMessage.second);
      }
    }

    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull());
  }

  private void handleMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                  @NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
      throws MmsException, InvalidMessagePayloadException {
    String                body       = message.getBody().isPresent() ? message.getBody().get() : "";
    ForstaMessage forstaMessage = ForstaMessageManager.fromMessagBodyString(body);

    if (forstaMessage.getMessageType().equals(ForstaMessage.MessageTypes.CONTENT)) {
      handleContentMessage(forstaMessage, masterSecret, message, envelope);
    } else {
      handleControlMessage(forstaMessage, message.getBody().get());
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull MasterSecretUnion masterSecret,
                                                     @NonNull SentTranscriptMessage message,
                                                     @NonNull Optional<Long> smsMessageId)
      throws MmsException, InvalidMessagePayloadException {
    MmsDatabase database   = DatabaseFactory.getMmsDatabase(context);

    ForstaMessage forstaMessage = ForstaMessageManager.fromMessagBodyString(message.getMessage().getBody().get());
    ForstaDistribution distribution = CcsmApi.getMessageDistribution(context, forstaMessage.getUniversalExpression());
    Recipients recipients = getDistributionRecipients(distribution);
    long threadId = DatabaseFactory.getThreadDatabase(context).getOrAllocateThreadId(recipients, forstaMessage, distribution);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipients,
        message.getTimestamp(),
        message.getMessage().getExpiresInSeconds() * 1000);

    long messageId = database.insertMessageOutbox(masterSecret, expirationUpdateMessage, threadId, false);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    DatabaseFactory.getThreadPreferenceDatabase(context).setExpireMessages(threadId, message.getMessage().getExpiresInSeconds());

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }

    return threadId;
  }

  private long handleSynchronizeSentMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                                 @NonNull SentTranscriptMessage message,
                                                 @NonNull Optional<Long> smsMessageId)
      throws MmsException, InvalidMessagePayloadException {
    MmsDatabase           database     = DatabaseFactory.getMmsDatabase(context);

    ForstaMessage forstaMessage = ForstaMessageManager.fromMessagBodyString(message.getMessage().getBody().get());
    if (forstaMessage.getMessageType().equals(ForstaMessage.MessageTypes.CONTENT)) {
      ForstaDistribution distribution = CcsmApi.getMessageDistribution(context, forstaMessage.getUniversalExpression());
      Recipients recipients = getDistributionRecipients(distribution);
      DirectoryHelper.refreshDirectoryFor(context, masterSecret.getMasterSecret().get(), recipients);
      recipients.setStale();
      recipients = RecipientFactory.getRecipientsFor(context, recipients.getRecipientsList(), false);
      long threadId = DatabaseFactory.getThreadDatabase(context).getOrAllocateThreadId(recipients, forstaMessage, distribution);

      if (DatabaseFactory.getThreadPreferenceDatabase(context).getExpireMessages(threadId) != message.getMessage().getExpiresInSeconds()) {
        handleSynchronizeSentExpirationUpdate(masterSecret, message, Optional.<Long>absent());
      }

      OutgoingMediaMessage  mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
          PointerAttachment.forPointers(masterSecret, message.getMessage().getAttachments()),
          message.getTimestamp(), -1,
          message.getMessage().getExpiresInSeconds() * 1000,
          ThreadDatabase.DistributionTypes.DEFAULT);

      mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);
      long messageId = database.insertMessageOutbox(masterSecret, mediaMessage, threadId, false);

      database.markAsSent(messageId);
      database.markAsPush(messageId);

      for (DatabaseAttachment attachment : DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId)) {
        ApplicationContext.getInstance(context)
            .getJobManager()
            .add(new AttachmentDownloadJob(context, messageId, attachment.getAttachmentId()));
      }

      if (smsMessageId.isPresent()) {
        DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
      }

      if (message.getMessage().getExpiresInSeconds() > 0) {
        database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
        ApplicationContext.getInstance(context)
            .getExpiringMessageManager()
            .scheduleDeletion(messageId, true,
                message.getExpirationStartTimestamp(),
                message.getMessage().getExpiresInSeconds() * 1000);
      }

      return threadId;
    } else {
      handleControlMessage(forstaMessage, message.getMessage().getBody().get());
      return -1;
    }
  }

  private Recipients getDistributionRecipients(ForstaDistribution distribution) throws InvalidMessagePayloadException {
    if (distribution.hasRecipients()) {
      return RecipientFactory.getRecipientsFromStrings(context, distribution.getRecipients(context), false);
    }
    throw new InvalidMessagePayloadException("No recipients found in message.");
  }

  private void handleContentMessage(ForstaMessage forstaMessage,
                                    MasterSecretUnion masterSecret,
                                    SignalServiceDataMessage message,
                                    SignalServiceEnvelope envelope) throws InvalidMessagePayloadException, MmsException {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    String               localNumber  = TextSecurePreferences.getLocalNumber(context);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, envelope.getSource(),
        localNumber, message.getTimestamp(), -1,
        message.getExpiresInSeconds() * 1000, false,
        Optional.fromNullable(envelope.getRelay()),
        message.getBody(),
        message.getGroupInfo(),
        message.getAttachments());


    ForstaDistribution distribution = CcsmApi.getMessageDistribution(context, forstaMessage.getUniversalExpression());
    Recipients recipients = getDistributionRecipients(distribution);
    DirectoryHelper.refreshDirectoryFor(context, masterSecret.getMasterSecret().get(), recipients);
    recipients.setStale();
    recipients = RecipientFactory.getRecipientsFor(context, recipients.getRecipientsList(), false);
    long threadId = DatabaseFactory.getThreadDatabase(context).getOrAllocateThreadId(recipients, forstaMessage, distribution);

    if (message.getExpiresInSeconds() != DatabaseFactory.getThreadPreferenceDatabase(context).getExpireMessages(threadId)) {
      handleExpirationUpdate(masterSecret, envelope, message, Optional.<Long>absent());
    }

    Pair<Long, Long>         messageAndThreadId = database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, threadId);
    List<DatabaseAttachment> attachments        = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageAndThreadId.first);

    for (DatabaseAttachment attachment : attachments) {
      ApplicationContext.getInstance(context)
          .getJobManager()
          .add(new AttachmentDownloadJob(context, messageAndThreadId.first,
              attachment.getAttachmentId()));
    }

    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
  }

  private void handleControlMessage(ForstaMessage forstaMessage, String messageBody) {
    try {
      Log.w(TAG, "Got control message: " + messageBody);
      Log.w(TAG, "Control Type: " + forstaMessage.getControlType());
      switch (forstaMessage.getControlType()) {
        case ForstaMessage.ControlTypes.THREAD_UPDATE:
          ThreadDatabase threadDb = DatabaseFactory.getThreadDatabase(context);
          ForstaThread threadData = threadDb.getForstaThread(forstaMessage.getThreadUId());

          if (threadData != null) {
            // TODO Need to handle in UI before allowing full thread updates here.
//          ForstaDistribution distribution = CcsmApi.getMessageDistribution(context, forstaMessage.getUniversalExpression());
//          Recipients recipients = getDistributionRecipients(distribution);
//          threadDb.updateForstaThread(threadData.getThreadid(), recipients, forstaMessage, distribution);
            String currentTitle = threadData.getTitle() != null ? threadData.getTitle() : "";
            if (!currentTitle.equals(forstaMessage.getThreadTitle())) {
              threadDb.updateThreadTitle(threadData.getThreadid(), forstaMessage.getThreadTitle());
              threadDb.setThreadUnread(threadData.getThreadid());
            }
          }
          break;
        case ForstaMessage.ControlTypes.PROVISION_REQUEST:
          Log.w(TAG, "Got Provision Request...");
          // Check to see that message request was sent by superman.
          String sender = forstaMessage.getSenderId();
          if (!sender.equals(BuildConfig.FORSTA_SYNC_NUMBER)) {
            throw new Exception("Received provision request from unknown sender.");
          }
          ForstaMessage.ForstaProvisionRequest request = forstaMessage.getProvisionRequest();
          ForstaServiceAccountManager accountManager = TextSecureCommunicationFactory.createManager(context);
          String verificationCode = accountManager.getNewDeviceVerificationCode();
          String ephemeralId = request.getUuid();
          String publicKeyEncoded = request.getKey();

          if (TextUtils.isEmpty(ephemeralId) || TextUtils.isEmpty(publicKeyEncoded)) {
            throw new Exception("UUID or Key is empty!");
          }

          ECPublicKey publicKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);
          IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context);

          accountManager.addDevice(ephemeralId, publicKey, identityKeyPair, verificationCode);
          TextSecurePreferences.setMultiDevice(context, true);
          break;
      }

    } catch (Exception e) {
      Log.e(TAG, "Control message excption: " + e.getMessage());
      e.printStackTrace();
    }
  }

}
