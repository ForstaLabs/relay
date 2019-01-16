/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.securesms.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.DatabaseUpgradeActivity;
import io.forsta.securesms.DummyActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.InvalidPassphraseException;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUtil;
import io.forsta.securesms.jobs.MasterSecretDecryptJob;
import io.forsta.securesms.notifications.AbstractNotificationBuilder;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.notifications.NotificationChannels;
import io.forsta.securesms.util.DynamicLanguage;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

/**
 * Small service that stays running to keep a key cached in memory.
 *
 * @author Moxie Marlinspike
 */

public class KeyCachingService extends Service {

  public static final String TAG = KeyCachingService.class.getSimpleName();
  public static final int SERVICE_RUNNING_ID = 4141;

  public  static final String KEY_PERMISSION           = "io.forsta.securesms.ACCESS_SECRETS";
  public  static final String NEW_KEY_EVENT            = "io.forsta.securesms.service.action.NEW_KEY_EVENT";
  public  static final String CLEAR_KEY_EVENT          = "io.forsta.securesms.service.action.CLEAR_KEY_EVENT";
  private static final String PASSPHRASE_EXPIRED_EVENT = "io.forsta.securesms.service.action.PASSPHRASE_EXPIRED_EVENT";
  public  static final String CLEAR_KEY_ACTION         = "io.forsta.securesms.service.action.CLEAR_KEY";
  public  static final String DISABLE_ACTION           = "io.forsta.securesms.service.action.DISABLE";
  public  static final String ACTIVITY_START_EVENT     = "io.forsta.securesms.service.action.ACTIVITY_START_EVENT";
  public  static final String ACTIVITY_STOP_EVENT      = "io.forsta.securesms.service.action.ACTIVITY_STOP_EVENT";
  public  static final String LOCALE_CHANGE_EVENT      = "io.forsta.securesms.service.action.LOCALE_CHANGE_EVENT";
  public  static final String LOCK_TOGGLED_EVENT       = "io.forsta.securesms.service.action.LOCK_ENABLED_EVENT";

  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private PendingIntent pending;
  private int activitiesRunning = 0;
  private final IBinder binder  = new KeySetBinder();

  private static MasterSecret masterSecret;

  public KeyCachingService() {}

  public static synchronized boolean isLocked(Context context) {
    return getMasterSecret(context) == null;
  }

  public static synchronized @Nullable MasterSecret getMasterSecret(Context context) {
    if (masterSecret == null && (TextSecurePreferences.isPasswordDisabled(context))) {
      try {
        return MasterSecretUtil.getMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
      } catch (InvalidPassphraseException e) {
        Log.w("KeyCachingService", e);
      }
    }

    return masterSecret;
  }

  public static void onAppForegrounded(@NonNull Context context) {
    ServiceUtil.getAlarmManager(context).cancel(buildExpirationPendingIntent(context));
  }

  public static void onAppBackgrounded(@NonNull Context context) {
    startTimeoutIfAppropriate(context);
  }

  @SuppressLint("StaticFieldLeak")
  public void setMasterSecret(final MasterSecret masterSecret) {
    synchronized (KeyCachingService.class) {
      KeyCachingService.masterSecret = masterSecret;

      foregroundService();
      broadcastNewSecret();
      startTimeoutIfAppropriate(this);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          if (!DatabaseUpgradeActivity.isUpdate(KeyCachingService.this)) {
            MessageNotifier.updateNotification(KeyCachingService.this, masterSecret);
          }
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;
    Log.w("KeyCachingService", "onStartCommand, " + intent.getAction());

    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case CLEAR_KEY_ACTION:         handleClearKey();        break;
        case PASSPHRASE_EXPIRED_EVENT: handleClearKey();        break;
        case DISABLE_ACTION:           handleDisableService();  break;
        case LOCALE_CHANGE_EVENT:      handleLocaleChanged();   break;
        case LOCK_TOGGLED_EVENT:       handleLockToggled();     break;
      }
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");
    super.onCreate();

    if (TextSecurePreferences.isPasswordDisabled(this)) {
      try {
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
        setMasterSecret(masterSecret);
      } catch (InvalidPassphraseException e) {
        Log.w("KeyCachingService", e);
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.w(TAG, "KCS Is Being Destroyed!");
    handleClearKey();
  }

  /**
   * Workaround for Android bug:
   * https://code.google.com/p/android/issues/detail?id=53313
   */
  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Intent intent = new Intent(this, DummyActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  private void handleClearKey() {
    Log.w("KeyCachingService", "handleClearKey()");
    KeyCachingService.masterSecret = null;
    stopForeground(true);

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(KeyCachingService.this, null);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleLockToggled() {
    stopForeground(true);

    try {
      MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
      setMasterSecret(masterSecret);
    } catch (InvalidPassphraseException e) {
      Log.w(TAG, e);
    }
  }

  private void handleDisableService() {
    if (TextSecurePreferences.isPasswordDisabled(this))
      stopForeground(true);
  }

  private void handleLocaleChanged() {
    dynamicLanguage.updateServiceLocale(this);
    foregroundService();
  }

  private static void startTimeoutIfAppropriate(@NonNull Context context) {
    boolean appVisible       = ApplicationContext.getInstance(context).isAppVisible();
    boolean secretSet        = KeyCachingService.masterSecret != null;

    boolean timeoutEnabled   = TextSecurePreferences.isPassphraseTimeoutEnabled(context);
    boolean passLockActive   = timeoutEnabled && !TextSecurePreferences.isPasswordDisabled(context);

//    long    screenTimeout    = TextSecurePreferences.getScreenLockTimeout(context);
//    boolean screenLockActive = screenTimeout >= 60 && TextSecurePreferences.isScreenLockEnabled(context);

    if (!appVisible && secretSet && (passLockActive)) {
      long passphraseTimeoutMinutes = TextSecurePreferences.getPassphraseTimeoutInterval(context);
//      long screenLockTimeoutSeconds = TextSecurePreferences.getScreenLockTimeout(context);

      long timeoutMillis = TimeUnit.MINUTES.toMillis(passphraseTimeoutMinutes);

//      if (!TextSecurePreferences.isPasswordDisabled(context)) timeoutMillis = TimeUnit.MINUTES.toMillis(passphraseTimeoutMinutes);
//      else                                                    timeoutMillis = TimeUnit.SECONDS.toMillis(screenLockTimeoutSeconds);

      Log.i(TAG, "Starting timeout: " + timeoutMillis);

      AlarmManager  alarmManager     = ServiceUtil.getAlarmManager(context);
      PendingIntent expirationIntent = buildExpirationPendingIntent(context);

      alarmManager.cancel(expirationIntent);
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + timeoutMillis, expirationIntent);
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  private void foregroundServiceModern() {
    Log.w("KeyCachingService", "foregrounding KCS");
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.LOCKED_STATUS);

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_signal_passphrase_cached));
    builder.setSmallIcon(R.drawable.icon_cached);
    builder.setWhen(0);
    builder.setPriority(Notification.PRIORITY_MIN);

    builder.addAction(R.drawable.ic_menu_lock_dark, getString(R.string.KeyCachingService_lock), buildLockIntent());
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);
    startForeground(SERVICE_RUNNING_ID, builder.build());
  }

  private void foregroundService() {
    if (TextSecurePreferences.isPasswordDisabled(this)) {
      stopForeground(true);
      return;
    }

    foregroundServiceModern();
  }

  private void broadcastNewSecret() {
    Log.w("service", "Broadcasting new secret...");

    Intent intent = new Intent(NEW_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, 0);
    return pendingIntent;
  }

  private PendingIntent buildLaunchIntent() {
    Intent intent              = new Intent(this, ConversationListActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent launchIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
    return launchIntent;
  }

  private static PendingIntent buildExpirationPendingIntent(@NonNull Context context) {
    Intent expirationIntent = new Intent(PASSPHRASE_EXPIRED_EVENT, null, context, KeyCachingService.class);
    return PendingIntent.getService(context, 0, expirationIntent, 0);
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return binder;
  }

  public class KeySetBinder extends Binder {
    public KeyCachingService getService() {
      return KeyCachingService.this;
    }
  }
}
