package io.forsta.util;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

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
            }
        }).start();

    }

    private void sendMessage(long messageId) {
        // Need to make this a Task or Thread.
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(mContext);
        try {
            SmsMessageRecord rec = database.getMessage(mMasterSecret, messageId);
            Log.d(TAG, rec.getDisplayBody().toString());
            NetworkUtils.sendToServer(rec);
        } catch (NoSuchMessageException e) {
            e.printStackTrace();
        }
    }
}
