package io.forsta.ccsm;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.zxing.common.StringUtils;

import org.json.JSONObject;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.util.NetworkUtils;

public class DashboardActivity extends PassphraseRequiredActionBarActivity {
    private static final String TAG = DashboardActivity.class.getSimpleName();
    private TextView mDebugText;
    private Button mLoginButton;
    private Button mTokenRefresh;
    private ToggleButton mDebugToggle;
    private CheckBox mToggleSyncMessages;
    private MasterSecret mMasterSecret;
    private Spinner mSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
        mMasterSecret = masterSecret;
        setContentView(R.layout.activity_dashboard);
        initView();
    }

    private void initView() {
        mDebugText = (TextView) findViewById(R.id.debug_text);
        mSpinner = (Spinner) findViewById(R.id.dashboard_selector);
        List<String> options = new ArrayList<String>();
        options.add("Select An Option");
        options.add("API Test");
        options.add("TextSecure Recipients");
        options.add("TextSecure Directory");
        options.add("SMS Messages");
        options.add("TextSecure Contacts");
        options.add("All Contacts");
        options.add("MMS (Group) Messages");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 1:
                        ApiContacts api = new ApiContacts();
                        api.execute();
                        break;
                    case 2:
                        RecipientsList task = new RecipientsList();
                        task.execute();
                        break;
                    case 3:
                        mDebugText.setText(printDirectory());
                        break;
                    case 4:
                        mDebugText.setText(printMessages());
                        break;
                    case 5:
                        mDebugText.setText(printTextSecureContacts());
                        break;
                    case 6:
                        mDebugText.setText(printSystemContacts());
                        break;
                    case 7:
                        mDebugText.setText(printAllMessages());
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

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
        StringBuilder sb = new StringBuilder();
        String token = ForstaPreferences.getRegisteredKey(DashboardActivity.this);
        String lastLogin = ForstaPreferences.getRegisteredDateTime(DashboardActivity.this);
        sb.append("Last Login:\n");
        sb.append(lastLogin);
        sb.append("\n");
        sb.append("\n");
        sb.append("Current Token Size:");
        sb.append(token.length());
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

    private String printMmsMessages() {
        MmsDatabase database = DatabaseFactory.getMmsDatabase(DashboardActivity.this);
        ThreadDatabase threadDb = DatabaseFactory.getThreadDatabase(DashboardActivity.this);
        Cursor cursor = threadDb.getConversationList();
        StringBuilder sb = new StringBuilder();
        while(cursor.moveToNext()) {
            for (int i=0; i<cursor.getColumnCount();i++) {
                sb.append(cursor.getColumnName(i)).append(": ");
                sb.append(cursor.getString(i)).append("\n");
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private String printGroupMessages(Cursor cursor) {
        MessageRecord record;
        MmsSmsDatabase.Reader reader = DatabaseFactory.getMmsSmsDatabase(DashboardActivity.this).readerFor(cursor, mMasterSecret);
        StringBuilder sb = new StringBuilder();
        while ((record = reader.getNext()) != null) {
            Recipient recipient = record.getIndividualRecipient();
            Recipients recipients = record.getRecipients();
            long threadId = record.getThreadId();
            CharSequence body = record.getDisplayBody();
            long timestamp = record.getTimestamp();
            Date dt = new Date(timestamp);

            sb.append("Primary Recipient: ");
            sb.append(recipient.getNumber());
            sb.append("\n");
            sb.append("Date: ");
            sb.append(dt.toString());
            sb.append("\n");
            sb.append("Message: ");
            sb.append(body.toString());
            sb.append("\n");
            sb.append("\n");
        }
        cursor.close();
        reader.close();
        return sb.toString();
    }

    private String printAllMessages() {
        StringBuilder sb = new StringBuilder();
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
                    Recipient recipient = record.getIndividualRecipient();
                    Recipients recipients = record.getRecipients();
                    long threadId = record.getThreadId();
                    CharSequence body = record.getDisplayBody();
                    long timestamp = record.getTimestamp();
                    Date dt = new Date(timestamp);

                    sb.append("Primary Recipient: ");
                    sb.append(recipient.getNumber());
                    sb.append("\n");
                    sb.append("Date: ");
                    sb.append(dt.toString());
                    sb.append("\n");
                    sb.append("Message: ");
                    sb.append(body.toString());
                    sb.append("\n");
                    sb.append("\n");
                }
                cursor.close();
                reader.close();
            }

            return sb.toString();
        }

        return sb.toString();
    }

    private String printMmsSmsMessages() {
        StringBuilder sb = new StringBuilder();
        if (mMasterSecret != null) {
            ThreadDatabase tdb = DatabaseFactory.getThreadDatabase(DashboardActivity.this);
            MmsSmsDatabase database = DatabaseFactory.getMmsSmsDatabase(DashboardActivity.this);
            Cursor c = tdb.getConversationList();
            List<Long> list = new ArrayList<>();
            while (c.moveToNext()) {
                list.add(c.getLong(0));
            }
            c.close();
            for (Long l : list) {
                Cursor mmsCursor = database.getConversation(l);
                while(mmsCursor.moveToNext()) {
                    for (int i = 0; i< mmsCursor.getColumnCount(); i++) {
                    sb.append(mmsCursor.getColumnName(i)).append(": ");
                    sb.append(mmsCursor.getString(i));
                    sb.append("\n");
                }
                sb.append("\n\n");
                }
                mmsCursor.close();
            }
            sb.append("\n\n");
        }

        return sb.toString();
    }

    private String printMessages() {
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

    private class ApiContacts extends AsyncTask<Void, Void, JSONObject> {

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

    private class RefreshApiToken extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... params) {
            return CcsmApi.forstaRefreshToken(DashboardActivity.this);
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            Log.d(TAG, jsonObject.toString());

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

