// vim: ts=2:sw=2:expandtab
package io.forsta.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.IdentityKeyUtil;
import io.forsta.securesms.crypto.PreKeyUtil;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.jobs.GcmRefreshJob;
import io.forsta.securesms.push.TextSecureCommunicationFactory;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import org.json.JSONException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The RegisterationService handles the process of PushService registration and verification.
 * If it receives an intent with a REGISTER_ACCOUNT, it does the following through
 * an executor:
 *
 * 1) Generate secrets.
 * 2) Register our CCSM user ID and those secrets with the server.
 * 3) Start the GCM registration process.
 *
 * The RegistrationService broadcasts its state throughout this process, and also makes its
 * state available through service binding.  This enables a View to display progress.
 *
 * @author Moxie Marlinspike
 *
 */

public class RegistrationService extends Service {

  public static final String REGISTER_ACCOUNT = "io.forsta.securesms.RegistrationService.REGISTER_ACCOUNT";

  public static final String NOTIFICATION_TITLE     = "io.forsta.securesms.NOTIFICATION_TITLE";
  public static final String NOTIFICATION_TEXT      = "io.forsta.securesms.NOTIFICATION_TEXT";
  public static final String CHALLENGE_EVENT        = "io.forsta.securesms.CHALLENGE_EVENT"; // Deprecated
  public static final String REGISTRATION_EVENT     = "io.forsta.securesms.REGISTRATION_EVENT";
  public static final String CHALLENGE_EXTRA        = "DEPRECATED";
    

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Binder          binder   = new RegistrationServiceBinder();

  private volatile RegistrationState registrationState = new RegistrationState(RegistrationState.STATE_IDLE);

  private volatile WeakReference<Handler>  registrationStateHandler;
  private          long                    verificationStartTime;
  private          boolean                 generatingPreKeys;

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    if (intent != null) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          handleCcsmRegistrationIntent(intent);
        }
      });
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    executor.shutdown();
    shutdown();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public void shutdown() {
    markAsVerifying(false);
    registrationState = new RegistrationState(RegistrationState.STATE_IDLE);
  }

  public RegistrationState getRegistrationState() {
    return registrationState;
  }

  private void handleCcsmRegistrationIntent(Intent intent) {
    markAsVerifying(true);
    int registrationId = TextSecurePreferences.getLocalRegistrationId(this);
    if (registrationId == 0) {
      registrationId = KeyHelper.generateRegistrationId(false);
      TextSecurePreferences.setLocalRegistrationId(this, registrationId);
    }
    String password     = Util.getSecret(18);
    String signalingKey = Util.getSecret(52);
    Context context = getApplicationContext();
    String addr = ForstaPreferences.getUserId(context);
    setState(new RegistrationState(RegistrationState.STATE_CONNECTING));
    try {
      ForstaServiceAccountManager accountManager = TextSecureCommunicationFactory.createManager(this);
      accountManager.createAccount(context, addr, password, signalingKey, registrationId);
      setState(new RegistrationState(RegistrationState.STATE_VERIFYING));
      handleCommonRegistration(accountManager, addr, password, signalingKey);
      markAsVerified(addr, password, signalingKey);
      setState(new RegistrationState(RegistrationState.STATE_COMPLETE));
      broadcastComplete(true);
    } catch (ExpectationFailedException efe) {
      Log.w("RegistrationService", efe);
      setState(new RegistrationState(RegistrationState.STATE_MULTI_REGISTERED));
      broadcastComplete(false);
    } catch (UnsupportedOperationException uoe) {
      Log.w("RegistrationService", uoe);
      setState(new RegistrationState(RegistrationState.STATE_GCM_UNSUPPORTED));
      broadcastComplete(false);
    } catch (Exception e) {
      Log.w("RegistrationService", e);
      setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR));
      broadcastComplete(false);
    }
  }

  private void handleCommonRegistration(ForstaServiceAccountManager accountManager, String addr,
                                        String password, String signalingKey)
      throws IOException
  {
    setState(new RegistrationState(RegistrationState.STATE_GENERATING_KEYS));
    Recipient          self         = RecipientFactory.getRecipientsFromString(this, addr, false).getPrimaryRecipient();
    IdentityKeyPair    identityKey  = IdentityKeyUtil.getIdentityKeyPair(this);
    List<PreKeyRecord> records      = PreKeyUtil.generatePreKeys(this);
    PreKeyRecord       lastResort   = PreKeyUtil.generateLastResortKey(this);
    SignedPreKeyRecord signedPreKey = PreKeyUtil.generateSignedPreKey(this, identityKey);
    accountManager.setPreKeys(identityKey.getPublicKey(),lastResort, signedPreKey, records);

    setState(new RegistrationState(RegistrationState.STATE_GCM_REGISTERING));

    String gcmRegistrationId = GoogleCloudMessaging.getInstance(this).register(GcmRefreshJob.REGISTRATION_ID);
    accountManager.setGcmId(Optional.of(gcmRegistrationId));

    TextSecurePreferences.setGcmRegistrationId(this, gcmRegistrationId);
    TextSecurePreferences.setWebsocketRegistered(this, true);

    DatabaseFactory.getIdentityDatabase(this).saveIdentity(self.getRecipientId(), identityKey.getPublicKey());
    DirectoryHelper.refreshDirectory(this, accountManager, addr);
    DirectoryRefreshListener.schedule(this);
  }

  private void markAsVerifying(boolean verifying) {
    TextSecurePreferences.setVerifying(this, verifying);

    if (verifying) {
      TextSecurePreferences.setPushRegistered(this, false);
    }
  }

  private void markAsVerified(String addr, String password, String signalingKey) {
    TextSecurePreferences.setVerifying(this, false);
    TextSecurePreferences.setPushRegistered(this, true);
    TextSecurePreferences.setLocalNumber(this, addr);
    TextSecurePreferences.setPushServerPassword(this, password);
    TextSecurePreferences.setSignalingKey(this, signalingKey);
    TextSecurePreferences.setSignedPreKeyRegistered(this, true);
    TextSecurePreferences.setPromptedPushRegistration(this, true);
  }

  private void setState(RegistrationState state) {
    this.registrationState = state;

    Handler registrationStateHandler = this.registrationStateHandler.get();

    if (registrationStateHandler != null) {
      registrationStateHandler.obtainMessage(state.state, state).sendToTarget();
    }
  }

  private void broadcastComplete(boolean success) {
    Intent intent = new Intent();
    intent.setAction(REGISTRATION_EVENT);

    if (success) {
      intent.putExtra(NOTIFICATION_TITLE, getString(R.string.RegistrationService_registration_complete));
      intent.putExtra(NOTIFICATION_TEXT, getString(R.string.RegistrationService_signal_registration_has_successfully_completed));
    } else {
      intent.putExtra(NOTIFICATION_TITLE, getString(R.string.RegistrationService_registration_error));
      intent.putExtra(NOTIFICATION_TEXT, getString(R.string.RegistrationService_signal_registration_has_encountered_a_problem));
    }

    this.sendOrderedBroadcast(intent, null);
  }

  public void setRegistrationStateHandler(Handler registrationStateHandler) {
    this.registrationStateHandler = new WeakReference<>(registrationStateHandler);
  }

  public class RegistrationServiceBinder extends Binder {
    public RegistrationService getService() {
      return RegistrationService.this;
    }
  }

  public static class RegistrationState {

    public static final int STATE_IDLE                 =  0;
    public static final int STATE_CONNECTING           =  1;
    public static final int STATE_VERIFYING            =  2;
    public static final int STATE_TIMER                =  3;
    public static final int STATE_COMPLETE             =  4;
    public static final int STATE_NETWORK_ERROR        =  6;

    public static final int STATE_GCM_UNSUPPORTED      =  8;
    public static final int STATE_GCM_REGISTERING      =  9;
    public static final int STATE_GCM_TIMEOUT          = 10;

    public static final int STATE_GENERATING_KEYS      = 13;

    public static final int STATE_MULTI_REGISTERED     = 14;

    public final int    state;
    public final String password;

    public RegistrationState(int state) {
      this(state, null);
    }

    public RegistrationState(int state, String password) {
      this.state        = state;
      this.password     = password;
    }
  }
}
