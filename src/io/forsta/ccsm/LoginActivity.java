package io.forsta.ccsm;

import android.content.Intent;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.concurrent.Executor;

import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.BaseActionBarActivity;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.R;
import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.util.Util;

public class LoginActivity extends BaseActionBarActivity implements View.OnClickListener, Executor {
  private static final String TAG = LoginActivity.class.getSimpleName();
  private LinearLayout mLoginFormContainer;
  private LinearLayout mSendLinkFormContainer;
  private LinearLayout mVerifyFormContainer;
  private LinearLayout passwordAuthContainer;
  private LinearLayout mAccountFormContainer;
  private TextView mLoginTitle;
  private EditText mSendTokenUsername;
  private EditText mSendTokenOrg;
  private EditText mLoginSecurityCode;
  private EditText mAccountFullName;
  private EditText mAccountTagSlug;
  private EditText mAccountPhone;
  private EditText mAccountEmail;
  private EditText mAccountPassword;
  private EditText mPassword;
  private ProgressBar mLoginProgressBar;
  private LinearLayout createAccountContainer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME);
    initializeView();
    initializeListeners();
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
    passwordAuthContainer = (LinearLayout) findViewById(R.id.forsta_login_password_container);

    mSendTokenOrg = (EditText) findViewById(R.id.forsta_login_org_get_token);
    mSendTokenUsername = (EditText) findViewById(R.id.forsta_login_username_get_token);
    mLoginSecurityCode = (EditText) findViewById(R.id.forsta_login_security_code);
    mAccountFullName = (EditText) findViewById(R.id.forsta_login_account_fullname);
    mAccountTagSlug = (EditText) findViewById(R.id.forsta_login_account_tag_slug);
    mAccountPhone = (EditText) findViewById(R.id.forsta_login_account_phone);
    mAccountEmail = (EditText) findViewById(R.id.forsta_login_account_email);
    mAccountPassword = (EditText) findViewById(R.id.forsta_login_account_password);
    mPassword = (EditText) findViewById(R.id.forsta_login_password);

    createAccountContainer = (LinearLayout) findViewById(R.id.create_account_button_container);

    ForstaUser user = ForstaUser.getLocalForstaUser(LoginActivity.this);
    if (user != null) {
      createAccountContainer.setVisibility(View.GONE);
    }
  }

  private void initializeListeners() {
    mAccountFullName.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void afterTextChanged(Editable editable) {
        String slug = ForstaUtils.slugify(editable.toString());
        mAccountTagSlug.setText(slug);
      }
    });


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
          ForstaPreferences.setForstaOrgName(getApplicationContext(), org);
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
          CCSMLogin task = new CCSMLogin(true);
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
    submitAccountButton.setOnClickListener(this);
//    submitAccountButton.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View view) {
//        // Need validation checking on form values.
//        showProgressBar();
//
//        String fullName = mAccountFullName.getText().toString().trim();
//        String tagSlug = mAccountTagSlug.getText().toString().trim();
//        String phone = mAccountPhone.getText().toString().trim();
//        String email = mAccountEmail.getText().toString().trim();
//        String password = mAccountPassword.getText().toString().trim();
//        if (fullName.length() < 1) {
//          Toast.makeText(LoginActivity.this, "Please enter a name", Toast.LENGTH_LONG).show();
//          return;
//        }
//        if (tagSlug.length() < 1) {
//          Toast.makeText(LoginActivity.this, "Please enter a username", Toast.LENGTH_LONG).show();
//          return;
//        }
//
//        if (password.length() < 8) {
//          Toast.makeText(LoginActivity.this, "Please enter a valid password", Toast.LENGTH_LONG).show();
//          return;
//        }
//
//        try {
//          if (phone.length() < 10) {
//            throw new InvalidNumberException("Too short");
//          }
//          phone = Util.canonicalizeNumberE164(phone);
//        } catch (InvalidNumberException e) {
//          Toast.makeText(LoginActivity.this, "Invalid phone number. Please enter full number, including area code.", Toast.LENGTH_LONG).show();
//          return;
//        }
//        hideProgressBar();
////        showProgressBar();
////        JoinAccountTask joinTask = new JoinAccountTask();
////        joinTask.execute(fullName, tagSlug, phone, email, password);
//      }
//    });

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

    Button loginWithPasswordButton = (Button) findViewById(R.id.forsta_login_password_submit_button);
    loginWithPasswordButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (TextUtils.isEmpty(mPassword.getText().toString())) {
          Toast.makeText(LoginActivity.this, "Please enter a password.", Toast.LENGTH_LONG).show();
          return;
        }
        showProgressBar();
        CCSMLogin loginTask = new CCSMLogin(false);
        loginTask.execute(mPassword.getText().toString());
      }
    });
  }

  private void showSendLinkForm() {
    ForstaPreferences.setForstaLoginPending(LoginActivity.this, false);
    mVerifyFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    passwordAuthContainer.setVisibility(View.GONE);
    mSendLinkFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showVerifyForm() {
    mSendLinkFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    passwordAuthContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showAuthPassword() {
    ForstaPreferences.setForstaLoginPending(LoginActivity.this, false);
    mVerifyFormContainer.setVisibility(View.GONE);
    mAccountFormContainer.setVisibility(View.GONE);
    mSendLinkFormContainer.setVisibility(View.GONE);
    passwordAuthContainer.setVisibility(View.VISIBLE);
    hideProgressBar();
  }

  private void showAccountForm() {
    ForstaPreferences.setForstaLoginPending(LoginActivity.this, false);
    mSendLinkFormContainer.setVisibility(View.GONE);
    mVerifyFormContainer.setVisibility(View.GONE);
    passwordAuthContainer.setVisibility(View.GONE);
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

  @Override
  public void onClick(View v) {
    SafetyNet.getClient(this).verifyWithRecaptcha(BuildConfig.RECAPTCHA_KEY)
        .addOnSuccessListener((Executor) this,
            new OnSuccessListener<SafetyNetApi.RecaptchaTokenResponse>() {
              @Override
              public void onSuccess(SafetyNetApi.RecaptchaTokenResponse response) {
                // Indicates communication with reCAPTCHA service was
                // successful.
                String userResponseToken = response.getTokenResult();
                if (!userResponseToken.isEmpty()) {
                  // Validate the user response token using the
                  // reCAPTCHA siteverify API.
                }
              }
            })
        .addOnFailureListener((Executor) this, new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            if (e instanceof ApiException) {
              // An error occurred when communicating with the
              // reCAPTCHA service. Refer to the status code to
              // handle the error appropriately.
              ApiException apiException = (ApiException) e;
              int statusCode = apiException.getStatusCode();
              Log.d(TAG, "Error: " + CommonStatusCodes
                  .getStatusCodeString(statusCode));
            } else {
              // A different, unknown type of error occurred.
              Log.d(TAG, "Error: " + e.getMessage());
            }
          }
        });
  }

  @Override
  public void execute(@NonNull Runnable command) {
    command.run();
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
      } else if (jsonObject.has("error")) {
        try {
          if (jsonObject.getString("error").equals("409")) {
            ForstaPreferences.setForstaLoginPending(LoginActivity.this, true);
            showAuthPassword();
          } else {
            hideProgressBar();
            Toast.makeText(LoginActivity.this, "Sorry an error has occurred.", Toast.LENGTH_LONG).show();
          }
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private class CCSMLogin extends AsyncTask<String, Void, JSONObject> {
    private boolean isSms = true;

    public CCSMLogin(boolean isSms) {
      this.isSms = isSms;
    }

    @Override
    protected JSONObject doInBackground(String... params) {
      String auth = params[0];
      String username = ForstaPreferences.getForstaUsername(getApplicationContext());
      String org = ForstaPreferences.getForstaOrgName(getApplicationContext());
      JSONObject authObj = new JSONObject();
      try {
        if (isSms) {
          auth = org + ":" + username + ":" + auth;
          authObj.put("authtoken", auth);
        } else {
          authObj.put("tag_slug", username + ":" + org);
          authObj.put("password", auth);
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }

      JSONObject token = CcsmApi.forstaLogin(LoginActivity.this, authObj);
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
      return CcsmApi.accountJoin(jsonObject);
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

  private class JoinAccountTask extends AsyncTask<String, Void, JSONObject> {
    @Override
    protected JSONObject doInBackground(String... strings) {
      JSONObject jsonObject = new JSONObject();
      try {
        jsonObject.put("full_name", strings[0]);
        jsonObject.put("tag_slug", strings[1]);
        jsonObject.put("phone", strings[2]);
        jsonObject.put("email", strings[3]);
        jsonObject.put("captcha", strings[3]);
      } catch (JSONException e) {
        e.printStackTrace();
      }
      return CcsmApi.accountJoin(jsonObject);
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
