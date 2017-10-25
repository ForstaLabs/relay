package io.forsta.ccsm;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.api.model.ForstaJWT;
import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.database.model.ForstaGroup;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.util.InvalidMessagePayloadException;
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
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.TextSecureDirectory;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.model.DisplayRecord;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.database.model.SmsMessageRecord;
import io.forsta.securesms.database.model.ThreadRecord;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.util.DateUtils;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;


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
  private Button messageTester;

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
      case R.id.menu_dashboard_clear_directory: {
        handleClearDirectory();
        break;
      }
      case R.id.menu_dashboard_clear_threads: {
        handleClearThreads();
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
    socketUtils = WebSocketUtils.getInstance(DashboardActivity.this, this);
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
    options.add("Control and Ccsm Sync messages.");
    options.add("Threads");
    options.add("Forsta Contacts");
    options.add("Groups");
    options.add("Get API Users");
    options.add("Get API Groups");
    options.add("Get Directory");

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
//            GetMessages getMessages = new GetMessages();
//            getMessages.execute();
            mDebugText.setText(printMediaMessages());
            break;
          case 5:
            mDebugText.setText(printThreads());
            break;
          case 6:
            mDebugText.setText(printForstaContacts());
            break;
          case 7:
            mDebugText.setText(printGroups());
            break;
          case 8:
            mDebugText.setText("");
            mProgressBar.setVisibility(View.VISIBLE);
            GetTagUsers tagTask = new GetTagUsers();
            tagTask.execute();
            break;
          case 9:
            mDebugText.setText("");
            mProgressBar.setVisibility(View.VISIBLE);
            GetTagGroups groupTask = new GetTagGroups();
            groupTask.execute();
            break;
          case 10:
            mDebugText.setText("");
            GetDirectory directory = new GetDirectory();
            directory.execute();
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
    messageTester = (Button) findViewById(R.id.message_tests_button);
    messageTester.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mDebugText.setText("");
        showScrollView();
        new MessageTests().execute();
      }
    });
    printLoginInformation();
  }

  private void showScrollView() {
    mScrollView.setVisibility(View.VISIBLE);
  }

  private void handleClearThreads() {
    new AlertDialog.Builder(DashboardActivity.this)
        .setTitle("Confirm")
        .setMessage("Are you sure?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            ThreadDatabase db = DatabaseFactory.getThreadDatabase(DashboardActivity.this);
            db.deleteAllConversations();
            Toast.makeText(DashboardActivity.this, "All threads deleted", Toast.LENGTH_LONG).show();
          }
        }).show();


  }

  private void handleClearDirectory() {
    new AlertDialog.Builder(DashboardActivity.this)
        .setTitle("Confirm")
        .setMessage("Are you sure?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            ContactDb db = DbFactory.getContactDb(DashboardActivity.this);
            db.removeAll();
            GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(DashboardActivity.this);
            groupDb.removeAllGroups();
            Toast.makeText(DashboardActivity.this, "All contacts and groups deleted", Toast.LENGTH_LONG).show();
          }
        }).show();
  }

  private void startLoginIntent() {
    Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
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

    ForstaUser user = ForstaUser.getLocalForstaUser(DashboardActivity.this);
    sb.append("Org Id: ");
    sb.append(user.org_id);
    sb.append("\n");
    sb.append("User Id: ");
    sb.append(user.uid);
    sb.append("\n");
    sb.append("Tag Id: ");
    sb.append(user.tag_id);
    sb.append("\n");
    sb.append("Slug: ");
    sb.append(user.slug);
    sb.append("\n");
    sb.append("Phone: ");
    sb.append(user.phone);
    sb.append("\n");
    sb.append("Device ID: ");
    sb.append(TextSecurePreferences.getLocalDeviceId(DashboardActivity.this));
    sb.append("\n");

    mLoginInfo.setText(sb.toString());
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
    SmsDatabase smsDb = DatabaseFactory.getSmsDatabase(DashboardActivity.this);
    sb.append("SMS message count:" + smsDb.getMessageCount()).append("\n");
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

  private String printMediaMessages() {
    MmsDatabase db = DatabaseFactory.getMmsDatabase(DashboardActivity.this);
    Cursor cursor = db.getMessages(-1);
    MmsDatabase.Reader reader = db.readerFor(mMasterSecret, cursor);
    MessageRecord messageRecord;
    StringBuilder sb = new StringBuilder();
    while ((messageRecord = reader.getNext()) != null) {
      String displayDate = DateUtils.formatDateTime(DashboardActivity.this, messageRecord.getTimestamp(), android.text.format.DateUtils.FORMAT_ABBREV_TIME);
      try {
        ForstaMessage message = ForstaMessage.fromMessagBodyString(messageRecord.getBody().getBody());
      } catch (InvalidMessagePayloadException e) {
        Log.w(TAG, "Bad message payload");
      }
      sb.append(messageRecord.getId())
          .append(" ")
          .append(displayDate)
          .append("\n");
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
      Recipients trecipients = trecord.getRecipients();

      if (trecipients.isGroupRecipient()) {
        String groupId = trecipients.getPrimaryRecipient().getNumber();
        sb.append("Group Recipients").append("\n");
        sb.append("Group ID: ").append(groupId).append("\n");
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
        String rawBody = record.getBody().getBody();
        long timestamp = record.getTimestamp();
        Date dt = new Date(timestamp);
        List<Recipient> recipList = recipients.getRecipientsList();
        List<DatabaseAttachment> attachments = adb.getAttachmentsForMessage(record.getId());
        sb.append("Expiration Timer: ").append(record.isExpirationTimerUpdate());
        sb.append("\n");
        sb.append("Key Exchange: ").append(record.isBundleKeyExchange());
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
        sb.append(rawBody);
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

  private class GetDirectory extends AsyncTask<Void, Void, JSONObject> {

    @Override
    protected JSONObject doInBackground(Void... voids) {
      return CcsmApi.getUserDirectory(DashboardActivity.this, null);
    }

    @Override
    protected void onPostExecute(JSONObject jsonObject) {
      mDebugText.setText(jsonObject.toString());
    }
  }

  private class MessageTests extends AsyncTask<Void, String, Void> {

    @Override
    protected Void doInBackground(Void... params) {
      publishProgress("Bad JSON blob test.");
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("");
        publishProgress("Failed: empty string.");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("{}");
        publishProgress("Failed: empty object.");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }

      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("[]");
        publishProgress("Failed: empty array");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      // No version object
      publishProgress("Bad version object test");
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("[{virgin: 1}]");
        publishProgress("Failed: empty array");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      publishProgress("Bad messageType object test");
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("[{version: 1, threadId: 1]}");
        publishProgress("Failed: invalid content type");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("[{version: 1, threadId: 1, messageType: blank}]");
        publishProgress("Failed: invalid content type");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception: " + e.getMessage());
      }
      // No distribution
      publishProgress("Bad distribution object");
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("[{version: 1, threadId: 1, messageType: content}]");
        publishProgress("Failed: no distribution object");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("[{version: 1, threadId: 1, messageType: content, distribution: {}}]");
        publishProgress("Failed: empty distribution object");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }

      publishProgress("Bad distribution expression object");
      try {
        ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString("[{version: 1, threadId: 1, messageType: content, distribution: {expression: ''}}]");
        publishProgress("Failed: empty distribution expression");
      } catch (InvalidMessagePayloadException e) {
        publishProgress("Caught Exception. Body: " + e.getMessage());
      }

      // Go through all existing messages and verify JSON.
      ThreadDatabase tdb = DatabaseFactory.getThreadDatabase(DashboardActivity.this);
      MmsSmsDatabase mdb = DatabaseFactory.getMmsSmsDatabase(DashboardActivity.this);
      Cursor ccursor = tdb.getConversationList();
      ThreadDatabase.Reader treader = tdb.readerFor(ccursor, mMasterCipher);
      ThreadRecord trecord;
      publishProgress("Verifying existing message records.");
      while ((trecord = treader.getNext()) != null) {
        Cursor mcursor = mdb.getConversation(trecord.getThreadId());
        MmsSmsDatabase.Reader mreader = mdb.readerFor(mcursor, mMasterSecret);
        MessageRecord mrecord;
        while ((mrecord = mreader.getNext()) != null) {
          try {
            ForstaMessage forstaMessage = ForstaMessage.fromMessagBodyString(mrecord.getBody().getBody());
            publishProgress("Type:" + forstaMessage.getMessageType().name() + " UID:" + forstaMessage.getThreadUId() + " ID:" + mrecord.getId());
          } catch (InvalidMessagePayloadException e) {
            publishProgress(e.getMessage()  + ": " + mrecord.getBody().getBody());
          }
        }
      }
      publishProgress("Complete. Verified message records.");
      // Create malformed message blobs and verify handling.
      // No JSON blob

      return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
      mDebugText.append(values[0] + "\n");
    }

    @Override
    protected void onPostExecute(Void v) {
      mDebugText.append("Complete");
    }
  }
}

