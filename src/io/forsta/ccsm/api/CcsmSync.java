package io.forsta.ccsm.api;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.mms.OutgoingExpirationUpdateMessage;
import io.forsta.securesms.mms.OutgoingGroupMediaMessage;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.sms.OutgoingTextMessage;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.Util;
import ws.com.google.android.mms.MmsException;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by jlewis on 1/18/17.
 */

public class CcsmSync {
  private static final String TAG = CcsmSync.class.getSimpleName();

  private CcsmSync() {
  }

  public static void syncMediaMessage(MasterSecret masterSecret, Context context, OutgoingMediaMessage message) {
    try {
      Recipients recipients = message.getRecipients();
      Recipient primaryRecipient = recipients.getPrimaryRecipient();
      String primary = primaryRecipient.getNumber();

      if (GroupUtil.isEncodedGroup(primary)) {
        byte[] decodedGroupId = GroupUtil.getDecodedId(primary);
        recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(decodedGroupId, false);
      }
      // Filters out group create/update messages and expiration update messages.
      if (!(message instanceof OutgoingGroupMediaMessage || message.isExpirationUpdate())) {
        syncMessage(masterSecret, context, recipients, message.getBody(), message.getAttachments(), message.getExpiresIn(), message.getSubscriptionId());
      }

    } catch (Exception e) {
      Log.e(TAG, "Forsta CCSM Sync media message failed");
      e.printStackTrace();
    }
  }

  public static void syncTextMessage(MasterSecret masterSecret, Context context, OutgoingTextMessage message) {
    try {
      Recipients recipients = message.getRecipients();
      boolean keyExchange = message.isKeyExchange();

      Recipient primaryRecipient = recipients.getPrimaryRecipient();
      String primary = primaryRecipient.getNumber();
      // Don't duplicate keyexchanges or direct messages to sync number.
      if (!keyExchange && !primary.equals(BuildConfig.FORSTA_SYNC_NUMBER)) {
        syncMessage(masterSecret, context, recipients, message.getMessageBody(), message.getExpiresIn(), message.getSubscriptionId());
      }
    } catch (Exception e) {
      Log.e(TAG, "Forsta CCSM Sync text message failed");
      e.printStackTrace();
    }
  }

  private static void syncMessage(MasterSecret masterSecret, Context context, Recipients recipients, String body, long expiresIn, int subsriptionId) {
    syncMessage(masterSecret, context, recipients, body, new LinkedList<Attachment>(), expiresIn, subsriptionId);
  }

  private static void syncMessage(MasterSecret masterSecret, Context context, Recipients recipients, String body, List<Attachment> attachments, long expiresIn, int subscriptionId) {
    Recipients superRecipients = RecipientFactory.getRecipientsFromString(context, BuildConfig.FORSTA_SYNC_NUMBER, false);
    long superThreadId = -1;
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    if (!ForstaMessage.isJsonBody(body)) {
      body = ForstaMessage.createForstaMessageBody(context, body, recipients);
    }
    OutgoingMediaMessage superMediaMessage = new OutgoingMediaMessage(superRecipients, body, attachments, System.currentTimeMillis(), -1, expiresIn, ThreadDatabase.DistributionTypes.CONVERSATION);
    Log.w(TAG, "Forsta Sync. Sending Sync Message.");
    try {
      long id = mmsDatabase.insertMessageOutbox(new MasterSecretUnion(masterSecret), superMediaMessage, superThreadId, false);
      MessageSender.sendMediaMessage(context, masterSecret, superRecipients, false, id, superMediaMessage.getExpiresIn());
    } catch (MmsException e) {
      e.printStackTrace();
    }
  }
}
