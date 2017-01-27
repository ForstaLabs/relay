package io.forsta.ccsm;

import android.content.Context;
import android.database.Cursor;
import android.telephony.PhoneNumberUtils;
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
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

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
