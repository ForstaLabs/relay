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
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.places.ui.PlacePicker;

import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.forsta.ccsm.DirectoryActivity;
import io.forsta.ccsm.DirectoryDialogFragment;
import io.forsta.ccsm.DrawerFragment;
import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.api.ForstaRecipient;
import io.forsta.ccsm.api.ForstaSyncAdapter;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
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
import io.forsta.securesms.recipients.Recipient;
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
    KeyboardAwareLinearLayout.OnKeyboardShownListener,
    InputPanel.Listener
{
  private static final String TAG = ConversationListActivity.class.getSimpleName();

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();


  private ImageButton directoryButton;
  private ImageButton newConversationButton;
  private TextView recipientCount;
  private Map<String, String> forstaRecipients = new HashMap<>();
  private Map<String, String> slugMap = new HashMap<>();
  private List<ForstaRecipient> forstaSlugs;
  private ConversationListFragment fragment;
  private DrawerFragment drawerFragment;
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
  // TODO audioRecorder does not seem to be
  private AudioRecorder audioRecorder;

  // TODO use these or remove them. These are copy pasta from ConversationActivity.
  private Recipients recipients;
  private long threadId;
  private int distributionType;
  private boolean archived;
  private boolean isSecureText;
  private boolean isSecureVoice;
  private boolean isMmsEnabled = true;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    setContentView(R.layout.conversation_list_activity);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME);

    fragment = initFragment(R.id.forsta_conversation_list, new ConversationListFragment(), masterSecret, dynamicLanguage.getCurrentLocale());
    drawerFragment = initFragment(R.id.forsta_drawer, new DrawerFragment(), masterSecret, dynamicLanguage.getCurrentLocale());

    if (ForstaPreferences.getForstaContactSync(this) == -1) {
      Account account = ForstaSyncAdapter.getAccount(getApplicationContext());
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
    getSlugs();
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
    case R.id.menu_dashboard:         handleDashboard();       return true;
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
    sendButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(ConversationListActivity.this);
        String message = composeText.getText().toString();

        if (forstaRecipients.size() > 0) {
          if (forstaRecipients.size() == 1) {
            // Send a single recipient message. Works for a single number or groupId
            sendSingleRecipientMessage(message, new ArrayList<String>(forstaRecipients.values()));
          } else {
            // Create a new group, using all the recipients.
            // Use the tags and usernames as the new group title... @john-lewis, @dev-team
            // Need to stop other users from modifying the group.
            StringBuilder title = new StringBuilder();
            Set<String> numbers = new HashSet<String>();
            for (Map.Entry<String, String> entry : forstaRecipients.entrySet()) {
              title.append(entry.getKey()).append(", ");
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
            // Add this phone's user to the end of the list.
            String thisUser = ForstaPreferences.getForstaUsername(ConversationListActivity.this);
            if (!forstaRecipients.keySet().contains(thisUser)) {
              title.append(ForstaPreferences.getForstaUsername(ConversationListActivity.this));
            }
            // Now create new group and send to the new groupId.
            sendGroupMessage(message, numbers, title.toString());
          }
        } else {
          Toast.makeText(ConversationListActivity.this, "There are no recipients in messsage.", Toast.LENGTH_SHORT).show();
        }
      }
    });

    directoryButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        DirectoryDialogFragment fragment = new DirectoryDialogFragment();
        fragment.show(getSupportFragmentManager(), "directoryDialog");
        fragment.setOnCompleteListener(new DirectoryDialogFragment.OnCompleteListener() {
          @Override
          public void onComplete(Set<ForstaRecipient> recipients) {
            GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(ConversationListActivity.this);

            Set<String> numbers = new HashSet<String>();
            StringBuilder sb = new StringBuilder();
            sb.append(composeText.getText().toString());
            for (ForstaRecipient recipient : recipients) {
              if (GroupUtil.isEncodedGroup(recipient.number)) {
                Set<String> members = null;
                try {
                  members = groupDb.getGroupMembers(GroupUtil.getDecodedId(recipient.number));
                  numbers.addAll(members);
                } catch (IOException e) {
                  e.printStackTrace();
                }
              } else {
                numbers.add(recipient.number);
              }
              sb.append("@").append(recipient.slug).append(" ");
            }
            composeText.setText(sb.toString());
            composeText.setSelection(composeText.getText().length());

            new AsyncTask<Set<String>, Void, Recipients>() {

              @Override
              protected Recipients doInBackground(Set<String>... params) {
                return RecipientFactory.getRecipientsFromStrings(ConversationListActivity.this, new ArrayList<String>(params[0]), false);
              }

              @Override
              protected void onPostExecute(Recipients recipients) {
                String groupId = GroupManager.getGroupIdFromRecipients(ConversationListActivity.this, recipients);
              }
            }.execute(numbers);
          }
        });
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
        Map<String, String> matched = new HashMap<String, String>();

        Pattern p = Pattern.compile("@[a-zA-Z0-9-]+");
        Matcher m = p.matcher(charSequence);

        while (m.find()) {
          String slug = m.group();
          slug = slug.substring(1);
          if (slugMap.containsKey(slug)) {
            matched.put(slug, slugMap.get(slug));
          }
        }
        forstaRecipients = matched;
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
    slugMap = db.getContactSlugs();
    slugMap.putAll(groupDb.getGroupSlugs());
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

  private void handleDashboard() {
    Intent dashIntent = new Intent(this, DashboardActivity.class);
    startActivity(dashIntent);
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    startActivity(preferencesIntent);
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
    if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else super.onBackPressed();
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

  }

  @Override
  public void onAttachmentChanged() {

  }

  private void sendSingleRecipientMessage(final String message, List<String> numbers) {
    new AsyncTask<List<String>, Void, Recipients>() {

      @Override
      protected Recipients doInBackground(List<String>... numbers) {
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
          Toast.makeText(ConversationListActivity.this, "Error sending message. No recipients found.", Toast.LENGTH_LONG);
        }
      }
    }.execute(numbers);
  }

  private void sendGroupMessage(final String message, Set<String> numbers, final String title) {
    // Need to check here to see if these numbers are already part of an existing group.
    //
    //
    new AsyncTask<Set<String>, Void, Recipients>() {

      @Override
      protected Recipients doInBackground(Set<String>... numbers) {
        try {
          return RecipientFactory.getRecipientsFromStrings(ConversationListActivity.this, new ArrayList<String>(numbers[0]), false);
        } catch (Exception e) {
          Log.d(TAG, "sendGroupMessage failed. No valid Recipients.");
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(Recipients recipients) {
        if (recipients != null) {
          try {
            // Now find out if these numbers are part of an existing group. Create a new group if there is not one of this composition of numbers.

            GroupManager.GroupActionResult result = GroupManager.createGroup(ConversationListActivity.this, masterSecret,  new HashSet<>(recipients.getRecipientsList()), null, title);
            Recipients groupRecipient = result.getGroupRecipient();
            sendMessage(message, groupRecipient);
          } catch (InvalidNumberException e) {
            e.printStackTrace();
          }
        } else {
          Toast.makeText(ConversationListActivity.this, "Error sending message. No recipients found.", Toast.LENGTH_LONG);
        }
      }
    }.execute(numbers);
  }

  private void sendMessage(String message, Recipients messageRecipients) {
    long expiresIn = messageRecipients.getExpireMessages() * 1000;

    OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(messageRecipients, attachmentManager.buildSlideDeck(), message, System.currentTimeMillis(), -1, expiresIn, ThreadDatabase.DistributionTypes.DEFAULT);
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
        attachmentManager.clear();
        composeText.setText("");
        forstaRecipients.clear();
      }
    }.execute(mediaMessage);
  }
}
