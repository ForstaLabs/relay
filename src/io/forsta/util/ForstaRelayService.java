package io.forsta.util;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import io.forsta.ccsm.ForstaPreferences;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class ForstaRelayService extends IntentService {
    private static final String TAG = ForstaRelayService.class.getSimpleName();
    private static Context mContext = null;
    private static MasterSecret mMasterSecret = null;
    private static final String mSupermanNumber = BuildConfig.FORSTA_SYNC_NUMBER;

    public ForstaRelayService() {
        super(TAG);
    }

    public static Intent newIntent(Context context, MasterSecret masterSecret) {
        mContext = context;
        mMasterSecret = masterSecret;
        return new Intent(context, ForstaRelayService.class);
    }

    public static String getSupermanNumber() {
        return mSupermanNumber;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final Bundle extras = intent.getExtras();
        final long messageId = extras.getLong("messageId");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Forsta Sync. Sending Message ID: " + messageId);
                sendToForstaSync(messageId);
            }
        }).start();
    }

    private void sendToApi(long messageId) {
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(mContext);
        try {
            SmsMessageRecord rec = database.getMessage(mMasterSecret, messageId);
            NetworkUtils.sendToServer(mContext, rec);
        } catch (NoSuchMessageException e) {
            e.printStackTrace();
        }
    }

    private void sendToForstaSync(long messageId) {
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(mContext);
        Recipients superRecipients = RecipientFactory.getRecipientsFromString(mContext, mSupermanNumber, false);

        try {
            SmsMessageRecord message = database.getMessage(mMasterSecret, messageId);
            OutgoingTextMessage superMessage = new OutgoingTextMessage(superRecipients, message.getDisplayBody().toString(), message.getExpiresIn(), message.getSubscriptionId());
            // Hide thread from UI.
            long superThreadId = -1;
            // For debugging. Turn on view of superman threads in the ConverstationListActivity.
            if (ForstaPreferences.isCCSMDebug(mContext)) {
                superThreadId = DatabaseFactory.getThreadDatabase(mContext).getThreadIdFor(superRecipients);
            }

            long superMessageId = database.insertMessageOutbox(new MasterSecretUnion(mMasterSecret), superThreadId, superMessage, false, System.currentTimeMillis());
            MessageSender.sendTextMessage(mContext, superRecipients, false, false, superMessageId, superMessage.getExpiresIn());
            Log.d(TAG, "Forsta Sync. Message Sent.");
        } catch (NoSuchMessageException e) {
            e.printStackTrace();
            Log.e(TAG, "Forsta Sync message not sent");
            // TODO handle failed messages.
        }
    }
}
