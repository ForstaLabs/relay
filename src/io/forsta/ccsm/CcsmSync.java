package io.forsta.ccsm;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;

import java.util.List;

/**
 * Created by jlewis on 1/18/17.
 */

public class CcsmSync {
    private static final String TAG = CcsmSync.class.getSimpleName();
    private static final String mSupermanNumber = BuildConfig.FORSTA_SYNC_NUMBER;

    private CcsmSync() {

    }

    public static void syncMessage(MasterSecret masterSecret, Context context, long messageId) {
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
        Recipients superRecipients = RecipientFactory.getRecipientsFromString(context, mSupermanNumber, false);

        try {
            SmsMessageRecord message = database.getMessage(masterSecret, messageId);
            JSONObject jsonBody = createMessageBody(message);
            // TODO send this shit to the SM.
            OutgoingTextMessage superMessage = new OutgoingTextMessage(superRecipients, jsonBody.toString(), message.getExpiresIn(), message.getSubscriptionId());
            // Hide thread from UI.
            long superThreadId = -1;
            // For debugging. Turn on view of superman threads in the ConverstationListActivity.
            if (ForstaPreferences.isCCSMDebug(context)) {
                superThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(superRecipients);
            }

            long superMessageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), superThreadId, superMessage, false, System.currentTimeMillis());
            MessageSender.sendTextMessage(context, superRecipients, false, false, superMessageId, superMessage.getExpiresIn());
            Log.d(TAG, "Forsta Sync. Sending Sync Message.");
        } catch (NoSuchMessageException e) {
            e.printStackTrace();
            Log.e(TAG, "Forsta Sync message not found.");
        }
    }

    private static JSONObject createMessageBody(SmsMessageRecord message) {
        JSONObject json = new JSONObject();
        Recipients recipients = message.getRecipients();
        List<Recipient> list = recipients.getRecipientsList();
        JSONArray dest = new JSONArray();
        for (Recipient item : list) {
            dest.put(item.getNumber());
        }
        try {
            json.put("dest", dest);
            json.put("message", message.getDisplayBody().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return json;
    }
}
