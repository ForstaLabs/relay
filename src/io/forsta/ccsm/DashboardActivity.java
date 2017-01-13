package io.forsta.ccsm;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.json.JSONObject;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.List;

import io.forsta.util.NetworkUtils;

public class DashboardActivity extends BaseActionBarActivity {
    private static final String TAG = DashboardActivity.class.getSimpleName();
    private TextView mDebugText;
    private RadioGroup mDebugType;
    private Button mLoginButton;
    private ToggleButton mDebugToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        initView();
    }

    private void initView() {
        mDebugText = (TextView) findViewById(R.id.debug_text);
        mDebugType = (RadioGroup) findViewById(R.id.dashboard_selection);
        mDebugType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.debug_radio1:
                        ApiContacts api = new ApiContacts();
                        api.execute();
                        break;
                    case R.id.debug_radio2:
                        RecipientsList task = new RecipientsList();
                        task.execute();
                        break;
                    case R.id.debug_radio3:
                        mDebugText.setText(printDirectory());
                        break;
//                    case R.id.debug_radio4:
//                        mDebugText.setText(printDirectory());
//                        break;
                    case R.id.debug_radio5:
                        mDebugText.setText(printTextSecureContacts());
                        break;
                    case R.id.debug_radio6:
                        mDebugText.setText(printSystemContacts());
                        break;
                }
            }
        });
        mLoginButton = (Button) findViewById(R.id.dashboard_login_button);
        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                Intent nextIntent = new Intent(DashboardActivity.this, DashboardActivity.class);
                intent.putExtra("next_intent", nextIntent);
                startActivity(intent);
                finish();
            }
        });
        mDebugToggle = (ToggleButton) findViewById(R.id.dashboard_toggle_debug);
        mDebugToggle.setChecked(ForstaPreferences.isCCSMDebug(DashboardActivity.this));
        mDebugToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ForstaPreferences.setCCSMDebug(DashboardActivity.this, mDebugToggle.isChecked());
            }
        });
    }

    private String printSystemContacts() {
        ContactsDatabase db = DatabaseFactory.getContactsDatabase(this);
        Cursor c = db.querySystemContacts(null);
        StringBuilder sb = new StringBuilder();
        sb.append("System Contacts: ").append(c.getCount()).append("\n");
        while (c.moveToNext()) {
            String[] cols = c.getColumnNames();
            for (int i=0;i < c.getColumnCount(); i++) {
                sb.append(c.getColumnName(i)).append(": ");
                try {
                    sb.append(c.getString(i)).append(" ");
                } catch (Exception e) {
                    sb.append(c.getInt(i)).append(" ");
                }
                sb.append("\n");
            }
        }
        c.close();

        return sb.toString();
    }

    private String printTextSecureContacts() {
        ContactsDatabase db = DatabaseFactory.getContactsDatabase(this);
        Cursor c = db.queryTextSecureContacts(null);
        StringBuilder sb = new StringBuilder();
        sb.append("TextSecure Contacts: ").append(c.getCount()).append("\n");
        while (c.moveToNext()) {
            String[] cols = c.getColumnNames();
            for (int i=0;i < c.getColumnCount(); i++) {
                sb.append(c.getColumnName(i)).append(": ");
                try {
                    sb.append(c.getString(i)).append(" ");
                } catch (Exception e) {
                    sb.append(c.getInt(i)).append(" ");
                }
                sb.append("\n");
            }
        }
        c.close();
        return sb.toString();
    }

    private Optional<RecipientPreferenceDatabase.RecipientsPreferences> getRecipientPreferences(long[] ids) {
        RecipientPreferenceDatabase rdb = DatabaseFactory.getRecipientPreferenceDatabase(this);
        return rdb.getRecipientsPreferences(ids);
    }

    private String printDirectory() {
        TextSecureDirectory dir = TextSecureDirectory.getInstance(this);
        Cursor cursor = dir.getAllNumbers();
        StringBuilder sb = new StringBuilder();
        try {
            while (cursor != null && cursor.moveToNext()) {
                for (int i=0;i<cursor.getColumnCount();i++) {
                    sb.append(cursor.getColumnName(i)).append(": ");
                    try {
                        sb.append(cursor.getString(i)).append(" ");
                    } catch(Exception e) {
                        sb.append("Bad value");
                    }
                }
                sb.append("\n");
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return sb.toString();
    }

    private List<String> getDirectoryActiveNumbers() {
        TextSecureDirectory dir = TextSecureDirectory.getInstance(this);
        return dir.getActiveNumbers();
    }

    private Recipients getDirectoryActiveRecipients(List<String> dirNumbers) {
        Recipients recipients = RecipientFactory.getRecipientsFromStrings(DashboardActivity.this, dirNumbers, false);
        return recipients;
    }

    private String printIdentities() {
        IdentityDatabase idb = DatabaseFactory.getIdentityDatabase(DashboardActivity.this);
        Cursor cdb = idb.getIdentities();
        StringBuilder sb = new StringBuilder();
        sb.append("\nIdentities\n");
        while (cdb.moveToNext()) {
            String[] cols = cdb.getColumnNames();
            for (int i=0;i < cdb.getColumnCount(); i++) {
                sb.append(cdb.getColumnName(i)).append(": ");
                try {
                    sb.append(cdb.getString(i)).append(" ");
                } catch(Exception e) {
                    sb.append(" bad value");
                }
            }
            sb.append("\n");
        }
        cdb.close();
        return sb.toString();
    }

//    private String printMessages() {
//        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(DashboardActivity.this);
//        SmsDatabase.Reader reader = database.getMessages(masterSecret, 0, 10);
//        SmsMessageRecord record;
//        StringBuilder sb = new StringBuilder();
//        while ((record = reader.getNext()) != null) {
//            sb.append(record.getDisplayBody().toString()).append("\n");
//        }
//        reader.close();
//        return sb.toString();
//    }

    private class ApiContacts extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
            return NetworkUtils.getApiData(DashboardActivity.this);
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            Log.d(TAG, "Response from API");
            mDebugText.setText(jsonObject.toString());
        }
    }

    private class RecipientsList extends AsyncTask<Void, Void, Recipients> {

        @Override
        protected Recipients doInBackground(Void... params) {
            List<String> dirNumbers = getDirectoryActiveNumbers();
            return getDirectoryActiveRecipients(dirNumbers);
        }

        @Override
        protected void onPostExecute(Recipients recipients) {
            List<Recipient> list = recipients.getRecipientsList();
            StringBuilder sb = new StringBuilder();
            for (Recipient item : list) {
                sb.append("Number: ").append(item.getNumber()).append(" ID: ").append(item.getRecipientId());
                sb.append(" Name: ").append(item.getName());
                sb.append("\n");
            }
            sb.append(printIdentities());
            mDebugText.setText(sb.toString());
        }
    }
}
