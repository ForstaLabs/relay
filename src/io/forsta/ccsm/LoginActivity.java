package io.forsta.ccsm;

import android.content.Intent;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.securesms.BaseActionBarActivity;
import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.R;
import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.util.Util;

public class LoginActivity extends BaseActionBarActivity {
  private static final String TAG = LoginActivity.class.getSimpleName();
  private LinearLayout mLoginFormContainer;
  private LinearLayout mSendLinkFormContainer;
  private LinearLayout mVerifyFormContainer;
  private LinearLayout mAccountFormContainer;

  private TextView mLoginTitle;

  private EditText mSendTokenUsername;
  private EditText mSendTokenOrg;
  private EditText mLoginSecurityCode;
  private EditText mAccountFirstName;
  private EditText mAccountLastName;
  private EditText mAccountPhone;
  private EditText mAccountEmail;

  private ProgressBar mLoginProgressBar;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME);
    initializeView();
    initializeButtonListeners();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mSendTokenOrg.setText(ForstaPreferences.getForstaOrgName(this));
    mSendTokenUsername.setText(ForstaPreferences.getForstaUsername(this));
    // TODO we can listen for SMS messages and respond, without the user having to type in the authemtication code manually.
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
    mLoginTitle = (TextView) findViewById(R.id.forsta_login_title);
    mLoginProgressBar = (ProgressBar) findViewById(R.id.login_progress_bar);

    mLoginFormContainer = (LinearLayout) findViewById(R.id.forsta_login_container);
    mSendLinkFormContainer = (LinearLayout) findViewById(R.id.forsta_login_send_link_container);
    mVerifyFormContainer = (LinearLayout) findViewById(R.id.forsta_login_verify_token_container);
    mAccountFormContainer = (LinearLayout) findViewById(R.id.forsta_login_create_account_container);

    mSendTokenOrg = (EditText) findViewById(R.id.forsta_login_org_get_token);
    mSendTokenUsername = (EditText) findViewById(R.id.forsta_login_username_get_token);
    mLoginSecurityCode = (EditText) findViewById(R.id.forsta_login_security_code);
    mAccountFirstName = (EditText) findViewById(R.id.forsta_login_account_firstname);
    mAccountLastName = (EditText) findViewById(R.id.forsta_login_account_lastname);
    mAccountPhone = (EditText) findViewById(R.id.forsta_login_account_phone);
    mAccountEmail = (EditText) findViewById(R.id.forsta_login_account_email);
  }

  private void initializeButtonListeners() {
    Button getTokenButton = (Button) findViewById(R.id.forsta_get_token_button);
    getTokenButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String org = mSendTokenOrg.getText().toString().trim();
        String username = mSendTokenUsername.getText().toString().trim();
        if (org.length() < 1 || username.length() < 1) {
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

    Button submitTokenButton = (Button) findViewById(R.id.forsta_login_submit_button);
    submitTokenButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String code = mLoginSecurityCode.getText().toString().trim();
        if (code.length() != 6) {
          Toast.makeText(LoginActivity.this, "Invalid security code", Toast.LENGTH_LONG).show();
        } else {
          showProgressBar();
          CCSMLogin task = new CCSMLogin();
          task.execute(code);
        }
      }
    });
    Button createAccountButton = (Button) findViewById(R.id.forsta_create_account_button);
    createAccountButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showAccountForm();
      }
    });

    Button submitAccountButton = (Button) findViewById(R.id.forsta_login_account_submit_button);
    submitAccountButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // Need validation checking on form values.
        String first = mAccountFirstName.getText().toString().trim();
        String last = mAccountLastName.getText().toString().trim();
        String phone = mAccountPhone.getText().toString().trim();
        String email = mAccountEmail.getText().toString().trim();
        if (first.length() < 1) {
          Toast.makeText(LoginActivity.this, "Please enter a first name", Toast.LENGTH_LONG).show();
          return;
        }
        if (last.length() < 1) {
          Toast.makeText(LoginActivity.this, "Please enter a last name", Toast.LENGTH_LONG).show();
          return;
        }

        try {
          if (phone.length() < 10) {
            throw new InvalidNumberException("Too short");
          }
          phone = Util.canonicalizeNumberE164(phone);
        } catch (InvalidNumberException e) {
          Toast.makeText(LoginActivity.this, "Invalid phone number. Please enter full number, including area code.", Toast.LENGTH_LONG).show();
          return;
        }
        showProgressBar();
        CCSMCreateAccount createAccountTask = new CCSMCreateAccount();
        createAccountTask.execute(first, last, phone, email);
      }
    });

    Button cancelAccountButton = (Button) findViewById(R.id.forsta_login_account_cancel_button);
    cancelAccountButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showSendLinkForm();
      }
    });

    TextView tryAgainText = (TextView) findViewById(R.id.forsta_login_tryagain);
    tryAgainText.setPaintFlags(tryAgainText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    tryAgainText.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ForstaPreferences.clearLogin(LoginActivity.this);
        showSendLinkForm();
      }
    });
  }

  private void showSendLinkForm() {
    ForstaPreferences.setForstaLoginPending(LoginActivity.this, false);
    mVerifyFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    mSendLinkFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showVerifyForm() {
    mSendLinkFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showAccountForm() {
    ForstaPreferences.setForstaLoginPending(LoginActivity.this, false);
    mSendLinkFormContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.VISIBLE);
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

    startActivity(nextIntent);
    finish();
  }

  private class CCSMSendToken extends AsyncTask<String, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(String... params) {
      String org = params[0];
      String uname = params[1];
      JSONObject response = CcsmApi.forstaSendToken(getApplicationContext(), org.trim(), uname.trim());
      return response;
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      if (jsonObject.has("msg")) {
        try {
          ForstaPreferences.setForstaLoginPending(LoginActivity.this, true);
          showVerifyForm();
          Toast.makeText(LoginActivity.this, jsonObject.getString("msg"), Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
          Log.d(TAG, e.getMessage());
        }
      } else {
        hideProgressBar();
        Toast.makeText(LoginActivity.this, "Sorry an error has occurred.", Toast.LENGTH_LONG).show();
      }
    }
  }

  private class CCSMLogin extends AsyncTask<String, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(String... params) {
      String authtoken = params[0];
      String username = ForstaPreferences.getForstaUsername(getApplicationContext());
      String org = ForstaPreferences.getForstaOrgName(getApplicationContext());
      authtoken = CcsmApi.parseLoginToken(authtoken);
      authtoken = org + ":" + username + ":" + authtoken;

      JSONObject token = CcsmApi.forstaLogin(LoginActivity.this, authtoken);
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

  private class CCSMCreateAccount extends AsyncTask<String, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(String... strings) {
      JSONObject jsonObject = new JSONObject();
      try {
        jsonObject.put("first_name", strings[0]);
        jsonObject.put("last_name", strings[1]);
        jsonObject.put("phone", strings[2]);
        jsonObject.put("email", strings[3]);
      } catch (JSONException e) {
        e.printStackTrace();
      }
      return CcsmApi.createAccount(jsonObject);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      if (jsonObject.has("id")) {
        try {
          String username = jsonObject.getString("username");
          JSONObject orgObject = jsonObject.getJSONObject("org");
          String orgSlug = orgObject.getString("slug");
          ForstaPreferences.setForstaUsername(LoginActivity.this, username);
          ForstaPreferences.setForstaOrgName(LoginActivity.this, orgSlug);
          showVerifyForm();
        } catch (JSONException e) {
          hideProgressBar();
          Toast.makeText(LoginActivity.this, "Sorry. An error has occurred.", Toast.LENGTH_LONG).show();
        }
      } else {
        Toast.makeText(LoginActivity.this, "Sorry. An error has occurred.", Toast.LENGTH_LONG).show();
      }
    }
  }
}
