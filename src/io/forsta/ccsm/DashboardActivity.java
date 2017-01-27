package io.forsta.ccsm;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.json.JSONObject;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import io.forsta.ccsm.api.CcsmApi;

public class DashboardActivity extends PassphraseRequiredActionBarActivity {
    private static final String TAG = DashboardActivity.class.getSimpleName();
    private TextView mDebugText;
    private Button mLoginButton;
    private Button mLogoutButton;
    private Button mTokenRefresh;
    private ToggleButton mDebugToggle;
    private CheckBox mToggleSyncMessages;
    private MasterSecret mMasterSecret;
    private Spinner mSpinner;
    private EditText mSyncNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
        mMasterSecret = masterSecret;
        setContentView(R.layout.activity_dashboard);
        initView();
    }

    private void initView() {
        mSyncNumber = (EditText) findViewById(R.id.dashboard_sync_number);
        mDebugText = (TextView) findViewById(R.id.debug_text);
        mSpinner = (Spinner) findViewById(R.id.dashboard_selector);
        List<String> options = new ArrayList<String>();
        options.add("Login Information");
        options.add("API Test");
        options.add("TextSecure Recipients");
        options.add("TextSecure Directory");
        options.add("SMS and MMS Messages");
        options.add("TextSecure Contacts");
        options.add("All Contacts");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        printLoginInformation();
                        break;
                    case 1:
                        GetApiContacts api = new GetApiContacts();
                        api.execute();
                        break;
                    case 2:
                        GetRecipientsList task = new GetRecipientsList();
                        task.execute();
                        break;
                    case 3:
                        mDebugText.setText(printDirectory());
                        break;
                    case 4:
                        GetMessages getMessages = new GetMessages();
                        getMessages.execute();
                        break;
                    case 5:
                        mDebugText.setText(printTextSecureContacts());
                        break;
                    case 6:
                        mDebugText.setText(printSystemContacts());
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                printLoginInformation();
            }
        });

        mLogoutButton = (Button) findViewById(R.id.dashboard_logout_button);
        mLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ForstaPreferences.clearPreferences(DashboardActivity.this);
                startLoginIntent();
            }
        });
        mLoginButton = (Button) findViewById(R.id.dashboard_login_button);
        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLoginIntent();
            }
        });

        mTokenRefresh = (Button) findViewById(R.id.dashboard_refresh_token);
        mTokenRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RefreshApiToken refresh = new RefreshApiToken();
                refresh.execute();
            }
        });

        mToggleSyncMessages = (CheckBox) findViewById(R.id.dashboard_toggle_sync_messages);
        mToggleSyncMessages.setChecked(ForstaPreferences.isCCSMDebug(DashboardActivity.this));
        mToggleSyncMessages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ForstaPreferences.setCCSMDebug(DashboardActivity.this, mToggleSyncMessages.isChecked());
            }
        });
        printLoginInformation();
    }

    private void startLoginIntent() {
        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
        Intent nextIntent = new Intent(DashboardActivity.this, DashboardActivity.class);
        intent.putExtra("next_intent", nextIntent);
        startActivity(intent);
        finish();
    }

    private void printLoginInformation() {
        String debugSyncNumber = ForstaPreferences.getForstaSyncNumber(DashboardActivity.this);
        String smNumber = debugSyncNumber != "" ? debugSyncNumber : BuildConfig.FORSTA_SYNC_NUMBER;

        mSyncNumber.setText(smNumber);
        StringBuilder sb = new StringBuilder();
        String lastLogin = ForstaPreferences.getRegisteredDateTime(DashboardActivity.this);
        sb.append("Forsta Sync Number: ");
        sb.append(BuildConfig.FORSTA_SYNC_NUMBER);
        sb.append("\n");
        sb.append("Last Login: ");
        sb.append(lastLogin);
        Date tokenExpire = ForstaPreferences.getTokenExpireDate(DashboardActivity.this);
        sb.append("\n");
        sb.append("Token Expires: ");
        sb.append(tokenExpire);
        mDebugText.setText(sb.toString());
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
            for (int i=1;i < cdb.getColumnCount(); i++) {
                sb.append(cdb.getColumnName(i)).append(": ");
                try {
                    sb.append(cdb.getString(i)).append("\n");
                } catch(Exception e) {
                    sb.append(" bad value");
                }
            }
            sb.append("\n");
        }
        cdb.close();
        return sb.toString();
    }

    private String printAllMessages() {
        StringBuilder sb = new StringBuilder();
        List<Pair<Date, String>> list = getMessages();
        for (Pair<Date, String> record : list) {
            sb.append(record.second);
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<Pair<Date, String>> getMessages() {
        List<Pair<Date, String>> messageList = new ArrayList<>();
        if (mMasterSecret != null) {
            ThreadDatabase tdb = DatabaseFactory.getThreadDatabase(DashboardActivity.this);
            Cursor cc = tdb.getConversationList();
            List<Long> list = new ArrayList<>();
            while (cc.moveToNext()) {
                list.add(cc.getLong(0));
            }
            cc.close();
            for (long tId : list) {
                Cursor cursor = DatabaseFactory.getMmsSmsDatabase(DashboardActivity.this).getConversation(tId);
                MessageRecord record;
                MmsSmsDatabase.Reader reader = DatabaseFactory.getMmsSmsDatabase(DashboardActivity.this).readerFor(cursor, mMasterSecret);

                while ((record = reader.getNext()) != null) {
                    StringBuilder sb = new StringBuilder();
                    Recipient recipient = record.getIndividualRecipient();
                    Recipients recipients = record.getRecipients();
                    long threadId = record.getThreadId();
                    CharSequence body = record.getDisplayBody();
                    long timestamp = record.getTimestamp();
                    Date dt = new Date(timestamp);
                    List<Recipient> recipList = recipients.getRecipientsList();
                    sb.append("ThreadId: ");
                    sb.append(threadId);
                    sb.append("\n");
                    sb.append("Recipients: ");
                    for (Recipient r : recipList) {
                        sb.append(r.getNumber()).append(" ");
                    }
                    sb.append("\n");
                    sb.append("Primary Recipient: ");
                    sb.append(recipient.getNumber());
                    sb.append("\n");
                    sb.append("Date: ");
                    sb.append(dt.toString());
                    sb.append("\n");
                    sb.append("Message: ");
                    sb.append(body.toString());
                    sb.append("\n");
                    messageList.add(new Pair(dt, sb.toString()));
                }
                cursor.close();
                reader.close();
            }
            Collections.sort(messageList, new Comparator<Pair<Date, String>>() {
                @Override
                public int compare(Pair<Date, String> lhs, Pair<Date, String> rhs) {
                    return rhs.first.compareTo(lhs.first);
                }
            });
        }
        return messageList;
    }

    private String printSmsMessages() {
        if (mMasterSecret != null) {
            EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(DashboardActivity.this);
            SmsDatabase.Reader reader = database.getMessages(mMasterSecret, 0, 50);
            SmsMessageRecord record;
            StringBuilder sb = new StringBuilder();
            while ((record = reader.getNext()) != null) {
                Date sent = new Date(record.getDateSent());
                Date received = new Date(record.getDateReceived());
                SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yy h:mm a");

                Recipients recipients = record.getRecipients();
                List<Recipient> rlist = recipients.getRecipientsList();

                sb.append("Sent: ");
                sb.append(formatter.format(sent)).append("\n");
                sb.append("To: ");
                for (Recipient r : rlist) {
                    sb.append(r.getNumber()).append(" ");
                    sb.append("ID: ");
                    sb.append(r.getRecipientId()).append(" ");
                }
                sb.append("\n");
                sb.append("Received: ");
                sb.append(formatter.format(received)).append(" ");
                sb.append("\n");
                sb.append("Message: ");
                sb.append(record.getDisplayBody().toString());
                sb.append("\n");
                sb.append("\n");
            }
            reader.close();
            return sb.toString();
        }
        return "MasterSecret NULL";
    }

    private class GetApiContacts extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
            return CcsmApi.getContacts(DashboardActivity.this);
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            Log.d(TAG, "Response from API");
            mDebugText.setText(jsonObject.toString());
        }
    }

    private class GetRecipientsList extends AsyncTask<Void, Void, Recipients> {

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

    private class RefreshApiToken extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
            return CcsmApi.forstaRefreshToken(DashboardActivity.this);
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            Log.d(TAG, jsonObject.toString());
            printLoginInformation();
        }
    }

    private class GetMessages extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            return printAllMessages();
        }

        @Override
        protected void onPostExecute(String s) {
            mDebugText.setText(s);
        }
    }

    private class GetDirectory extends AsyncTask<Void, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(Void... params) {
            SignalServiceAccountManager accountManager = TextSecureCommunicationFactory.createManager(DashboardActivity.this);
            try {
                List<DeviceInfo> devices = accountManager.getDevices();
                Log.d(TAG, "Devices");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            super.onPostExecute(jsonObject);
        }
    }
}

