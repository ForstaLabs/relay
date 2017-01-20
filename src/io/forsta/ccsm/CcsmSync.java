package io.forsta.ccsm;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.GroupUtil;

import java.util.List;

import io.forsta.util.ForstaRelayService;

/**
 * Created by jlewis on 1/18/17.
 */

public class CcsmSync {
    private static final String TAG = CcsmSync.class.getSimpleName();
    private static final String mSupermanNumber = BuildConfig.FORSTA_SYNC_NUMBER;

    private CcsmSync() {

    }

    public static void syncMediaMessage(MasterSecret masterSecret, Context context, OutgoingMediaMessage message) {
        try {
            Recipients recipients = message.getRecipients();
            Recipient primaryRecipient = recipients.getPrimaryRecipient();
            String primary = primaryRecipient.getNumber();
            if (GroupUtil.isEncodedGroup(primary)) {
                recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(GroupUtil.getDecodedId(primary), false);
            }

            syncMessage(masterSecret, context, recipients, message.getBody(), message.getExpiresIn(), message.getSubscriptionId());

        } catch (Exception e) {
            Log.e(TAG, "Forsta Sync failed");
            e.printStackTrace();
        }
    }

    public static void syncTextMessage(MasterSecret masterSecret, Context context, OutgoingTextMessage message) {
        try {
            Recipients recipients = message.getRecipients();
            boolean keyExchange = message.isKeyExchange();

            // Check to see if message is for superman.
            Recipient primaryRecipient = recipients.getPrimaryRecipient();
            String primary = primaryRecipient.getNumber();

            if (!keyExchange && !primary.equals(mSupermanNumber)) {
                syncMessage(masterSecret, context, recipients, message.getMessageBody(), message.getExpiresIn(), message.getSubscriptionId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Forsta Sync failed");
            e.printStackTrace();
        }
    }

    private static void syncMessage(MasterSecret masterSecret, Context context, Recipients recipients, String body, long expiresIn, int subscriptionId) {
        EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
        Recipients superRecipients = RecipientFactory.getRecipientsFromString(context, mSupermanNumber, false);
        JSONObject jsonBody = createMessageBody(recipients, body);
        OutgoingTextMessage superMessage = new OutgoingTextMessage(superRecipients, jsonBody.toString(), expiresIn, subscriptionId);
        // Hide thread from UI.
        long superThreadId = -1;
        // For debugging. Turn on view of superman threads in the ConverstationListActivity.
        if (ForstaPreferences.isCCSMDebug(context)) {
            superThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(superRecipients);
        }

        long superMessageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), superThreadId, superMessage, false, System.currentTimeMillis());
        MessageSender.sendTextMessage(context, superRecipients, false, false, superMessageId, superMessage.getExpiresIn());
        Log.d(TAG, "Forsta Sync. Sending Sync Message.");
    }

    private static JSONObject createMessageBody(Recipients recipients, String body) {
        JSONObject json = new JSONObject();
        List<Recipient> list = recipients.getRecipientsList();
        JSONArray dest = new JSONArray();
        for (Recipient item : list) {
            dest.put(item.getNumber());
        }
        try {
            json.put("dest", dest);
            json.put("message", body);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return json;
    }
}
