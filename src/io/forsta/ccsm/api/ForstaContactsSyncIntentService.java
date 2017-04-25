package io.forsta.ccsm.api;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.service.KeyCachingService;

/**
 * Created by jlewis
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
    try {
      Context context = getApplicationContext();
      MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
//            CcsmApi.syncForstaContacts(getApplicationContext());
//            CcsmApi.syncForstaGroups(context, masterSecret);
      CcsmApi.syncForstaGroupUsers(context, masterSecret);
      ForstaPreferences.setForstaContactSync(context, new Date().toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
