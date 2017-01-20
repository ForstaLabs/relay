package io.forsta.util;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import io.forsta.ccsm.CcsmSync;

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
//                sendToForstaSync(messageId);
            }
        }).start();
    }

//    private void sendToForstaSync(long messageId) {
//        CcsmSync.syncMessage(mMasterSecret, mContext, messageId);
//    }
}
