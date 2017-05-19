package io.forsta.ccsm.api;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by jlewis on 5/8/17.
 */

public class ForstaSyncService extends Service {

  private static ForstaSyncAdapter syncAdapter = null;
  private static final Object syncLock = new Object();

  @Override
  public void onCreate() {
    synchronized (syncLock) {
      if (syncAdapter == null) {
        syncAdapter = new ForstaSyncAdapter(getApplicationContext(), true);
      }
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {

    return syncAdapter.getSyncAdapterBinder();
  }
}
