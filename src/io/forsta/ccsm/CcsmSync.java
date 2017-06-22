package io.forsta.ccsm;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.EncryptingSmsDatabase;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.ThreadDatabase;
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

import org.whispersystems.signalservice.api.util.InvalidNumberException;

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
      // Filters out group create/update messages.
      if (!(message instanceof OutgoingGroupMediaMessage)) {
        syncMessage(masterSecret, context, recipients, message.getBody(), message.getAttachments(), message.getExpiresIn(), message.getSubscriptionId());
      }

    } catch (Exception e) {
      Log.e(TAG, "Forsta Sync failed");
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
      if (!keyExchange && !primary.equals(ForstaPreferences.getForstaSyncNumber(context))) {
        syncMessage(masterSecret, context, recipients, message.getMessageBody(), message.getExpiresIn(), message.getSubscriptionId());
      }
    } catch (Exception e) {
      Log.e(TAG, "Forsta Sync failed");
      e.printStackTrace();
    }
  }

  private static void syncMessage(MasterSecret masterSecret, Context context, Recipients recipients, String body, long expiresIn, int subsriptionId) {
    syncMessage(masterSecret, context, recipients, body, new LinkedList<Attachment>(), expiresIn, subsriptionId);
  }

  private static void syncMessage(MasterSecret masterSecret, Context context, Recipients recipients, String body, List<Attachment> attachments, long expiresIn, int subscriptionId) {
    Recipients superRecipients = RecipientFactory.getRecipientsFromString(context, ForstaPreferences.getForstaSyncNumber(context), false);
    // Change this to use JSON body sent to all clients. ForstaUtils.createForstaMessageBody(body)
    JSONObject jsonBody = createMessageBody(context, recipients, body);
    // TODO check use of -1 as default. Currently hides messages from UI, but may create other issues.
    // For debugging. Turn on view of superman threads in the ConversationListActivity.
    long superThreadId = ForstaPreferences.isCCSMDebug(context) ? DatabaseFactory.getThreadDatabase(context).getThreadIdFor(superRecipients) : -1;
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage superMediaMessage = new OutgoingMediaMessage(superRecipients, jsonBody.toString(), attachments, System.currentTimeMillis(), -1, expiresIn, ThreadDatabase.DistributionTypes.CONVERSATION);
    Log.d(TAG, "Forsta Sync. Sending Sync Message.");
    try {
      long id = mmsDatabase.insertMessageOutbox(new MasterSecretUnion(masterSecret), superMediaMessage, superThreadId, false);
      MessageSender.sendMediaMessage(context, masterSecret, superRecipients, false, id, superMediaMessage.getExpiresIn());
    } catch (MmsException e) {
      e.printStackTrace();
    }
  }

  private static JSONObject createMessageBody(Context context, Recipients recipients, String body) {
    JSONObject json = new JSONObject();
    List<Recipient> list = recipients.getRecipientsList();
    JSONArray dest = new JSONArray();

    try {
      for (Recipient item : list) {
        String e164Number = Util.canonicalizeNumber(context, item.getNumber());
        dest.put(e164Number);
      }
      json.put("dest", dest);
      json.put("message", body);
    } catch (JSONException e) {
      e.printStackTrace();
    } catch (InvalidNumberException e) {
      e.printStackTrace();
    }

    return json;
  }

}
