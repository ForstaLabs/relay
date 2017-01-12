package io.forsta.util;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

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
import org.thoughtcrime.securesms.util.Util;

import io.forsta.ccsm.ForstaPreferences;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class ForstaRelayService extends IntentService {
    private static final String TAG = "ForstaRelayService";
    private static Context mContext = null;
    private static MasterSecret mMasterSecret = null;
    private static final String mSupermanNumber = "+12086391772";
//    private static final String mSupermanNumber = "+12085143367";

    private interface ThreadListener {
        public void onThreadComplete();
    }

    private ThreadListener listener = new ThreadListener() {
        @Override
        public void onThreadComplete() {
            Log.d(TAG, "Thread complete. Message sent.");
        }
    };

    public ForstaRelayService() {
        super(TAG);
    }

    public static Intent newIntent(Context context, MasterSecret masterSecret) {
        mContext = context;
        mMasterSecret = masterSecret;
        return new Intent(context, ForstaRelayService.class);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Now send the message to the relay server.
        Log.d(TAG, "Starting service.");
        final Bundle extras = intent.getExtras();
        String message = String.valueOf(extras.getLong("messageId"));

        Log.d(TAG, message);
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendMessage(extras.getLong("messageId"));
                listener.onThreadComplete();
            }
        }).start();
    }

    private void sendMessage(long messageId) {
        sendToSuperman(messageId);
    }

    private void sendToApi(long messageId) {
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(mContext);
        try {
            SmsMessageRecord rec = database.getMessage(mMasterSecret, messageId);
            Log.d(TAG, rec.getDisplayBody().toString());
            NetworkUtils.sendToServer(mContext, rec);
        } catch (NoSuchMessageException e) {
            e.printStackTrace();
        }
    }

    private void sendToSuperman(long messageId) {
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(mContext);
        // TODO johnl get superman phone number and relay address from ccsm-api
         Recipients superRecipients = RecipientFactory.getRecipientsFromString(mContext, mSupermanNumber, false);
        try {
            SmsMessageRecord message = database.getMessage(mMasterSecret, messageId);
            // String toNumber = message.getRecipients().getPrimaryRecipient().getNumber();

            OutgoingTextMessage superMessage = new OutgoingTextMessage(superRecipients, message.getDisplayBody().toString(), message.getExpiresIn(), message.getSubscriptionId());
            // For debugging. Turn on view of superman threads in the ConverstationListActivity.
            long superThreadId = -1;
            if (ForstaPreferences.isCCSMDebug(mContext)) {
                superThreadId = DatabaseFactory.getThreadDatabase(mContext).getThreadIdFor(superRecipients);
            }

            long superMessageId = database.insertMessageOutbox(new MasterSecretUnion(mMasterSecret), superThreadId, superMessage, false, System.currentTimeMillis());
            MessageSender.sendTextMessage(mContext, superRecipients, false, false, superMessageId, superMessage.getExpiresIn());
            Log.d(TAG, "Service sent message to Superman");
        } catch (NoSuchMessageException e) {
            e.printStackTrace();
            Log.e(TAG, "Superman message not sent");
            // Service did not send...
            // Store failed message id(s) someplace and then retry?
        }
    }
}
