package io.forsta.ccsm.api;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.util.DirectoryHelper;

/**
 * Created by jlewis
 */

//TODO remove this code. This has been replaced by the SyncAdapter services.
public class ForstaContactsSyncIntentService extends IntentService {

  private static final int SYNC_INTERVAL = 1000 * 60 * 60 * 4; // Four hours

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
    try {
      Log.d(TAG, "Syncing Forsta Contacts");
      Context context = getApplicationContext();
      MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
      CcsmApi.syncForstaContacts(context, masterSecret);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
