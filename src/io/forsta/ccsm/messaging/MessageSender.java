/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.ccsm.messaging;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.EncryptingSmsDatabase;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.jobmanager.JobManager;
import io.forsta.securesms.jobs.PushMediaSendJob;
import io.forsta.securesms.jobs.PushTextSendJob;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.ExpiringMessageManager;
import io.forsta.securesms.sms.OutgoingEndSessionMessage;
import io.forsta.securesms.sms.OutgoingTextMessage;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;

import java.util.LinkedList;

import ws.com.google.android.mms.MmsException;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  private static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingMediaMessage message,
                          final long threadId,
                          final boolean forceSms)
  {
    try {
      MmsDatabase    database       = DatabaseFactory.getMmsDatabase(context);

      if (threadId == -1) {
        throw new Exception("Invalid thread id");
      }
      Recipients recipients = message.getRecipients();
      long       messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, threadId, forceSms);

      sendMediaMessage(context, masterSecret, recipients, forceSms, messageId, message.getExpiresIn());

    } catch (MmsException e) {
      Log.w(TAG, e);
    } catch (Exception e) {
      Log.w(TAG, "Message send exception: " + e);
    }
    return threadId;
  }

  private static void sendControlMessage(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingMessage message)
  {
    try {
      MmsDatabase    database       = DatabaseFactory.getMmsDatabase(context);

      Recipients recipients = message.getRecipients();
      long       messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, -1, false);

      sendMediaMessage(context, masterSecret, recipients, false, messageId, message.getExpiresIn());

    } catch (MmsException e) {
      Log.w(TAG, e);
    } catch (Exception e) {
      Log.w(TAG, "Message send exception: " + e);
    }
  }

  public static void resend(Context context, MasterSecret masterSecret, MessageRecord messageRecord) {
    try {
      long       messageId   = messageRecord.getId();
      boolean    forceSms    = messageRecord.isForcedSms();
      boolean    keyExchange = messageRecord.isKeyExchange();
      long       expiresIn   = messageRecord.getExpiresIn();

      Recipients recipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageId);
      sendMediaMessage(context, masterSecret, recipients, forceSms, messageId, expiresIn);

    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private static void sendMediaMessage(Context context, MasterSecret masterSecret,
                                       Recipients recipients, boolean forceSms,
                                       long messageId, long expiresIn)
      throws MmsException
  {
    if (isSelfSend(context, recipients)) {
      sendMediaSelf(context, masterSecret, messageId, expiresIn);
    } else {
      sendMediaPush(context, recipients, messageId);
    }
  }

  private static void sendTextSelf(Context context, long messageId, long expiresIn) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    Pair<Long, Long> messageAndThreadId = database.copyMessageInbox(messageId);
    database.markAsPush(messageAndThreadId.first);

    if (expiresIn > 0) {
      ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(context).getExpiringMessageManager();

      database.markExpireStarted(messageId);
      expiringMessageManager.scheduleDeletion(messageId, false, expiresIn);
    }
  }

  private static void sendMediaSelf(Context context, MasterSecret masterSecret,
                                    long messageId, long expiresIn)
      throws MmsException
  {
    ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MmsDatabase            database               = DatabaseFactory.getMmsDatabase(context);

    database.markAsSent(messageId);
    database.markAsPush(messageId);

    Recipient you = RecipientFactory.getRecipient(context, TextSecurePreferences.getLocalNumber(context), false);
    Recipients recipients = RecipientFactory.getRecipientsFor(context, you, false);
    sendMediaPush(context, recipients, messageId);

    if (expiresIn > 0) {
      database.markExpireStarted(messageId);
      expiringMessageManager.scheduleDeletion(messageId, true, expiresIn);
    }
  }

  private static void sendTextPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushTextSendJob(context, messageId, recipients.getPrimaryRecipient().getAddress()));
  }

  private static void sendMediaPush(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushMediaSendJob(context, messageId, recipients.getPrimaryRecipient().getAddress()));
  }

  private static boolean isSelfSend(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (!recipients.isSingleRecipient()) {
      return false;
    }

    return Util.isOwnNumber(context, recipients.getPrimaryRecipient().getAddress());
  }

  public static long sendContentMessage(Context context, MasterSecret masterSecret, String body, Recipients recipients, SlideDeck slideDeck, long threadId, long expiresIn) {
    OutgoingMessage message = ForstaMessageManager.createOutgoingContentMessage(context, body, recipients, slideDeck.asAttachments(), threadId, expiresIn);
    return send(context, masterSecret, message, threadId, false);
  }

  public static long sendContentReplyMesage(Context context, MasterSecret masterSecret, String body, Recipients recipients, SlideDeck slideDeck, long threadId, long expiresIn, String messageRef, int vote) {
    OutgoingMessage message = ForstaMessageManager.createOutgoingContentReplyMessage(context, body, recipients, slideDeck.asAttachments(), threadId, expiresIn, messageRef, vote);
    return send(context, masterSecret, message, threadId, false);
  }

  public static void sendExpirationUpdate(Context context, MasterSecret masterSecret, Recipients recipients, long threadId, int expirationTime) {
    OutgoingExpirationUpdateMessage message = ForstaMessageManager.createOutgoingExpirationUpdateMessage(context, recipients, threadId, expirationTime * 1000);
    send(context, masterSecret, message, threadId, false);
  }

  public static void sendThreadUpdate(Context context, MasterSecret masterSecret, Recipients recipients, long threadId) {
    try {
      ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      String payload = ForstaMessageManager.createThreadUpdateMessage(context, user, recipients, thread);
      OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      sendControlMessage(context, masterSecret, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void sendEndSessionMessage(Context context, MasterSecret masterSecret, Recipients recipients, long threadId) {
    OutgoingEndSessionMediaMessage message = ForstaMessageManager.createOutgoingEndSessionMessage(context, recipients, threadId);
    send(context, masterSecret, message, threadId, false);
  }
}
