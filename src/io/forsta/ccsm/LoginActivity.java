package io.forsta.ccsm;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
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
import io.forsta.securesms.BaseActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.RegistrationActivity;
import io.forsta.ccsm.api.CcsmApi;

public class LoginActivity extends BaseActionBarActivity {
    private static final String TAG = LoginActivity.class.getSimpleName();
    private static final String IS_PENDING = "pending";
    private boolean mPending = false;
    private Button mSubmitButton;
    private Button mSendTokenButton;
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
        getSupportActionBar().setTitle("Connect with Forsta");
        if (savedInstanceState != null && savedInstanceState.getBoolean(IS_PENDING)) {
            mPending = savedInstanceState.getBoolean(IS_PENDING);
        }
        initializeView();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_PENDING, mPending);
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
                if (mSendTokenOrg.getText().length() < 3 || mSendTokenUsername.getText().length() < 3) {
                    Toast.makeText(LoginActivity.this, "Invalid Organization or Username", Toast.LENGTH_LONG).show();
                } else {
                    showProgressBar();
                    CCSMSendToken getToken = new CCSMSendToken();
                    getToken.execute(mSendTokenOrg.getText().toString(), mSendTokenUsername.getText().toString());
                }
            }
        });
        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoginSecurityCode.getText().length() < 3) {
                    Toast.makeText(LoginActivity.this, "Invalid security code", Toast.LENGTH_LONG).show();
                } else {
                    showProgressBar();
                    CCSMLogin task = new CCSMLogin();
                    task.execute(mLoginUsernameText.getText().toString(), mLoginPasswordText.getText().toString(), mLoginSecurityCode.getText().toString());
                }
            }
        });

        mStandardLogin = (TextView) findViewById(R.id.forsta_login_standard_login);
        mStandardLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSendLinkFormContainer.setVisibility(View.GONE);
                mVerifyFormContainer.setVisibility(View.GONE);
                mPasswordFormContainer.setVisibility(View.VISIBLE);
                mLoginSubmitFormContainer.setVisibility(View.VISIBLE);
                mLoginFormContainer.setVisibility(View.VISIBLE);
            }
        });

        if (mPending) {
            mSendLinkFormContainer.setVisibility(View.GONE);
            mLoginSubmitFormContainer.setVisibility(View.VISIBLE);
        }
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
            nextIntent = new Intent(LoginActivity.this, RegistrationActivity.class);
        }

        startActivity(nextIntent);
        finish();
    }

    private class CCSMSendToken extends AsyncTask<String, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            String org = params[0];
            String uname = params[1];
            JSONObject response = CcsmApi.forstaSendToken(org, uname);
            return response;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            hideProgressBar();
            if (jsonObject.has("msg")) {
                try {
                    mPending = true;
                    mSendLinkFormContainer.setVisibility(View.GONE);
                    mLoginSubmitFormContainer.setVisibility(View.VISIBLE);
                    Toast.makeText(LoginActivity.this, jsonObject.getString("msg"), Toast.LENGTH_LONG).show();
                } catch (JSONException e) {
                    Log.d(TAG, e.getMessage());
                }
            } else {
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
            if (authtoken.contains("/")) {
                String[] parts = authtoken.split("/");
                authtoken = parts[parts.length-1];
            }

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
