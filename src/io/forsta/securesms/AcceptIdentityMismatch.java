package io.forsta.securesms;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.IdentityDatabase;
import io.forsta.securesms.database.MmsAddressDatabase;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.PushDatabase;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.documents.IdentityKeyMismatch;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.jobs.IdentityUpdateJob;
import io.forsta.securesms.jobs.PushDecryptJob;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.Base64;

/**
 * Created by jlewis on 7/19/17.
 */

public class AcceptIdentityMismatch extends AsyncTask<Void, Void, Void> {
  private final Context context;
  private final MasterSecret masterSecret;
  private final MessageRecord messageRecord;
  private final IdentityKeyMismatch mismatch;

  public AcceptIdentityMismatch(Context context, MasterSecret masterSecret, MessageRecord messageRecord, IdentityKeyMismatch mismatch) {
    this.context = context;
    this.masterSecret  = masterSecret;
    this.messageRecord = messageRecord;
    this.mismatch      = mismatch;
  }

  @Override
  protected Void doInBackground(Void... voids) {
    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);

    identityDatabase.saveIdentity(mismatch.getRecipientId(),
        mismatch.getIdentityKey());

    processMessageRecord(messageRecord);
    processPendingMessageRecords(messageRecord.getThreadId(), mismatch);

    ApplicationContext.getInstance(context)
        .getJobManager()
        .add(new IdentityUpdateJob(context, mismatch.getRecipientId()));

    return null;
  }

  private void processMessageRecord(MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) processOutgoingMessageRecord(messageRecord);
    else                            processIncomingMessageRecord(messageRecord);
  }

  private void processPendingMessageRecords(long threadId, IdentityKeyMismatch mismatch) {
    MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
    Cursor cursor         = mmsSmsDatabase.getIdentityConflictMessagesForThread(threadId);
    MmsSmsDatabase.Reader reader         = mmsSmsDatabase.readerFor(cursor, masterSecret);
    MessageRecord         record;

    try {
      while ((record = reader.getNext()) != null) {
        for (IdentityKeyMismatch recordMismatch : record.getIdentityKeyMismatches()) {
          if (mismatch.equals(recordMismatch)) {
            processMessageRecord(record);
          }
        }
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private void processOutgoingMessageRecord(MessageRecord messageRecord) {
    SmsDatabase smsDatabase        = DatabaseFactory.getSmsDatabase(context);
    MmsDatabase mmsDatabase        = DatabaseFactory.getMmsDatabase(context);
    MmsAddressDatabase mmsAddressDatabase = DatabaseFactory.getMmsAddressDatabase(context);

    if (messageRecord.isMms()) {
      mmsDatabase.removeMismatchedIdentity(messageRecord.getId(),
          mismatch.getRecipientId(),
          mismatch.getIdentityKey());

      Recipients recipients = mmsAddressDatabase.getRecipientsForId(messageRecord.getId());

      if (recipients.isGroupRecipient()) MessageSender.resendGroupMessage(context, masterSecret, messageRecord, mismatch.getRecipientId());
      else                               MessageSender.resend(context, masterSecret, messageRecord);
    } else {
      smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
          mismatch.getRecipientId(),
          mismatch.getIdentityKey());

      MessageSender.resend(context, masterSecret, messageRecord);
    }
  }

  private void processIncomingMessageRecord(MessageRecord messageRecord) {
    try {
      PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(context);
      SmsDatabase  smsDatabase  = DatabaseFactory.getSmsDatabase(context);

      smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
          mismatch.getRecipientId(),
          mismatch.getIdentityKey());

      SignalServiceEnvelope envelope = new SignalServiceEnvelope(SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE,
          messageRecord.getIndividualRecipient().getAddress(),
          messageRecord.getRecipientDeviceId(), "",
          messageRecord.getDateSent(),
          Base64.decode(messageRecord.getBody().getBody()),
          null);

      long pushId = pushDatabase.insert(envelope);

      ApplicationContext.getInstance(context)
          .getJobManager()
          .add(new PushDecryptJob(context, pushId, messageRecord.getId(),
              messageRecord.getIndividualRecipient().getAddress()));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
