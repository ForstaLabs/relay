package io.forsta.ccsm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.api.ForstaContactsSyncIntentService;
import io.forsta.securesms.BaseActionBarActivity;
import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.RegistrationActivity;
import io.forsta.ccsm.api.CcsmApi;

public class LoginActivity extends BaseActionBarActivity {
  private static final String TAG = LoginActivity.class.getSimpleName();
  private Button mSubmitButton;
  private Button mSendTokenButton;
  private TextView mTryAgainButton;
  private EditText mSendTokenUsername;
  private EditText mSendTokenOrg;
  private EditText mLoginUsernameText;
  private EditText mLoginPasswordText;
  private EditText mLoginSecurityCode;
  private ProgressBar mLoginProgressBar;
  private LinearLayout mLoginFormContainer;
  private LinearLayout mSendLinkFormContainer;
  private LinearLayout mLoginSubmitFormContainer;
  private LinearLayout mVerifyFormContainer;
  private LinearLayout mPasswordFormContainer;
  private TextView mStandardLogin;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
    getSupportActionBar().setTitle("Join Forsta");
    initializeView();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mSendTokenOrg.setText(ForstaPreferences.getForstaOrgName(this));
    mSendTokenUsername.setText(ForstaPreferences.getForstaUsername(this));
    // handleBroadcastIntent();
    if (ForstaPreferences.getForstaLoginPending(LoginActivity.this)) {
      showVerifyForm();
    } else if (!ForstaPreferences.getRegisteredKey(LoginActivity.this).equals("")) {
      Intent intent = new Intent(LoginActivity.this, ConversationListActivity.class);
      startActivity(intent);
      finish();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  private void initializeView() {
    mLoginFormContainer = (LinearLayout) findViewById(R.id.forsta_login_container);
    mSendLinkFormContainer = (LinearLayout) findViewById(R.id.forsta_login_send_link_container);
    mLoginSubmitFormContainer = (LinearLayout) findViewById(R.id.forsta_login_submit_container);
    mVerifyFormContainer = (LinearLayout) findViewById(R.id.forsta_login_verify_token_container);
    mPasswordFormContainer = (LinearLayout) findViewById(R.id.forsta_login_password_container);

    mLoginProgressBar = (ProgressBar) findViewById(R.id.login_progress_bar);
    mSendTokenOrg = (EditText) findViewById(R.id.forsta_login_org_get_token);
    mSendTokenUsername = (EditText) findViewById(R.id.forsta_login_username_get_token);
    mSendTokenButton = (Button) findViewById(R.id.forsta_get_token_button);

    mLoginSecurityCode = (EditText) findViewById(R.id.forsta_login_security_code);

    mLoginUsernameText = (EditText) findViewById(R.id.forsta_login_username);
    mLoginPasswordText = (EditText) findViewById(R.id.forsta_login_password);
    mSubmitButton = (Button) findViewById(R.id.forsta_login_submit_button);

    mSendTokenButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String org = mSendTokenOrg.getText().toString().trim();
        String username = mSendTokenUsername.getText().toString().trim();
        if (org.length() < 3 || username.length() < 3) {
          Toast.makeText(LoginActivity.this, "Invalid Organization or Username", Toast.LENGTH_LONG).show();
        } else {
          showProgressBar();
          ForstaPreferences.setForstaOrgName(getApplicationContext(),org);
          ForstaPreferences.setForstaUsername(getApplicationContext(), username);
          CCSMSendToken getToken = new CCSMSendToken();
          getToken.execute(org, username);
        }
      }
    });
    mSubmitButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String code = mLoginSecurityCode.getText().toString().trim();
        if (code.length() != 6) {
          Toast.makeText(LoginActivity.this, "Invalid security code", Toast.LENGTH_LONG).show();
        } else {
          showProgressBar();

          String token = code;
          CCSMLogin task = new CCSMLogin();

          task.execute(mLoginUsernameText.getText().toString().trim(), mLoginPasswordText.getText().toString().trim(), token);
        }
      }
    });
    // TODO Remove this option?
    mStandardLogin = (TextView) findViewById(R.id.forsta_login_standard_login);
    mStandardLogin.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showPasswordForm();
      }
    });

    mTryAgainButton = (TextView) findViewById(R.id.forsta_login_tryagain);
    mTryAgainButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ForstaPreferences.clearLogin(LoginActivity.this);
        showSendLinkForm();
      }
    });
  }

  private void showSendLinkForm() {
    ForstaPreferences.setForstaLoginPending(LoginActivity.this, false);
    mSendLinkFormContainer.setVisibility(View.VISIBLE);
    mLoginSubmitFormContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.VISIBLE);
    mPasswordFormContainer.setVisibility(View.GONE);
    hideProgressBar();
  }

  private void showVerifyForm() {
    mSendLinkFormContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.VISIBLE);
    mPasswordFormContainer.setVisibility(View.GONE);
    mLoginSubmitFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showPasswordForm() {
    ForstaPreferences.setForstaLoginPending(LoginActivity.this, false);
    mSendLinkFormContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.GONE);
    mPasswordFormContainer.setVisibility(View.VISIBLE);
    mLoginSubmitFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showProgressBar() {
    mLoginFormContainer.setVisibility(View.GONE);
    mLoginProgressBar.setVisibility(View.VISIBLE);
  }

  private void hideProgressBar() {
    mLoginFormContainer.setVisibility(View.VISIBLE);
    mLoginProgressBar.setVisibility(View.GONE);
  }

  private void finishLoginActivity() {
    Intent nextIntent = getIntent().getParcelableExtra("next_intent");
    if (nextIntent == null) {
      nextIntent = new Intent(LoginActivity.this, ConversationListActivity.class);
    }

    Intent intent = ForstaContactsSyncIntentService.newIntent(getApplicationContext());
    startService(intent);

    startActivity(nextIntent);
    finish();
  }

  private class CCSMSendToken extends AsyncTask<String, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(String... params) {
      String org = params[0];
      String uname = params[1];
      JSONObject response = CcsmApi.forstaSendToken(org.trim(), uname.trim());
      return response;
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      if (jsonObject.has("msg")) {
        try {
          // If we've requested a new login, we need to logout.
          // We want to clear login on
          ForstaPreferences.setForstaLoginPending(LoginActivity.this, true);
          showVerifyForm();
          Toast.makeText(LoginActivity.this, jsonObject.getString("msg"), Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
          Log.d(TAG, e.getMessage());
        }
      } else {
        showVerifyForm();
        Toast.makeText(LoginActivity.this, "Sorry an error has occurred.", Toast.LENGTH_LONG).show();
      }
    }
  }

  private class CCSMLogin extends AsyncTask<String, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(String... params) {
      String uname = params[0];
      String pass = params[1];
      String authtoken = params[2];
      String username = ForstaPreferences.getForstaUsername(getApplicationContext());
      authtoken = CcsmApi.parseLoginToken(authtoken);
      authtoken = username + ":" + authtoken;

      JSONObject token = CcsmApi.forstaLogin(LoginActivity.this, uname, pass, authtoken);
      return token;
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      if (jsonObject.has("token")) {
        finishLoginActivity();
      } else {
        hideProgressBar();
        Toast.makeText(LoginActivity.this, "Sorry. Invalid Authentication.", Toast.LENGTH_LONG).show();
      }
    }
  }
}
