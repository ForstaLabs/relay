// vim: ts=2:sw=2:expandtab
package io.forsta.securesms;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.push.TextSecureCommunicationFactory;
import io.forsta.securesms.service.RegistrationService;
import io.forsta.securesms.util.Dialogs;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;

import java.io.IOException;

import static io.forsta.securesms.service.RegistrationService.RegistrationState;

public class RegistrationProgressActivity extends BaseActionBarActivity {

  private static final String TAG = RegistrationProgressActivity.class.getSimpleName();

  private static final int FOCUSED_COLOR   = Color.parseColor("#ff333333");
  private static final int UNFOCUSED_COLOR = Color.parseColor("#ff808080");

  private ServiceConnection    serviceConnection        = new RegistrationServiceConnection();
  private Handler              registrationStateHandler = new RegistrationStateHandler();
  private RegistrationReceiver registrationReceiver     = new RegistrationReceiver();

  private RegistrationService registrationService;

  private LinearLayout registrationLayout;
  private LinearLayout connectivityFailureLayout;

  private ProgressBar registrationProgress;
  private ProgressBar connectingProgress;
  private ProgressBar generatingKeysProgress;
  private ProgressBar gcmRegistrationProgress;


  private ImageView   connectingCheck;
  private ImageView   generatingKeysCheck;
  private ImageView   gcmRegistrationCheck;

  private TextView    connectingText;
  private TextView    generatingKeysText;
  private TextView    gcmRegistrationText;

  private MasterSecret masterSecret;
  private volatile boolean visible;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.registration_progress_activity);

    initializeResources();
    initializeLinks();
    initializeServiceBinding();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdownServiceBinding();
  }

  @Override
  public void onResume() {
    super.onResume();
    handleActivityVisible();
  }

  @Override
  public void onPause() {
    super.onPause();
    handleActivityNotVisible();
  }

  @Override
  public void onBackPressed() {

  }

  private void initializeServiceBinding() {
    Intent intent = new Intent(this, RegistrationService.class);
    bindService(intent, serviceConnection, BIND_AUTO_CREATE);
  }

  private void initializeResources() {
    this.masterSecret              = getIntent().getParcelableExtra("master_secret");
    this.registrationLayout        = (LinearLayout)findViewById(R.id.registering_layout);
    this.connectivityFailureLayout = (LinearLayout)findViewById(R.id.connectivity_failure_layout);
    this.registrationProgress      = (ProgressBar) findViewById(R.id.registration_progress);
    this.connectingProgress        = (ProgressBar) findViewById(R.id.connecting_progress);
    this.generatingKeysProgress    = (ProgressBar) findViewById(R.id.generating_keys_progress);
    this.gcmRegistrationProgress   = (ProgressBar) findViewById(R.id.gcm_registering_progress);
    this.connectingCheck           = (ImageView)   findViewById(R.id.connecting_complete);
    this.generatingKeysCheck       = (ImageView)   findViewById(R.id.generating_keys_complete);
    this.gcmRegistrationCheck      = (ImageView)   findViewById(R.id.gcm_registering_complete);
    this.connectingText            = (TextView)    findViewById(R.id.connecting_text);
    this.generatingKeysText        = (TextView)    findViewById(R.id.generating_keys_text);
    this.gcmRegistrationText       = (TextView)    findViewById(R.id.gcm_registering_text);
  }

  private void initializeLinks() {
    String          link            = getString(R.string.RegistrationProblemsActivity_possible_problems);
    SpannableString spannableString = new SpannableString(link);

    spannableString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        new AlertDialog.Builder(RegistrationProgressActivity.this)
                .setTitle(R.string.RegistrationProblemsActivity_possible_problems)
                .setView(R.layout.registration_problems)
                .setNeutralButton(android.R.string.ok, null)
                .show();
      }
    }, 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }

  private void handleActivityVisible() {
    IntentFilter filter = new IntentFilter(RegistrationService.REGISTRATION_EVENT);
    filter.setPriority(1000);
    registerReceiver(registrationReceiver, filter);
    visible = true;
  }

  private void handleActivityNotVisible() {
    unregisterReceiver(registrationReceiver);
    visible = false;
  }

  private void handleStateIdle() {
    Intent intent = new Intent(this, RegistrationService.class);
    intent.setAction(RegistrationService.REGISTER_ACCOUNT);
    intent.putExtra("master_secret", masterSecret);
    startService(intent);
  }

  private void handleStateConnecting() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.VISIBLE);
    this.connectingCheck.setVisibility(View.INVISIBLE);
    this.generatingKeysProgress.setVisibility(View.INVISIBLE);
    this.generatingKeysCheck.setVisibility(View.INVISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(FOCUSED_COLOR);
    this.generatingKeysText.setTextColor(UNFOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(UNFOCUSED_COLOR);
  }

  private void handleStateVerifying() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.generatingKeysProgress.setVisibility(View.INVISIBLE);
    this.generatingKeysCheck.setVisibility(View.INVISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.generatingKeysText.setTextColor(UNFOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(UNFOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.VISIBLE);
  }

  private void handleStateGeneratingKeys() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.generatingKeysProgress.setVisibility(View.VISIBLE);
    this.generatingKeysCheck.setVisibility(View.INVISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.generatingKeysText.setTextColor(FOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(UNFOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.INVISIBLE);
  }

  private void handleStateGcmRegistering() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.generatingKeysProgress.setVisibility(View.INVISIBLE);
    this.generatingKeysCheck.setVisibility(View.VISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.VISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.generatingKeysText.setTextColor(UNFOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(FOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.INVISIBLE);
  }

  private void handleGcmTimeout(RegistrationState state) {
    handleConnectivityError(state);
  }

  private void handleConnectivityError(RegistrationState state) {
    this.registrationLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.VISIBLE);
  }

  private void shutdownServiceBinding() {
    if (serviceConnection != null) {
      unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  private void shutdownService() {
    if (registrationService != null) {
      registrationService.shutdown();
      registrationService = null;
    }

    shutdownServiceBinding();

    Intent serviceIntent = new Intent(RegistrationProgressActivity.this, RegistrationService.class);
    stopService(serviceIntent);
  }

  private class RegistrationServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      registrationService  = ((RegistrationService.RegistrationServiceBinder)service).getService();
      registrationService.setRegistrationStateHandler(registrationStateHandler);

      RegistrationState state = registrationService.getRegistrationState();
      registrationStateHandler.obtainMessage(state.state, state).sendToTarget();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      registrationService.setRegistrationStateHandler(null);
    }
  }

  private class RegistrationStateHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      RegistrationState state = (RegistrationState)message.obj;

      switch (message.what) {
      case RegistrationState.STATE_IDLE:                 handleStateIdle();                       break;
      case RegistrationState.STATE_CONNECTING:           handleStateConnecting();                 break;
      case RegistrationState.STATE_VERIFYING:            handleStateVerifying();                  break;
      case RegistrationState.STATE_GENERATING_KEYS:      handleStateGeneratingKeys();             break;
      case RegistrationState.STATE_GCM_REGISTERING:      handleStateGcmRegistering();             break;
      case RegistrationState.STATE_GCM_TIMEOUT:          handleGcmTimeout(state);                 break;
      case RegistrationState.STATE_NETWORK_ERROR:        handleConnectivityError(state);          break;
      }
    }
  }

  private static class RegistrationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      abortBroadcast();
    }
  }

/*
        @Override
        protected Integer doInBackground(Void... params) {
          try {
            SignalServiceAccountManager accountManager = TextSecureCommunicationFactory.createManager(context, e164number, password);
            int                         registrationId = TextSecurePreferences.getLocalRegistrationId(context);

            accountManager.verifyAccountWithCode(code, signalingKey, registrationId, true);

            return SUCCESS;
          } catch (ExpectationFailedException e) {
            Log.w(TAG, e);
            return MULTI_REGISTRATION_ERROR;
          } catch (RateLimitException e) {
            Log.w(TAG, e);
            return RATE_LIMIT_ERROR;
          } catch (IOException e) {
            Log.w(TAG, e);
            return NETWORK_ERROR;
          }
        }
      }.execute();
    }
  }
*/

}
