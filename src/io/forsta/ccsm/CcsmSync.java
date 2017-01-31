package io.forsta.ccsm;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.forsta.securesms.BuildConfig;
import org.forsta.securesms.crypto.MasterSecret;
import org.forsta.securesms.crypto.MasterSecretUnion;
import org.forsta.securesms.database.DatabaseFactory;
import org.forsta.securesms.database.EncryptingSmsDatabase;
import org.forsta.securesms.mms.OutgoingMediaMessage;
import org.forsta.securesms.recipients.Recipient;
import org.forsta.securesms.recipients.RecipientFactory;
import org.forsta.securesms.recipients.Recipients;
import org.forsta.securesms.sms.MessageSender;
import org.forsta.securesms.sms.OutgoingTextMessage;
import org.forsta.securesms.util.GroupUtil;
import org.forsta.securesms.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.List;

/**
 * Created by jlewis on 1/18/17.
 */

public class CcsmSync {
    private static final String TAG = CcsmSync.class.getSimpleName();
    private static final String mForstaSyncNumber = BuildConfig.FORSTA_SYNC_NUMBER;

    private CcsmSync() { }

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

            Recipient primaryRecipient = recipients.getPrimaryRecipient();
            String primary = primaryRecipient.getNumber();
            // Don't duplicate keyexchanges or direct messages to sync number.
            if (!keyExchange && !primary.equals(mForstaSyncNumber)) {
                syncMessage(masterSecret, context, recipients, message.getMessageBody(), message.getExpiresIn(), message.getSubscriptionId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Forsta Sync failed");
            e.printStackTrace();
        }
    }

    private static void syncMessage(MasterSecret masterSecret, Context context, Recipients recipients, String body, long expiresIn, int subscriptionId) {
        EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
        Recipients superRecipients = RecipientFactory.getRecipientsFromString(context, mForstaSyncNumber, false);
        JSONObject jsonBody = createMessageBody(context, recipients, body);
        OutgoingTextMessage superMessage = new OutgoingTextMessage(superRecipients, jsonBody.toString(), expiresIn, subscriptionId);

        // TODO check use of -1 as default. Currently hides messages from UI, but may create other issues.
        // For debugging. Turn on view of superman threads in the ConverstationListActivity.
        long superThreadId = ForstaPreferences.isCCSMDebug(context) ? DatabaseFactory.getThreadDatabase(context).getThreadIdFor(superRecipients) : -1;
        long superMessageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), superThreadId, superMessage, false, System.currentTimeMillis());

        Log.d(TAG, "Forsta Sync. Sending Sync Message.");

        MessageSender.sendTextMessage(context, superRecipients, false, false, superMessageId, superMessage.getExpiresIn());
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
