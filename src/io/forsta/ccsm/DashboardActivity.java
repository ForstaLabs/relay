package io.forsta.ccsm;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.Spanned;
import android.util.Log;
import android.util.Pair;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.api.ForstaJWT;
import io.forsta.ccsm.database.model.ForstaGroup;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.ccsm.util.WebSocketUtils;
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


// TODO Remove all of this code for production release. This is for discovery and debug use.
public class DashboardActivity extends PassphraseRequiredActionBarActivity implements WebSocketUtils.MessageCallback{
  private static final String TAG = DashboardActivity.class.getSimpleName();
  private TextView mDebugText;
  private TextView mLoginInfo;
  private CheckBox mToggleSyncMessages;
  private MasterSecret mMasterSecret;
  private MasterCipher mMasterCipher;
  private Spinner mSpinner;
  private Spinner mConfigSpinner;
  private ScrollView mScrollView;
  private ProgressBar mProgressBar;
  private WebSocketUtils socketUtils;
  private Button socketTester;

  @Override
  protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
    mMasterSecret = masterSecret;
    mMasterCipher = new MasterCipher(mMasterSecret);
    setContentView(R.layout.activity_dashboard);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
    initView();
    initSocket();
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
        ForstaPreferences.clearLogin(DashboardActivity.this);
        startLoginIntent();
        break;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (socketUtils.socketOpen) {
      socketTester.setText("Close socket");
    } else {
      socketTester.setText("Open socket");
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (socketUtils.socketOpen) {
      socketUtils.disconnect();
    }
  }

  private void initSocket() {
    socketUtils = new WebSocketUtils(DashboardActivity.this, this);
  }

  private void initView() {
    socketTester = (Button) findViewById(R.id.socket_tester);
    socketTester.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!socketUtils.socketOpen) {
          socketUtils.connect();
          socketTester.setText("Close Socket");
        } else {
          socketUtils.disconnect();
          socketTester.setText("Open Socket");
        }
      }
    });
    mProgressBar = (ProgressBar) findViewById(R.id.dashboard_progress_bar);
    mScrollView = (ScrollView) findViewById(R.id.dashboard_scrollview);
    mLoginInfo = (TextView) findViewById(R.id.dashboard_login_info);
    mDebugText = (TextView) findViewById(R.id.debug_text);
    mSpinner = (Spinner) findViewById(R.id.dashboard_selector);
    List<String> options = new ArrayList<String>();
    options.add("Choose an option");
    options.add("Canonical Address Db");
    options.add("TextSecure Recipients");
    options.add("TextSecure Directory");
    options.add("TextSecure Contacts");
    options.add("System Contact RawContacts");
    options.add("System Contact Data");
    options.add("SMS and MMS Message Threads");
    options.add("Threads");
    options.add("Forsta Contacts");
    options.add("Groups");
    options.add("Get API Users");
    options.add("Get API Groups");

    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options);
    mSpinner.setAdapter(adapter);
    mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position != 0) {
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
            mDebugText.setText(printAllRawContacts());
            break;
          case 6:
            mDebugText.setText(printAllContactData());
            break;
          case 7:
            GetMessages getMessages = new GetMessages();
            getMessages.execute();
            break;
          case 8:
            mDebugText.setText(printThreads());
            break;
          case 9:
            mDebugText.setText(printForstaContacts());
            break;
          case 10:
            mDebugText.setText(printGroups());
            break;
          case 11:
            mDebugText.setText("");
            mProgressBar.setVisibility(View.VISIBLE);
            GetTagUsers tagTask = new GetTagUsers();
            tagTask.execute();
            break;
          case 12:
            mDebugText.setText("");
            mProgressBar.setVisibility(View.VISIBLE);
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
  }

  private void startLoginIntent() {
    Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
//    Intent nextIntent = new Intent(DashboardActivity.this, DashboardActivity.class);
//    intent.putExtra("next_intent", nextIntent);
    startActivity(intent);
    finish();
  }

  private void printLoginInformation() {
    StringBuilder sb = new StringBuilder();
    String lastLogin = ForstaPreferences.getRegisteredDateTime(DashboardActivity.this);
    String token = ForstaPreferences.getRegisteredKey(getApplicationContext());
    ForstaJWT jwt = new ForstaJWT(token);

    sb.append("API Host:");
    sb.append(BuildConfig.FORSTA_API_URL);
    sb.append("\n");
    sb.append("Sync Number:");
    sb.append(BuildConfig.FORSTA_SYNC_NUMBER);
    Date tokenExpire = jwt.getExpireDate();
    sb.append("\n");
    sb.append("Token Expires: ");
    sb.append(tokenExpire);
    sb.append("\n");

    String forstaUser = ForstaPreferences.getForstaUser(DashboardActivity.this);
    try {
      JSONObject data = new JSONObject(forstaUser);
      ForstaUser user = new ForstaUser(data);
      sb.append("Org Id: ");
      sb.append(user.org_id);
      sb.append("\n");
      sb.append("User Id: ");
      sb.append(user.uid);
      sb.append("\n");
      sb.append("Tag Id: ");
      sb.append(user.tag_id);
      sb.append("\n");
      sb.append("Phone: ");
      sb.append(user.phone);
      sb.append("\n");
    } catch (JSONException e) {
      e.printStackTrace();
    }

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

  private String printAllRawContacts() {
    Cursor c = getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI, null, null, null, null);
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

  private String printAllContactData() {
    String qs = ContactsContract.Data.MIMETYPE + " = ?";
    String[] q = new String[] {"vnd.android.cursor.item/name"};
    String notDeleted = ContactsContract.RawContacts.DELETED + "<>1";

    Cursor c = getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, null, null, null);
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
    Cursor cursor = db.getForstaGroups("");
    StringBuilder sb = new StringBuilder();
    while (cursor.moveToNext()) {
      int cols = cursor.getColumnCount();
      for (int i=0; i<cols; i++) {
        sb.append(cursor.getColumnName(i)).append(": ");
        try {
          sb.append(cursor.getString(i));
        } catch (Exception e) {

        }
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

  private String printThreads() {
    StringBuilder sb = new StringBuilder();
    ThreadDatabase tdb = DatabaseFactory.getThreadDatabase(DashboardActivity.this);
    Cursor cursor = tdb.getConversationList();
    while (cursor != null && cursor.moveToNext()) {
      for (int i=0; i<cursor.getColumnCount(); i++) {
        sb.append(cursor.getColumnName(i)).append(": ");
        sb.append(cursor.getString(i)).append("\n");
      }
      sb.append("\n");
    }
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

  @Override
  public void onMessage(String message) {
    showScrollView();
    mDebugText.setText(message);
  }

  @Override
  public void onStatusChanged(boolean connected) {
    if (!connected) {
      socketTester.setText("Open socket");
    } else {
      socketTester.setText("Close socket");
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

        for (String number : group.members) {
          sb.append(number).append("\n");
        }
        sb.append("\n");
      }
      mDebugText.setText(sb.toString());
      mProgressBar.setVisibility(View.GONE);
    }
  }

  private class GetTagUsers extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return CcsmApi.getUsers(DashboardActivity.this);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      List<ForstaUser> contacts = CcsmApi.parseUsers(getApplicationContext(), jsonObject);
      StringBuilder sb = new StringBuilder();
      for (ForstaUser user : contacts) {
        sb.append(user.phone).append(" ");
        sb.append(user.email).append(" ");
        sb.append(user.username).append("\n");
      }
      mDebugText.setText(sb.toString());
      mProgressBar.setVisibility(View.GONE);
    }
  }
}

