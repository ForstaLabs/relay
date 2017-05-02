package io.forsta.ccsm;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
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

import org.json.JSONObject;
import org.whispersystems.libsignal.util.guava.Optional;

import io.forsta.ccsm.api.ForstaGroup;
import io.forsta.ccsm.api.ForstaUser;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.attachments.DatabaseAttachment;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.crypto.MasterCipher;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.database.CanonicalAddressDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.EncryptingSmsDatabase;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.IdentityDatabase;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.TextSecureDirectory;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.database.model.SmsMessageRecord;
import io.forsta.securesms.database.model.ThreadRecord;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.util.GroupUtil;

public class DashboardActivity extends PassphraseRequiredActionBarActivity {
  private static final String TAG = DashboardActivity.class.getSimpleName();
  private TextView mDebugText;
  private TextView mLoginInfo;
  private CheckBox mToggleSyncMessages;
  private Button mChangeNumberButton;
  private Button mResetNumberButton;
  private MasterSecret mMasterSecret;
  private MasterCipher mMasterCipher;
  private Spinner mSpinner;
  private LinearLayout mChangeNumberContainer;
  private ScrollView mScrollView;
  private EditText mSyncNumber;

  @Override
  protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
    mMasterSecret = masterSecret;
    mMasterCipher = new MasterCipher(mMasterSecret);
    setContentView(R.layout.activity_dashboard);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
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
    options.add("Canonical Address Db");
    options.add("TextSecure Recipients");
    options.add("TextSecure Directory");
    options.add("TextSecure Contacts");
    options.add("System Contact Data");
    options.add("SMS and MMS Message Threads");
    options.add("SMS Messages");
    options.add("Forsta Contacts");
    options.add("Groups");
    options.add("Get API Users");
    options.add("Get API Groups");

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
            GetAddressDatabase getAddresses = new GetAddressDatabase();
            getAddresses.execute();
            break;
          case 2:
            GetRecipientsList task = new GetRecipientsList();
            task.execute();
            break;
          case 3:
            mDebugText.setText(printDirectory());
            break;
          case 4:
            mDebugText.setText(printTextSecureContacts());
            break;
          case 5:
            mDebugText.setText(printAllContacts());
            break;
          case 6:
            GetMessages getMessages = new GetMessages();
            getMessages.execute();
            break;
          case 7:
            mDebugText.setText(printSmsMessages());
            break;
          case 8:
            mDebugText.setText(printForstaContacts());
            break;
          case 9:
            mDebugText.setText(printGroups());
            break;
          case 10:
            GetTagUsers tagTask = new GetTagUsers();
            tagTask.execute();
            break;
          case 11:
            GetTagGroups groupTask = new GetTagGroups();
            groupTask.execute();
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
    Cursor c = db.querySystemContacts(null);
    StringBuilder sb = new StringBuilder();
    sb.append("System Contacts: ").append(c.getCount()).append("\n");
    while (c.moveToNext()) {
      String[] cols = c.getColumnNames();
      for (int i = 0; i < c.getColumnCount(); i++) {
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

  private String printAllContacts() {
    String[] projection = new String[]{
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Data._ID,
        ContactsContract.Data.RAW_CONTACT_ID,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.Data.DATA1,
        ContactsContract.Data.MIMETYPE
    };
    String[] proj = new String[]{
        ContactsContract.RawContacts._ID,
        ContactsContract.RawContacts.CONTACT_ID,
        ContactsContract.RawContacts.ACCOUNT_NAME,
        ContactsContract.RawContacts.ACCOUNT_TYPE,
        ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.RawContacts.DATA_SET,
        ContactsContract.RawContacts.DISPLAY_NAME_ALTERNATIVE,
        ContactsContract.RawContacts.DISPLAY_NAME_SOURCE,
        ContactsContract.RawContacts.SYNC1,
        ContactsContract.RawContacts.SYNC2
    };

    Uri currentContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, "Forsta")
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, "io.forsta.securesms")
        .build();
    String qs = ContactsContract.Data.MIMETYPE + " = ?";
    String[] q = new String[] {"vnd.android.cursor.item/name"};
    String notDeleted = ContactsContract.RawContacts.DELETED + "<>1";

    Cursor c = getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI, null, notDeleted, null, null);
    StringBuilder sb = new StringBuilder();
    sb.append("Records: ").append(c.getCount()).append("\n");
    while (c.moveToNext()) {
      String[] cols = c.getColumnNames();
      for (int i = 0; i < c.getColumnCount(); i++) {
        sb.append(c.getColumnName(i)).append(": ");
        try {
          sb.append(c.getString(i)).append(" ");
        } catch (Exception e) {
          sb.append(c.getInt(i)).append(" ");
        }
        sb.append("\n");
      }
      sb.append("\n");
    }
    c.close();

    return sb.toString();
  }

  private void removeContact(String id) {
    ArrayList<ContentProviderOperation> operations        = new ArrayList<>();
    operations.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI.buildUpon()
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, "Forsta")
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, "io.forsta.securesms")
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
        .withYieldAllowed(true)
        .withSelection(BaseColumns._ID + " = ?", new String[] {id})
        .build());
  }

  private String printTextSecureContacts() {
    ContactsDatabase db = DatabaseFactory.getContactsDatabase(this);
    Cursor c = db.queryTextSecureContacts(null);
    StringBuilder sb = new StringBuilder();
    sb.append("TextSecure Contacts: ").append(c.getCount()).append("\n");
    while (c.moveToNext()) {
      String[] cols = c.getColumnNames();
      for (int i = 0; i < c.getColumnCount(); i++) {
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

  private String printGroups() {
    GroupDatabase db = DatabaseFactory.getGroupDatabase(getApplicationContext());
    Cursor cursor = db.getForstaGroups();
    StringBuilder sb = new StringBuilder();
    while (cursor.moveToNext()) {
      int cols = cursor.getColumnCount();
      for (int i=0; i<cols; i++) {
        sb.append(cursor.getColumnName(i)).append(": ");
        sb.append(cursor.getString(i));
        sb.append("\n");
      }
    }
    cursor.close();
    return sb.toString();
  }

  private String printForstaContacts() {
    ContactDb db = DbFactory.getContactDb(getApplicationContext());
    Cursor cursor = db.get();
    StringBuilder sb = new StringBuilder();
    while (cursor.moveToNext()) {
      int cols = cursor.getColumnCount();
      for (int i=0; i<cols; i++) {
        sb.append(cursor.getColumnName(i)).append(": ");
        sb.append(cursor.getString(i));
        sb.append("\n");
      }
    }
    cursor.close();
    return sb.toString();
  }

  private String printDirectory() {
    TextSecureDirectory dir = TextSecureDirectory.getInstance(this);
    Cursor cursor = dir.getAllNumbers();
    StringBuilder sb = new StringBuilder();
    sb.append("Count: ").append(cursor.getCount()).append("\n");
    try {
      while (cursor != null && cursor.moveToNext()) {
        for (int i = 0; i < cursor.getColumnCount(); i++) {
          if (!cursor.getColumnName(i).equals("timestamp") && !cursor.getColumnName(i).equals("relay") && !cursor.getColumnName(i).equals("voice")) {
            sb.append(cursor.getColumnName(i)).append(": ");
            try {
              sb.append(cursor.getString(i)).append(" ");
            } catch (Exception e) {
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
      for (int i = 1; i < cdb.getColumnCount(); i++) {
        sb.append(cdb.getColumnName(i)).append(": ");
        try {
          sb.append(cdb.getString(i)).append("\n");
        } catch (Exception e) {
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
    ThreadDatabase tdb = DatabaseFactory.getThreadDatabase(DashboardActivity.this);
    AttachmentDatabase adb = DatabaseFactory.getAttachmentDatabase(DashboardActivity.this);
    GroupDatabase gdb = DatabaseFactory.getGroupDatabase(DashboardActivity.this);
    Cursor ccursor = tdb.getConversationList();
    ThreadDatabase.Reader treader = tdb.readerFor(ccursor, mMasterCipher);
    ThreadRecord trecord;
    while ((trecord = treader.getNext()) != null) {
      long tId = trecord.getThreadId();
      sb.append("Thread: ");
      sb.append(tId);
      sb.append("\n");
      sb.append("Thread Message: ").append("\n");
      sb.append(trecord.getDisplayBody().toString());
      sb.append("\n");
      Recipients trecipients = trecord.getRecipients();

      if (trecipients.isGroupRecipient()) {
        String groupId = trecipients.getPrimaryRecipient().getNumber();
        sb.append("Group Recipients").append("\n");
        sb.append("Group ID: ").append(groupId).append("\n");
        Log.d(TAG, "TextSecure Group: " + groupId);
        try {
          byte[] id = GroupUtil.getDecodedId(groupId);
          Log.d(TAG, "Decoded Group: " + id);
          GroupDatabase.GroupRecord groupRec = gdb.getGroup(id);
          Recipients rec = gdb.getGroupMembers(id, true);

          if (groupRec != null) {
            sb.append("Name: ").append(groupRec.getTitle()).append("\n");

            List<Recipient> groupRecipients = rec.getRecipientsList();
            for (Recipient recipient : groupRecipients) {
              sb.append(recipient.getNumber()).append("\n");
            }
          } else {
            sb.append("No Group Found.").append("\n");
          }

        } catch (IOException e) {
          e.printStackTrace();
        }


      } else {
        sb.append("Recipients").append("\n");
        for (Recipient rec : trecipients.getRecipientsList()) {
          sb.append(rec.getNumber()).append("\n");
        }
      }

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
        List<Recipient> recipList = recipients.getRecipientsList();
        List<DatabaseAttachment> attachments = adb.getAttachmentsForMessage(record.getId());
        sb.append("Group Update: ").append(record.isGroupUpdate());
        sb.append("\n");
        sb.append("Group Action: ").append(record.isGroupAction());
        sb.append("\n");
        sb.append("Group Quit: ").append(record.isGroupQuit());
        sb.append("\n");
        sb.append("Message Recipients: ").append("\n");
        for (Recipient r : recipList) {
          sb.append(r.getNumber()).append(" ");
        }
        sb.append("\n");
        sb.append("Primary Recipient: ");
        sb.append(recipients.getPrimaryRecipient().getNumber());
        sb.append("\n");
        sb.append("Date: ");
        sb.append(dt.toString());
        sb.append("\n");
        sb.append("Message: ");
        sb.append(body.toString());
        sb.append("\n");
        sb.append("Attachments:");
        for (DatabaseAttachment item : attachments) {
          sb.append(item.getDataUri()).append(" ");
        }
        sb.append("\n");
        sb.append("\n");
      }
      sb.append("\n");
      reader.close();
    }
    sb.append("\n");

    treader.close();

    return sb.toString();
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

  private class GetTagGroups extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return CcsmApi.getTags(DashboardActivity.this);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      List<ForstaGroup> groups = CcsmApi.parseTagGroups(jsonObject);

      StringBuilder sb = new StringBuilder();
      for (ForstaGroup group : groups) {
        String groupId = group.id;
        sb.append(groupId).append("\n");
        String encoded = GroupUtil.getEncodedId(groupId.getBytes());
        sb.append("Encoded Group DB ID: ").append(encoded).append("\n");
        try {
          byte[] decoded = GroupUtil.getDecodedId(encoded);
          sb.append(decoded).append("\n");
        } catch (IOException e) {
          e.printStackTrace();
        }
        sb.append(group.description).append("\n");

        for (String number : group.getGroupNumbers()) {
          sb.append(number).append("\n");
        }
        sb.append("\n");
      }
      mDebugText.setText(sb.toString());
    }
  }

  private class GetTagUsers extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return CcsmApi.getUsers(DashboardActivity.this);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      List<ForstaUser> contacts = CcsmApi.parseUsers(jsonObject);
      StringBuilder sb = new StringBuilder();
      for (ForstaUser user : contacts) {
        sb.append(user.phone).append(" ");
        sb.append(user.getName()).append("\n");
      }
      mDebugText.setText(sb.toString());
    }
  }

  private class GetGroups extends AsyncTask<Void, Void, List<GroupDatabase.GroupRecord>> {

    @Override
    protected List<GroupDatabase.GroupRecord> doInBackground(Void... voids) {
      GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(DashboardActivity.this);
      GroupDatabase.Reader reader = groupDb.getGroups();
      GroupDatabase.GroupRecord record;
      List<GroupDatabase.GroupRecord> results = new ArrayList<>();
      while ((record = reader.getNext()) != null) {
        results.add(record);
      }
      reader.close();
      return results;
    }

    @Override
    protected void onPostExecute(List<GroupDatabase.GroupRecord> groupRecords) {
      StringBuilder sb = new StringBuilder();
      for (GroupDatabase.GroupRecord rec : groupRecords) {
        sb.append("Title: ").append(rec.getTitle()).append("\n");
        sb.append("ID: ").append(rec.getEncodedId()).append("\n");
        sb.append("TagId: ").append(new String(rec.getId())).append("\n");
        sb.append("Active: ").append(rec.isActive()).append("\n");
        sb.append("Members:").append("\n");
        List<String> numbers = rec.getMembers();
        for (String num : numbers) {
          sb.append(num).append("\n");
        }
        sb.append("\n");
      }
      mDebugText.setText(sb.toString());
    }
  }
}

