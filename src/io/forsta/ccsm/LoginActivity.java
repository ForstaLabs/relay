package io.forsta.ccsm;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RegistrationActivity;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.List;

import io.forsta.ccsm.api.CcsmApi;
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
        getSupportActionBar().setTitle("Connect with Forsta");
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
                finishLoginActivity();
            }
        });

        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoginEmailText.length() < 5 || mLoginPasswordText.length() < 8) {
                    Toast.makeText(LoginActivity.this, "Invalid username or password", Toast.LENGTH_LONG).show();
                } else {
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
            Log.d(TAG, jsonObject.toString());
            if (jsonObject.has("token")) {
                finishLoginActivity();
            } else {
                Toast.makeText(LoginActivity.this, "Sorry. Invalid Authentication.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
