package io.forsta.ccsm;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RegistrationActivity;
import io.forsta.ccsm.api.CcsmApi;

public class LoginActivity extends BaseActionBarActivity {
    private static final String TAG = LoginActivity.class.getSimpleName();
    private Button mSubmitButton;
    private TextView mLoginEmailText;
    private TextView mLoginPasswordText;
    private TextView mLoginTitle;
    private ProgressBar mLoginProgressBar;
    private LinearLayout mFormContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getSupportActionBar().setTitle("Connect with Forsta");
        initializeView();
    }

    private void initializeView() {
        mFormContainer = (LinearLayout) findViewById(R.id.login_form_container);
        mLoginProgressBar = (ProgressBar) findViewById(R.id.login_progress_bar);
        mLoginTitle = (TextView) findViewById(R.id.forsta_login_title);
        mSubmitButton = (Button) findViewById(R.id.forsta_submit_login);
        mLoginEmailText = (TextView) findViewById(R.id.forsta_login_email);
        mLoginPasswordText = (TextView) findViewById(R.id.forsta_login_password);

        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoginEmailText.length() < 5 || mLoginPasswordText.length() < 8) {
                    Toast.makeText(LoginActivity.this, "Invalid username or password", Toast.LENGTH_LONG).show();
                } else {
                    mFormContainer.setVisibility(View.GONE);
                    mLoginProgressBar.setVisibility(View.VISIBLE);
                    CCSMLogin task = new CCSMLogin();
                    task.execute(mLoginEmailText.getText().toString(), mLoginPasswordText.getText().toString());
                }
            }
        });

    }

    private void finishLoginActivity() {
        Intent nextIntent = getIntent().getParcelableExtra("next_intent");
        if (nextIntent == null) {
            nextIntent = new Intent(LoginActivity.this, RegistrationActivity.class);
        }

        startActivity(nextIntent);
        finish();
    }

    private class CCSMLogin extends AsyncTask<String, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            String uname = params[0];
            String pass = params[1];
            JSONObject token = CcsmApi.forstaLogin(LoginActivity.this, uname, pass);
            return token;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            if (jsonObject.has("token")) {
                finishLoginActivity();
            } else {
                mLoginProgressBar.setVisibility(View.GONE);
                mFormContainer.setVisibility(View.VISIBLE);
                Toast.makeText(LoginActivity.this, "Sorry. Invalid Authentication.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
