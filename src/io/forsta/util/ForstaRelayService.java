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
            NetworkUtils.sendToServer(rec);
        } catch (NoSuchMessageException e) {
            e.printStackTrace();
        }
    }

    private void sendToSuperman(long messageId) {
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(mContext);
        Recipients superRecipients = RecipientFactory.getRecipientsFromString(mContext, "+12086391772", false);
        try {
            SmsMessageRecord message = database.getMessage(mMasterSecret, messageId);
            String toNumber = message.getRecipients().getPrimaryRecipient().getNumber();

            OutgoingTextMessage superMessage = new OutgoingTextMessage(superRecipients, message.getDisplayBody().toString(), message.getExpiresIn(), message.getSubscriptionId());
            long superThreadId = DatabaseFactory.getThreadDatabase(mContext).getThreadIdFor(superRecipients);
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

// This code is saved. Changed the scope of the sendTextMessage method to achieve the service implementation above.
    //      try {
//        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//        String oldFfromNumber = tm.getLine1Number();
//        Recipients superRecipients = RecipientFactory.getRecipientsFromString(context, "+12086391772", false);
//        String fromNumber = Util.getDeviceE164Number(context);
//        String toNumber = message.getRecipients().getPrimaryRecipient().getNumber();
//
//        Log.d(TAG, "FROM:");
//        Log.d(TAG, fromNumber);
//        Log.d(TAG, "TO:");
//        Log.d(TAG, toNumber);
//
////        StringBuilder messageData = new StringBuilder();
////        messageData.append("Message: ").append(message.getMessageBody()).append("\n");
////        messageData.append("From: ").append(fromNumber).append("\n");
////        messageData.append("To: ").append(toNumber);
//        OutgoingTextMessage superMessage = new OutgoingTextMessage(superRecipients, message.getMessageBody(), message.getExpiresIn(), message.getSubscriptionId());
//        long superThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(superRecipients);
//        long superMessageId = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), superThreadId, superMessage, forceSms, System.currentTimeMillis());
//
//        sendTextMessage(context, superRecipients, forceSms, keyExchange, superMessageId, superMessage.getExpiresIn());
//      } catch (Exception e) {
//        Log.e(TAG, "Superman failed...");
//      }
}
