package io.forsta.ccsm;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.StringBuilderPrinter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.attachments.DatabaseAttachment;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.database.CanonicalAddressDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.EncryptingSmsDatabase;
import io.forsta.securesms.database.IdentityDatabase;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.RecipientPreferenceDatabase;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.TextSecureDirectory;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.database.model.SmsMessageRecord;
import io.forsta.securesms.push.TextSecureCommunicationFactory;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.TextSecurePreferences;

public class DashboardActivity extends PassphraseRequiredActionBarActivity {
    private static final String TAG = DashboardActivity.class.getSimpleName();
    private TextView mDebugText;
    private TextView mLoginInfo;
    private CheckBox mToggleSyncMessages;
    private Button mChangeNumberButton;
    private Button mResetNumberButton;
    private MasterSecret mMasterSecret;
    private Spinner mSpinner;
    private LinearLayout mChangeNumberContainer;
    private ScrollView mScrollView;
    private EditText mSyncNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
        mMasterSecret = masterSecret;
        setContentView(R.layout.activity_dashboard);
        initView();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuInflater inflater = new MenuInflater(DashboardActivity.this);
        menu.clear();
        inflater.inflate(R.menu.dashboard, menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_dashboard_logout: {
                ForstaPreferences.clearPreferences(DashboardActivity.this);
                startLoginIntent();
                break;
            }

            case R.id.menu_dashboard_token_refresh: {
                RefreshApiToken refresh = new RefreshApiToken();
                refresh.execute();
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void initView() {
        mChangeNumberContainer = (LinearLayout) findViewById(R.id.dashboard_change_number_container);
        mScrollView = (ScrollView) findViewById(R.id.dashboard_scrollview);
        mSyncNumber = (EditText) findViewById(R.id.dashboard_sync_number);
        mLoginInfo = (TextView) findViewById(R.id.dashboard_login_info);
        mDebugText = (TextView) findViewById(R.id.debug_text);
        mChangeNumberButton = (Button) findViewById(R.id.dashboard_change_number_button);
        mChangeNumberButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ForstaPreferences.setForstaSyncNumber(DashboardActivity.this, mSyncNumber.getText().toString());
                printLoginInformation();
                Toast.makeText(DashboardActivity.this, "Sync number changed.", Toast.LENGTH_LONG).show();
            }
        });

        mResetNumberButton = (Button) findViewById(R.id.dashboard_reset_number_button);
        mResetNumberButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ForstaPreferences.setForstaSyncNumber(DashboardActivity.this, "");
                Toast.makeText(DashboardActivity.this, "Sync number reset.", Toast.LENGTH_LONG).show();
                mSyncNumber.setText(BuildConfig.FORSTA_SYNC_NUMBER);
                printLoginInformation();
            }
        });
        mSpinner = (Spinner) findViewById(R.id.dashboard_selector);
        List<String> options = new ArrayList<String>();
        options.add("Choose an option");
        options.add("Sync Contacts");
        options.add("Address Database");
        options.add("TextSecure Recipients");
        options.add("TextSecure Directory");
        options.add("Contacts");
        options.add("SMS and MMS Messages");
        options.add("SMS Messages");



        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    showChangeNumber();
                } else {
                    showScrollView();
                }
                switch (position) {
                    case 0:
                        printLoginInformation();
                        break;
                    case 1:
                        FetchContacts refresh = new FetchContacts();
                        refresh.execute();
                        break;
                    case 2:
                        GetAddressDatabase getAddresses = new GetAddressDatabase();
                        getAddresses.execute();
                        break;
                    case 3:
                        GetRecipientsList task = new GetRecipientsList();
                        task.execute();
                        break;
                    case 4:
                        mDebugText.setText(printDirectory());
                        break;
                    case 5:
                        mDebugText.setText(printSystemContacts());
                        break;
                    case 6:
                        GetMessages getMessages = new GetMessages();
                        getMessages.execute();
                        break;
                    case 7:
                        mDebugText.setText(printSmsMessages());
                        break;


                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

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

    private void showScrollView() {
        mScrollView.setVisibility(View.VISIBLE);
        mChangeNumberContainer.setVisibility(View.GONE);
    }

    private void showChangeNumber() {
        mScrollView.setVisibility(View.GONE);
        mChangeNumberContainer.setVisibility(View.VISIBLE);
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
        String smNumber = !debugSyncNumber.equals("") ? debugSyncNumber : BuildConfig.FORSTA_SYNC_NUMBER;

        mSyncNumber.setText(smNumber);
        StringBuilder sb = new StringBuilder();
        String lastLogin = ForstaPreferences.getRegisteredDateTime(DashboardActivity.this);
        sb.append("Sync Number: Build: ");
        sb.append(BuildConfig.FORSTA_SYNC_NUMBER);
        sb.append(" Current: ").append(smNumber);
        sb.append("\n");
        sb.append("Last Login: ");
        sb.append(lastLogin);
        Date tokenExpire = ForstaPreferences.getTokenExpireDate(DashboardActivity.this);
        sb.append("\n");
        sb.append("Token Expires: ");
        sb.append(tokenExpire);
        mLoginInfo.setText(sb.toString());
    }

    private String printSystemContacts() {
        ContactsDatabase db = DatabaseFactory.getContactsDatabase(this);
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
        };
        String  sort = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

        Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, sort);
//        Cursor c = db.querySystemContacts(null);
        StringBuilder sb = new StringBuilder();
        sb.append("System Contacts: ").append(c.getCount()).append("\n");
        sb.append("Count: ").append(c.getCount()).append("\n");
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
        sb.append("Count: ").append(c.getCount()).append("\n");
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

    private String printDirectory() {
        TextSecureDirectory dir = TextSecureDirectory.getInstance(this);
        Cursor cursor = dir.getAllNumbers();
        StringBuilder sb = new StringBuilder();
        sb.append("Count: ").append(cursor.getCount()).append("\n");
        try {
            while (cursor != null && cursor.moveToNext()) {
                for (int i=0;i<cursor.getColumnCount();i++) {
                    if (!cursor.getColumnName(i).equals("timestamp") && !cursor.getColumnName(i).equals("relay") && !cursor.getColumnName(i).equals("voice")) {
                        sb.append(cursor.getColumnName(i)).append(": ");
                        try {
                            sb.append(cursor.getString(i)).append(" ");
                        } catch(Exception e) {
                            sb.append("Bad value");
                        }
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
            AttachmentDatabase adb = DatabaseFactory.getAttachmentDatabase(DashboardActivity.this);

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
                    List<DatabaseAttachment> attachments = adb.getAttachmentsForMessage(record.getId());

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
                    sb.append("Attachments:");
                    for (DatabaseAttachment item: attachments) {
                        sb.append(item.getDataUri()).append(" ");
                    }
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
                sb.append("ThreadId: ");
                sb.append(record.getThreadId());
                sb.append("\n");
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

    private class GetAddressDatabase extends AsyncTask<Void, Void, Map<String, Long>> {

        @Override
        protected Map<String, Long> doInBackground(Void... voids) {
            CanonicalAddressDatabase db = CanonicalAddressDatabase.getInstance(DashboardActivity.this);
            Map<String, Long> vals = db.addressCache;

            return vals;
        }

        @Override
        protected void onPostExecute(Map<String, Long> addresses) {
            StringBuilder sb = new StringBuilder();
            for (String number : addresses.keySet()) {
                sb.append(number).append(" ");
                sb.append(addresses.get(number));
                sb.append("\n");
            }
            mDebugText.setText(sb.toString());
        }
    }

    private class FetchContacts extends AsyncTask<Void, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Void... voids) {
            try {
                CcsmApi.syncForstaContacts(DashboardActivity.this);
                DirectoryHelper.refreshDirectory(DashboardActivity.this, mMasterSecret);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            mDebugText.setText(printTextSecureContacts());
        }
    }
}

