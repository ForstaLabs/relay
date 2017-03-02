package io.forsta.ccsm.api;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.service.KeyCachingService;

/**
 *
 */
public class ForstaContactsSyncIntentService extends IntentService {
    private static final String TAG = ForstaContactsSyncIntentService.class.getSimpleName();

    public ForstaContactsSyncIntentService() {
        super(TAG);
    }

    public static Intent newIntent(Context context) {
        return new Intent(context, ForstaContactsSyncIntentService.class);
    }

    public ForstaContactsSyncIntentService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Context context = getApplicationContext();
        String lastUpdate = ForstaPreferences.getForstaContactSync(context);
        boolean shouldUpdate = false;
        if (lastUpdate.equals("")) {
            shouldUpdate = true;
        }

        if (shouldUpdate) {
            MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
            if (masterSecret != null) {
                CcsmApi.syncForstaGroups(context, masterSecret);
                ForstaPreferences.setForstaContactSync(context, new Date().toString());
            }
        }
    }
}
