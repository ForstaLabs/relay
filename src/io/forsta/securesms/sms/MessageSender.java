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
package io.forsta.securesms.sms;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.messaging.OutgoingMessage;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.EncryptingSmsDatabase;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.NotInDirectoryException;
import io.forsta.securesms.database.TextSecureDirectory;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.jobs.MmsSendJob;
import io.forsta.securesms.jobs.PushGroupSendJob;
import io.forsta.securesms.jobs.PushMediaSendJob;
import io.forsta.securesms.jobs.PushTextSendJob;
import io.forsta.securesms.jobs.SmsSendJob;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.push.TextSecureCommunicationFactory;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.ExpiringMessageManager;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import io.forsta.securesms.webrtc.IceUpdateMessage;
import ws.com.google.android.mms.MmsException;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  // This is only used for sending Session End Messages.
  // OutgoingEndSessionMessage
  public static long send(final Context context,
                          final MasterSecret masterSecret,
                          final OutgoingTextMessage message,
                          final long threadId,
                          final boolean forceSms)
  {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients  = message.getRecipients();
    boolean               keyExchange = message.isKeyExchange();

    long allocatedThreadId;

    if (threadId == -1) {
      allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    } else {
      allocatedThreadId = threadId;
    }

    long messageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), allocatedThreadId,
                                                  message, forceSms, System.currentTimeMillis());

    sendTextMessage(context, recipients, forceSms, keyExchange, messageId, message.getExpiresIn());

    return allocatedThreadId;
  }

  public static long send(final Context context,
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

  public static void sendControlMessage(final Context context,
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

  public static void sendSmsInvite(final Context context, final MasterSecret masterSecret, final OutgoingTextMessage message) {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients  = message.getRecipients();

    long messageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), -1,
        message, true, System.currentTimeMillis());

    sendSms(context, recipients, messageId);
  }


  public static void resendGroupMessage(Context context, MasterSecret masterSecret, MessageRecord messageRecord, long filterRecipientId) {
    if (!messageRecord.isMms()) throw new AssertionError("Not Group");

    Recipients recipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageRecord.getId());
    sendGroupPush(context, recipients, messageRecord.getId(), filterRecipientId);
  }

  public static void resend(Context context, MasterSecret masterSecret, MessageRecord messageRecord) {
    try {
      long       messageId   = messageRecord.getId();
      boolean    forceSms    = messageRecord.isForcedSms();
      boolean    keyExchange = messageRecord.isKeyExchange();
      long       expiresIn   = messageRecord.getExpiresIn();

      if (messageRecord.isMms()) {
        Recipients recipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageId);
        sendMediaMessage(context, masterSecret, recipients, forceSms, messageId, expiresIn);
      } else {
        Recipients recipients  = messageRecord.getRecipients();
        sendTextMessage(context, recipients, forceSms, keyExchange, messageId, expiresIn);
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  public static void sendMediaMessage(Context context, MasterSecret masterSecret,
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

  private static void sendTextMessage(Context context, Recipients recipients,
                                      boolean forceSms, boolean keyExchange,
                                      long messageId, long expiresIn)
  {
    if (isSelfSend(context, recipients)) {
      sendTextSelf(context, messageId, expiresIn);
    } else {
      sendTextPush(context, recipients, messageId);
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

    long newMessageId = database.copyMessageInbox(masterSecret, messageId);
    database.markAsPush(newMessageId);

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

  // Keep for sending invitations.
  private static void sendSms(Context context, Recipients recipients, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new SmsSendJob(context, messageId, recipients.getPrimaryRecipient().getName()));
  }

  // No longer valid.
  private static void sendGroupPush(Context context, Recipients recipients, long messageId, long filterRecipientId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new PushGroupSendJob(context, messageId, recipients.getPrimaryRecipient().getAddress(), filterRecipientId));
  }

  // No longer valid
  private static void sendMms(Context context, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new MmsSendJob(context, messageId));
  }

  private static boolean isPushTextSend(Context context, Recipients recipients, boolean keyExchange) {
    try {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (keyExchange) {
        return false;
      }

      Recipient recipient   = recipients.getPrimaryRecipient();
      String    destination = Util.canonicalizeNumber(context, recipient.getAddress());

      return isPushDestination(context, destination);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private static boolean isPushMediaSend(Context context, Recipients recipients) {
    try {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (recipients.getRecipientsList().size() > 1) {
        return isForstaGroupDestination(context, recipients.toNumberStringList(false));
      }

      Recipient recipient   = recipients.getPrimaryRecipient();
      String    destination = Util.canonicalizeNumber(context, recipient.getAddress());

      return isPushDestination(context, destination);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private static boolean isGroupPushSend(Recipients recipients) {
    return GroupUtil.isEncodedGroup(recipients.getPrimaryRecipient().getAddress());
  }

  private static boolean isSelfSend(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return false;
    }

    if (!recipients.isSingleRecipient()) {
      return false;
    }

    if (recipients.isGroupRecipient()) {
      return false;
    }

    return Util.isOwnNumber(context, recipients.getPrimaryRecipient().getAddress());
  }

  private static boolean isPushDestination(Context context, String destination) {
    TextSecureDirectory directory = TextSecureDirectory.getInstance(context);

    try {
      return directory.isSecureTextSupported(destination);
    } catch (NotInDirectoryException e) {
      try {
        ForstaServiceAccountManager   accountManager = TextSecureCommunicationFactory.createManager(context);
        Optional<ContactTokenDetails> registeredUser = accountManager.getContact(destination);

        if (!registeredUser.isPresent()) {
          registeredUser = Optional.of(new ContactTokenDetails());
          registeredUser.get().setNumber(destination);
          directory.setNumber(registeredUser.get(), false);
          return false;
        } else {
          registeredUser.get().setNumber(destination);
          directory.setNumber(registeredUser.get(), true);
          return true;
        }
      } catch (IOException e1) {
        Log.w(TAG, e1);
        return false;
      }
    }
  }

  private static boolean isForstaGroupDestination(Context context, List<String> destinations) {
    boolean result = false;
    for (String address : destinations) {
      result = isPushDestination(context, address);
    }
    return result;
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

  public static void sendCallAcceptOffer(Context context, MasterSecret masterSecret, Recipients recipients, String threadId, String callId, SessionDescription sdp, String peerId) {
    try {
      ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      String payload = ForstaMessageManager.createAcceptCallOfferMessage(user, recipients, thread, callId, sdp.description, peerId);
      Log.w(TAG, "Sending call accept offer: " + payload);
      OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      sendControlMessage(context, masterSecret, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void sendCallOffer(Context context, MasterSecret masterSecret, Recipients recipients, String threadId, String callId, SessionDescription sdp, String peerId) {
    try {
      ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      String payload = ForstaMessageManager.createCallOfferMessage(user, recipients, thread, callId, sdp.description, peerId);
      Log.w(TAG, "Sending call offer: " + payload);
      OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      sendControlMessage(context, masterSecret, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void sendIceUpdate(Context context, MasterSecret masterSecret, Recipients recipients, String threadId, String callId, String peerId, List<IceCandidate> updates) {
    try {
      ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      JSONArray jsonUpdates = new JSONArray();
      for (IceCandidate candidate : updates) {
        JSONObject jsonCandidate = new JSONObject();
        jsonCandidate.put("candidate", candidate.sdp);
        jsonCandidate.put("sdpMid", candidate.sdpMid);
        jsonCandidate.put("sdpMLineIndex", candidate.sdpMLineIndex);
        jsonUpdates.put(jsonCandidate);
      }

      String payload = ForstaMessageManager.createIceCandidateMessage(user, recipients, thread, callId, peerId, jsonUpdates);
      Log.w(TAG, "Sending ICE Update: " + payload);
      OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      sendControlMessage(context, masterSecret, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void sendCallLeave(Context context, MasterSecret masterSecret, Recipients recipients, String threadId, String callId) {
    try {
      ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      String payload = ForstaMessageManager.createCallLeaveMessage(user, recipients, thread, callId);
      Log.w(TAG, "Sending Call Leave: " + payload);
      OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      sendControlMessage(context, masterSecret, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
