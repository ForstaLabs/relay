package io.forsta.securesms.jobs;

import android.content.Context;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.IncomingIdentityUpdateMessage;
import io.forsta.securesms.sms.IncomingTextMessage;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

public class IdentityUpdateJob extends MasterSecretJob {

  private final long recipientId;

  public IdentityUpdateJob(Context context, long recipientId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(IdentityUpdateJob.class.getName())
                                .withPersistence()
                                .create());
    this.recipientId = recipientId;
  }

  @Override
  public void onRun(MasterSecret masterSecret) throws Exception {
    Recipient            recipient      = RecipientFactory.getRecipientForId(context, recipientId, true);
    Recipients           recipients     = RecipientFactory.getRecipientsFor(context, recipient, true);
    String               number         = recipient.getNumber();
    long                 time           = System.currentTimeMillis();
    SmsDatabase smsDatabase    = DatabaseFactory.getSmsDatabase(context);
    ThreadDatabase       threadDatabase = DatabaseFactory.getThreadDatabase(context);
    GroupDatabase        groupDatabase  = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader         = groupDatabase.getGroups();

    GroupDatabase.GroupRecord groupRecord;

    while ((groupRecord = reader.getNext()) != null) {
      if (groupRecord.getMembers().contains(number)) {
        SignalServiceGroup            group       = new SignalServiceGroup(groupRecord.getId());
        IncomingTextMessage           incoming    = new IncomingTextMessage(number, 1, time, null, Optional.of(group), 0);
        IncomingIdentityUpdateMessage groupUpdate = new IncomingIdentityUpdateMessage(incoming);

        smsDatabase.insertMessageInbox(groupUpdate);
      }
    }

    if (threadDatabase.getThreadIdIfExistsFor(recipients) != -1) {
      IncomingTextMessage           incoming         = new IncomingTextMessage(number, 1, time, null, Optional.<SignalServiceGroup>absent(), 0);
      IncomingIdentityUpdateMessage individualUpdate = new IncomingIdentityUpdateMessage(incoming);
      smsDatabase.insertMessageInbox(individualUpdate);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {

  }
}
