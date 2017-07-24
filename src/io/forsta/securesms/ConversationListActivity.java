/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.securesms;

import android.accounts.Account;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncStatusObserver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.location.places.ui.PlacePicker;

import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.forsta.ccsm.DirectoryFragment;
import io.forsta.ccsm.DrawerFragment;
import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.LoginActivity;
import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.database.model.ForstaRecipient;
import io.forsta.ccsm.api.ForstaSyncAdapter;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.audio.AudioRecorder;
import io.forsta.securesms.components.AttachmentTypeSelector;
import io.forsta.securesms.components.ComposeText;
import io.forsta.securesms.components.InputAwareLayout;
import io.forsta.securesms.components.InputPanel;
import io.forsta.securesms.components.KeyboardAwareLinearLayout;
import io.forsta.securesms.components.emoji.EmojiDrawer;
import io.forsta.securesms.components.location.SignalPlace;
import io.forsta.securesms.components.reminder.ReminderView;
import io.forsta.securesms.contacts.ContactAccessor;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.groups.GroupManager;
import io.forsta.securesms.mms.AttachmentManager;
import io.forsta.securesms.mms.AttachmentTypeSelectorAdapter;
import io.forsta.securesms.mms.MediaConstraints;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.DirectoryRefreshListener;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.DynamicLanguage;
import io.forsta.securesms.util.DynamicTheme;
import io.forsta.ccsm.DashboardActivity;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.MediaUtil;
import io.forsta.securesms.util.TextSecurePreferences;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener,
    AttachmentManager.AttachmentListener,
    KeyboardAwareLinearLayout.OnKeyboardShownListener, KeyboardAwareLinearLayout.OnKeyboardHiddenListener,
    InputPanel.Listener
{
  private static final String TAG = ConversationListActivity.class.getSimpleName();
  private static IntentFilter syncIntentFilter = new IntentFilter(ForstaSyncAdapter.FORSTA_SYNC_COMPLETE);
  private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "Sync complete");
      syncIndicator.setVisibility(View.GONE);
      // TODO make the slug parser a class var, then pass it to the filter when sync is complete.
      directoryFragment.resetQueryFilter();
      getSlugs();
    }
  };

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();


  private boolean isDirectoryOpen = false;
  private boolean isKeyboardOpen = false;
  private ImageButton directoryButton;
  private ImageButton newConversationButton;
  private TextView recipientCount;
  private Map<String, String> forstaRecipients = new HashMap<>();
  private Map<String, ForstaRecipient> forstaSlugs;
  private ConversationListFragment fragment;
  private DrawerFragment drawerFragment;
  private DirectoryFragment directoryFragment;
  private LinearLayout syncIndicator;

  private ContentObserver observer;
  private MasterSecret masterSecret;

  private static final int PICK_IMAGE        = 1;
  private static final int PICK_VIDEO        = 2;
  private static final int PICK_AUDIO        = 3;
  private static final int PICK_CONTACT_INFO = 4;
  private static final int GROUP_EDIT        = 5;
  private static final int TAKE_PHOTO        = 6;
  private static final int ADD_CONTACT       = 7;
  private static final int PICK_LOCATION     = 8;

  // TODO implement receivers.
  private BroadcastReceiver securityUpdateReceiver;
  private BroadcastReceiver recipientsStaleReceiver;

  protected ComposeText composeText;
  private ImageButton sendButton;
  private ImageButton attachButton;
  private TextView charactersLeft;
  private InputAwareLayout container;
  private View composePanel;

  // TODO decide on use of Reminders.
  protected ReminderView reminderView;

  private EmojiDrawer emojiDrawer;
  private InputPanel inputPanel;
  private AttachmentTypeSelector attachmentTypeSelector;
  private AttachmentManager attachmentManager;

  // TODO audioRecorder does not seem to be used in ConversationActivity.
  private AudioRecorder audioRecorder;

  // TODO use these or remove them. These are copy pasta from ConversationActivity.
  private Recipients recipients;
  private long threadId;
  private int distributionType;
  private boolean archived;
  private boolean isSecureText;
  private boolean isMmsEnabled = true;
  private final String DRAFT_KEY = "message_draft";

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putString(DRAFT_KEY, composeText.toString());
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onCreate(Bundle savedState, @NonNull MasterSecret masterSecret) {
    if (savedState != null) {
      composeText.setText(savedState.getString(DRAFT_KEY));
    }
    this.masterSecret = masterSecret;
    setContentView(R.layout.conversation_list_activity);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME);

    fragment = initFragment(R.id.forsta_conversation_list, new ConversationListFragment(), masterSecret, dynamicLanguage.getCurrentLocale());
//    drawerFragment = initFragment(R.id.forsta_drawer_left, new DrawerFragment(), masterSecret, dynamicLanguage.getCurrentLocale());
    directoryFragment = initFragment(R.id.forsta_directory_helper, new DirectoryFragment(), masterSecret, dynamicLanguage.getCurrentLocale());
    hideDirectory();
    syncIndicator = (LinearLayout) findViewById(R.id.forsta_sync_indicator);

    VerifyCcsmToken tokenCheck = new VerifyCcsmToken();
    tokenCheck.execute();

    // Force set enable thread trimming to 30 days.
    if (!TextSecurePreferences.isThreadLengthTrimmingEnabled(getApplicationContext())) {
      TextSecurePreferences.setThreadTrimEnabled(getApplicationContext(), true);
    }

    if (ForstaPreferences.getForstaContactSync(this) == -1) {
      Account account = ForstaSyncAdapter.getAccount(getApplicationContext());
      syncIndicator.setVisibility(View.VISIBLE);
      ContentResolver.requestSync(account, ForstaSyncAdapter.AUTHORITY, Bundle.EMPTY);
    }

    initializeViews();
    initializeListeners();
    initializeContactUpdatesReceiver();

    DirectoryRefreshListener.schedule(this);
    // TODO decide on use of the rating manager
//    RatingManager.showRatingDialogIfNecessary(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    registerReceiver(syncReceiver, syncIntentFilter);
    getSlugs();
  }

  @Override
  protected void onPause() {
    syncIndicator.setVisibility(View.GONE);
    unregisterReceiver(syncReceiver);
    super.onPause();
  }

  @Override
  public void onDestroy() {
    if (observer != null) getContentResolver().unregisterContentObserver(observer);
    super.onDestroy();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(this));

    inflater.inflate(R.menu.conversation_list, menu);
    MenuItem menuItem = menu.findItem(R.id.menu_search);
    initializeSearch(menuItem);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void initializeSearch(MenuItem searchViewItem) {
    SearchView searchView = (SearchView)MenuItemCompat.getActionView(searchViewItem);
    searchView.setQueryHint(getString(R.string.ConversationListActivity_search));
    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        if (fragment != null) {
          fragment.setQueryFilter(query);
          return true;
        }

        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return onQueryTextSubmit(newText);
      }
    });

    MenuItemCompat.setOnActionExpandListener(searchViewItem, new MenuItemCompat.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem menuItem) {
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem menuItem) {
        if (fragment != null) {
          fragment.resetQueryFilter();
        }

        return true;
      }
    });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.menu_new_group:         createGroup();           return true;
      case R.id.menu_settings:          handleDisplaySettings(); return true;
      case R.id.menu_clear_passphrase:  handleClearPassphrase(); return true;
      case R.id.menu_mark_all_read:     handleMarkAllRead();     return true;
      case R.id.menu_import_export:     handleImportExport();    return true;
  //    case R.id.menu_invite:            handleInvite();          return true;
      case R.id.menu_help:              handleHelp();            return true;
      case R.id.menu_directory:         handleDirectory();       return true;
      case R.id.menu_linked_devices:    handleLinkedDevices();   return true;
      case R.id.menu_archive:
        onSwitchToArchive();
        return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null && reqCode != TAKE_PHOTO || resultCode != RESULT_OK) return;

    switch (reqCode) {
      case PICK_IMAGE:
        boolean isGif = MediaUtil.isGif(MediaUtil.getMimeType(this, data.getData()));
        setMedia(data.getData(), isGif ? AttachmentManager.MediaType.GIF : AttachmentManager.MediaType.IMAGE);
        break;
      case PICK_VIDEO:
        setMedia(data.getData(), AttachmentManager.MediaType.VIDEO);
        break;
      case PICK_AUDIO:
        setMedia(data.getData(), AttachmentManager.MediaType.AUDIO);
        break;
      case PICK_CONTACT_INFO:
        addAttachmentContactInfo(data.getData());
        break;
      case TAKE_PHOTO:
        if (attachmentManager.getCaptureUri() != null) {
          setMedia(attachmentManager.getCaptureUri(), AttachmentManager.MediaType.IMAGE);
        }
        break;
      case PICK_LOCATION:
        SignalPlace place = new SignalPlace(PlacePicker.getPlace(data, this));
        attachmentManager.setLocation(masterSecret, place, getCurrentMediaConstraints());
        break;
    }
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    startActivity(intent);
    overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out);
  }

  @Override
  public void onSwitchToArchive() {
    Intent intent = new Intent(this, ConversationListArchiveActivity.class);
    startActivity(intent);
  }

  private void showDirectory() {
    isDirectoryOpen = true;
    getSupportFragmentManager().beginTransaction().show(directoryFragment).commit();
  }

  private void hideDirectory() {
    isDirectoryOpen = false;
    getSupportFragmentManager().beginTransaction().hide(directoryFragment).commit();
  }

  private void initializeViews() {

    directoryButton = (ImageButton) findViewById(R.id.forsta_quick_directory);
    newConversationButton = (ImageButton) findViewById(R.id.forsta_single_recipient);
    recipientCount = (TextView) findViewById(R.id.forsta_input_recipients);
    sendButton = (ImageButton) findViewById(R.id.forsta_send_button);
    attachButton = (ImageButton) findViewById(R.id.attach_button);
    composeText = (ComposeText) findViewById(R.id.embedded_text_editor);
    emojiDrawer = (EmojiDrawer) findViewById(R.id.emoji_drawer);
    composePanel = findViewById(R.id.bottom_panel);
    container = (InputAwareLayout) findViewById(R.id.layout_container);
    inputPanel = (InputPanel) findViewById(R.id.bottom_panel);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this, emojiDrawer);
    attachmentTypeSelector = new AttachmentTypeSelector(this, new AttachmentTypeListener());
    attachmentManager = new AttachmentManager(this, this);

    emojiDrawer.setEmojiEventListener(inputPanel);
//    composeText.setOnEditorActionListener(sendButtonListener);
    attachButton.setOnClickListener(new AttachButtonListener());
//    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
  }

  private void initializeListeners() {

    directoryFragment.setItemSelectedListener(new DirectoryFragment.ItemSelectedListener() {
      @Override
      public void onItemSelected(String slug) {
        String text = composeText.getText().toString();

        int recipientIndex = text.lastIndexOf("@") != -1 ? text.lastIndexOf("@") : 0;

        String newText = text.substring(0, recipientIndex);

        StringBuilder sb = new StringBuilder();
        sb.append(newText);
        sb.append("@").append(slug).append(" ");
        composeText.setText(sb.toString());
        composeText.setSelection(composeText.getText().length());
        hideDirectory();
      }
    });

    sendButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        sendForstaDistribution();
      }
    });

    directoryButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (isDirectoryOpen) {
          hideDirectory();
        } else {
          composeText.setText(composeText.getText() + "@");
          composeText.setSelection(composeText.length());
          if (!isKeyboardOpen) {
            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
          }
          composeText.requestFocus();
          showDirectory();
        }
      }
    });

    newConversationButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        startActivity(new Intent(ConversationListActivity.this, NewConversationActivity.class));
      }
    });


    composeText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        Pattern p = Pattern.compile("@[a-zA-Z0-9-]+");
        Matcher m = p.matcher(charSequence);
        String input = charSequence.toString();

        int slugStart = input.lastIndexOf("@");
        String slugPart = input.substring(slugStart + 1);
        if (slugPart.contains(" ") || input.length() == 0) {
          hideDirectory();
        } else {
          directoryFragment.setQueryFilter(slugPart);
          if (i2 > 0 && charSequence.length() > 0 && charSequence.charAt(charSequence.length() - 1) == "@".charAt(0)) {
            showDirectory();
          }
        }

        forstaRecipients.clear();
        while (m.find()) {
          String slug = m.group();
          slug = slug.substring(1);
          if (forstaSlugs.containsKey(slug)) {
            forstaRecipients.put(slug, forstaSlugs.get(slug).number);
          }
        }

        // TODO remove this. For development only.
        recipientCount.setText("" + forstaRecipients.size());
      }

      @Override
      public void afterTextChanged(Editable editable) {

      }
    });
  }

  private void getSlugs() {
    ContactDb db = DbFactory.getContactDb(ConversationListActivity.this);
    GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(ConversationListActivity.this);
    forstaSlugs = db.getContactRecipients();
    forstaSlugs.putAll(groupDb.getForstaRecipients());
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactAccessor.ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void selectContactInfo(ContactAccessor.ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIconAttribute(R.attr.conversation_attach_contact_info);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        composeText.append(numbers[which]);
      }
    });
    builder.show();
  }

  private void createGroup() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    startActivity(intent);
  }

  private void handleDirectory() {
    Intent directoryIntent = new Intent(this, NewConversationActivity.class);
    startActivity(directoryIntent);
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    startActivity(preferencesIntent);
  }

  private void handleLinkedDevices() {
    Intent intent = new Intent(this, DeviceActivity.class);
    startActivity(intent);
  }


  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }

  private void handleImportExport() {
    startActivity(new Intent(this, ImportExportActivity.class));
  }

  private void handleMarkAllRead() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getThreadDatabase(ConversationListActivity.this).setAllThreadsRead();
        MessageNotifier.updateNotification(ConversationListActivity.this, masterSecret);
        return null;
      }
    }.execute();
  }

  private void handleInvite() {
    startActivity(new Intent(this, InviteActivity.class));
  }

  private void handleHelp() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://support.forsta.io")));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.ConversationListActivity_there_is_no_browser_installed_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  private void initializeContactUpdatesReceiver() {
    observer = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        Log.w(TAG, "Detected android contact data changed, refreshing cache");
        RecipientFactory.clearCache(ConversationListActivity.this);
        ConversationListActivity.this.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            fragment.getListAdapter().notifyDataSetChanged();
          }
        });
      }
    };

    getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                                                 true, observer);
  }

  private class AttachmentTypeListener implements AttachmentTypeSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      addAttachment(type);
    }
  }

  private void addAttachment(int type) {
    Log.w("ComposeMessageActivity", "Selected: " + type);
    switch (type) {
      case AttachmentTypeSelectorAdapter.ADD_IMAGE:
        AttachmentManager.selectImage(this, PICK_IMAGE); break;
      case AttachmentTypeSelectorAdapter.ADD_VIDEO:
        AttachmentManager.selectVideo(this, PICK_VIDEO); break;
      case AttachmentTypeSelectorAdapter.ADD_SOUND:
        AttachmentManager.selectAudio(this, PICK_AUDIO); break;
      case AttachmentTypeSelectorAdapter.ADD_CONTACT_INFO:
        AttachmentManager.selectContactInfo(this, PICK_CONTACT_INFO); break;
      case AttachmentTypeSelector.ADD_LOCATION:
        AttachmentManager.selectLocation(this, PICK_LOCATION); break;
      case AttachmentTypeSelectorAdapter.TAKE_PHOTO:
        attachmentManager.capturePhoto(this, TAKE_PHOTO); break;
    }
  }

  private class AttachButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled || isSecureText) {
      attachmentTypeSelector.show(this, attachButton);
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Intent intent = new Intent(this, PromptMmsActivity.class);
    intent.putExtras(getIntent().getExtras());
    startActivity(intent);
  }

  private void setMedia(Uri uri, AttachmentManager.MediaType mediaType) {
    if (uri == null) return;
    attachmentManager.setMedia(masterSecret, uri, mediaType, getCurrentMediaConstraints());
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return MediaConstraints.PUSH_CONSTRAINTS;
//    return sendButton.getSelectedTransport().getType() == TransportOption.Type.TEXTSECURE
//        ? MediaConstraints.PUSH_CONSTRAINTS
//        : MediaConstraints.MMS_CONSTRAINTS;
  }

  @Override
  public void onBackPressed() {
    Log.w(TAG, "onBackPressed()");
    if (container.isInputOpen()) {
      container.hideCurrentInput(composeText);
    } else if (isDirectoryOpen) {
      hideDirectory();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public void onRecorderStarted() {

  }

  @Override
  public void onRecorderFinished() {

  }

  @Override
  public void onRecorderCanceled() {

  }

  @Override
  public void onEmojiToggle() {
    if (container.getCurrentInput() == emojiDrawer) container.showSoftkey(composeText);
    else container.show(composeText, emojiDrawer);
  }

  @Override
  public void onKeyboardShown() {
    isKeyboardOpen = true;
  }

  @Override
  public void onKeyboardHidden() {
    isKeyboardOpen = false;
  }

  @Override
  public void onAttachmentChanged() {

  }

  public class VerifyCcsmToken extends AsyncTask<Void, Void, Boolean> {

    @Override
    protected Boolean doInBackground(Void... voids) {
      return CcsmApi.checkForstaAuth(getApplicationContext());
    }

    @Override
    protected void onPostExecute(Boolean isError) {
      if (isError) {
        // Not authorized. Start intent for LoginActivity to re-auth.
        Log.d(TAG, "Not Authorized");
        ForstaPreferences.clearLogin(ConversationListActivity.this);
        Intent intent = new Intent(ConversationListActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
      } else {
        Log.d(TAG, "Authorized");
      }
    }
  }

  private void sendForstaDistribution() {
    String message = composeText.getText().toString();
    if (forstaRecipients.size() > 0) {
      if (forstaRecipients.size() == 1) {
        sendSingleRecipientMessage(message, new ArrayList<String>(forstaRecipients.values()));
      } else {
        sendGroupDistribution(message);
      }
    } else {
      Toast.makeText(ConversationListActivity.this, "There are no recipients in this messsage.", Toast.LENGTH_SHORT).show();
    }
  }

  private void sendSingleRecipientMessage(final String message, List<String> numbers) {
    new AsyncTask<List, Void, Recipients>() {

      @Override
      protected Recipients doInBackground(List... numbers) {
        try {
          return RecipientFactory.getRecipientsFromStrings(ConversationListActivity.this, new ArrayList<String>(numbers[0]), false);
        } catch (Exception e) {
          Log.d(TAG, "sendSingleTagMessage failed");
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(Recipients recipients) {
        if (recipients != null) {
          sendMessage(message, recipients);
        } else {
          Toast.makeText(ConversationListActivity.this, "Error sending message. No recipients found.", Toast.LENGTH_LONG).show();
        }
      }
    }.execute(numbers);
  }

  private void sendGroupDistribution(String message) {
    GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(ConversationListActivity.this);
    // Create a new group, using all the recipients.
    // Use the tags and usernames as the new group title... @john-lewis, @dev-team
    Set<String> titleSlugs = new HashSet<>();
    Set<String> numbers = new HashSet<>();
    for (Map.Entry<String, String> entry : forstaRecipients.entrySet()) {
      titleSlugs.add("@" + entry.getKey());
      if (GroupUtil.isEncodedGroup(entry.getValue())) {
        try {
          Set<String> members = groupDb.getGroupMembers(GroupUtil.getDecodedId(entry.getValue()));
          numbers.addAll(members);
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        numbers.add(entry.getValue());
      }
    }
    // Add this phone's user to the end of the title. createGroup will append the number.
    String thisUser = ForstaPreferences.getForstaUsername(ConversationListActivity.this);
    if (!forstaRecipients.keySet().contains(thisUser)) {
      titleSlugs.add("@" + ForstaPreferences.getForstaUsername(ConversationListActivity.this));
    }
    // Now create new group and send to the new groupId.
    String textTitle = TextUtils.join(" + ", titleSlugs);
    // TODO use title field to send JSON blob to clients, with information about dynamic distributions.
//    JSONObject jsonTitle = new JSONObject();
//    try {
//      jsonTitle.put("title", textTitle);
//    } catch (JSONException e) {
//      e.printStackTrace();
//    }
//    sendGroupMessage(message, numbers, jsonTitle.toString());
    sendGroupMessage(message, numbers, textTitle);
  }

  private void sendGroupMessage(final String message, Set<String> numbers, final String title) {
    new AsyncTask<Set<String>, Void, Recipients>() {

      @Override
      protected Recipients doInBackground(Set<String>... params) {
        Set<String> numberSet = params[0];
        try {
          numberSet.add(TextSecurePreferences.getLocalNumber(getApplicationContext()));
          String groupId = GroupManager.getGroupIdFromMembers(ConversationListActivity.this, new ArrayList<>(numberSet));
          if (!groupId.equals("")) {
            List<String> groupNumber = new ArrayList<>();
            groupNumber.add(groupId);
            return RecipientFactory.getRecipientsFromStrings(ConversationListActivity.this, groupNumber, false);
          } else {
            Recipients recipients = RecipientFactory.getRecipientsFromStrings(ConversationListActivity.this, new ArrayList<>(numberSet), false);
            GroupManager.GroupActionResult result = GroupManager.createForstaDistribution(ConversationListActivity.this, masterSecret,  new HashSet<>(recipients.getRecipientsList()), null, title);
            return result.getGroupRecipient();
          }
        } catch (InvalidNumberException e) {
          e.printStackTrace();
        } catch (Exception e) {
          Log.d(TAG, "sendGroupMessage failed getting recipients.");
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(Recipients recipients) {
        if (recipients != null) {
          Log.d(TAG, "Recipients");
          sendMessage(message, recipients);
        } else {
          Toast.makeText(ConversationListActivity.this, "Error sending message. No recipients found.", Toast.LENGTH_LONG).show();
        }
      }
    }.execute(numbers);
  }

  private void sendMessage(String message, Recipients messageRecipients) {
    long expiresIn = messageRecipients.getExpireMessages() * 1000;

    String forstaBody = ForstaUtils.createForstaMessageBody(ConversationListActivity.this, message, messageRecipients);

    OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(messageRecipients, attachmentManager.buildSlideDeck(), forstaBody, System.currentTimeMillis(), -1, expiresIn, ThreadDatabase.DistributionTypes.DEFAULT);
    new AsyncTask<OutgoingMediaMessage, Void, Void>() {

      @Override
      protected Void doInBackground(OutgoingMediaMessage... params) {
        try {
          // This will create a threadId if there is not one already.
          final long threadId = DatabaseFactory.getThreadDatabase(ConversationListActivity.this).getThreadIdFor(params[0].getRecipients());
          MessageSender.send(ConversationListActivity.this, masterSecret, params[0], threadId, false);
        } catch (Exception e) {
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(Void aVoid) {
        fragment.getListAdapter().notifyDataSetChanged();
        attachmentManager.clear();
        composeText.setText("");
        forstaRecipients.clear();
        recipientCount.setText("0");
      }
    }.execute(mediaMessage);
  }
}
