package io.forsta.ccsm;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RegistrationActivity;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import io.forsta.util.NetworkUtils;

public class LoginActivity extends BaseActionBarActivity {
    private static final String TAG = LoginActivity.class.getSimpleName();
    private Button mCancelButton;
    private Button mSubmitButton;
    private TextView mLoginEmailText;
    private TextView mLoginPasswordText;
    private TextView mLoginTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getSupportActionBar().setTitle(getString(R.string.RegistrationActivity_connect_with_signal));
        initializeView();
    }

    private void initializeView() {
        mLoginTitle = (TextView) findViewById(R.id.forsta_login_title);
        mCancelButton = (Button) findViewById(R.id.forsta_cancel_login);
        mSubmitButton = (Button) findViewById(R.id.forsta_submit_login);
        mLoginEmailText = (TextView) findViewById(R.id.forsta_login_email);
        mLoginPasswordText = (TextView) findViewById(R.id.forsta_login_password);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Contact CCSM server and attempt login at ccsm-api/api-auth
                // Response should contain the api key
//                TextSecurePreferences.setPromptedPushRegistration(RegistrationActivity.this, true);
//                Intent nextIntent = getIntent().getParcelableExtra("next_intent");

//                if (nextIntent == null) {
//                    nextIntent = new Intent(LoginActivity.this, RegistrationActivity.class);
//                }
                CCSMLogin task = new CCSMLogin();
                task.execute("jlewis@forsta.io", "Jdlewy33!");

//                Intent nextIntent = new Intent(LoginActivity.this, RegistrationActivity.class);
//                startActivity(nextIntent);
//                finish();
            }
        });
    }

    private class CCSMLogin extends AsyncTask<String, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            String uname = params[0];
            String pass = params[1];
            JSONObject token = NetworkUtils.ccsmLogin(uname, pass);
            return token;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            Log.d(TAG, jsonObject.toString());

        }
    }
}
