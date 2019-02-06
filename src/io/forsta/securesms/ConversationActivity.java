/**
 * Copyright (C) 2011 Whisper Systems
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

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.places.ui.PlacePicker;

import io.forsta.ccsm.ThreadPreferenceActivity;
import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.messaging.IncomingMessage;
import io.forsta.ccsm.messaging.OutgoingMessage;
import io.forsta.securesms.audio.AudioRecorder;
import io.forsta.securesms.audio.AudioSlidePlayer;
import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.color.MaterialColors;
import io.forsta.securesms.components.AnimatingToggle;
import io.forsta.securesms.components.AttachmentTypeSelector;
import io.forsta.securesms.components.ComposeText;
import io.forsta.securesms.components.HidingLinearLayout;
import io.forsta.securesms.components.InputAwareLayout;
import io.forsta.securesms.components.InputPanel;
import io.forsta.securesms.components.SendButton;
import io.forsta.securesms.components.camera.QuickAttachmentDrawer;
import io.forsta.securesms.components.emoji.EmojiDrawer;
import io.forsta.securesms.components.location.SignalPlace;
import io.forsta.securesms.components.reminder.ReminderView;
import io.forsta.securesms.contacts.ContactAccessor;
import io.forsta.securesms.crypto.MasterCipher;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.DraftDatabase;
import io.forsta.securesms.database.MessagingDatabase.MarkedMessageInfo;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.MmsSmsColumns.Types;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.ThreadPreferenceDatabase;
import io.forsta.securesms.mms.AttachmentManager;
import io.forsta.securesms.mms.AttachmentManager.MediaType;
import io.forsta.securesms.mms.AttachmentTypeSelectorAdapter;
import io.forsta.securesms.mms.AudioSlide;
import io.forsta.securesms.mms.IncomingMediaMessage;
import io.forsta.securesms.mms.LocationSlide;
import io.forsta.securesms.mms.MediaConstraints;
import io.forsta.securesms.mms.OutgoingExpirationUpdateMessage;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.mms.OutgoingSecureMediaMessage;
import io.forsta.securesms.mms.Slide;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.notifications.MarkReadReceiver;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.permissions.Permissions;
import io.forsta.securesms.providers.PersistentBlobProvider;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.RecipientProvider;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.recipients.Recipients.RecipientsModifiedListener;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.database.model.MediaMmsMessageRecord;
import io.forsta.securesms.service.WebRtcCallService;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.sms.OutgoingEndSessionMessage;
import io.forsta.securesms.sms.OutgoingTextMessage;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.DynamicLanguage;
import io.forsta.securesms.util.DynamicTheme;
import io.forsta.securesms.util.ExpirationUtil;
import io.forsta.securesms.util.MediaUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import io.forsta.securesms.util.ViewUtil;
import io.forsta.securesms.util.concurrent.AssertedSuccessListener;
import io.forsta.securesms.util.concurrent.ListenableFuture;
import io.forsta.securesms.util.concurrent.SettableFuture;

import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.forsta.securesms.components.KeyboardAwareLinearLayout;
import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationActivity extends PassphraseRequiredActionBarActivity
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               RecipientsModifiedListener,
        KeyboardAwareLinearLayout.OnKeyboardShownListener,
        QuickAttachmentDrawer.AttachmentDrawerListener,
        InputPanel.Listener
{
  private static final String TAG = ConversationActivity.class.getSimpleName();

  public static final String RECIPIENTS_EXTRA        = "recipients";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String IS_ARCHIVED_EXTRA       = "is_archived";
  public static final String TEXT_EXTRA              = "draft_text";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";

  private static final int PICK_IMAGE        = 1;
  private static final int PICK_VIDEO        = 2;
  private static final int PICK_AUDIO        = 3;
  private static final int PICK_CONTACT_INFO = 4;
  private static final int GROUP_EDIT        = 5;
  private static final int TAKE_PHOTO        = 6;
  private static final int ADD_CONTACT       = 7;
  private static final int PICK_LOCATION     = 8;
  private static final int PICK_DOCUMENT     = 9;

  private   MasterSecret          masterSecret;
  protected ComposeText           composeText;
  private   AnimatingToggle       buttonToggle;
  private   SendButton            sendButton;
  private   ImageButton           attachButton;
  protected ConversationTitleView titleView;
  private   TextView              charactersLeft;
  private   ConversationFragment  fragment;
  private   Button                unblockButton;
  private   InputAwareLayout      container;
  private   View                  composePanel;
  protected ReminderView          reminderView;

  private   AttachmentTypeSelector attachmentTypeSelector;
  private   AttachmentManager      attachmentManager;
  private   AudioRecorder          audioRecorder;
  private   BroadcastReceiver      recipientsClearReceiver;
  private   EmojiDrawer            emojiDrawer;
  protected HidingLinearLayout     quickAttachmentToggle;
  private   QuickAttachmentDrawer  quickAttachmentDrawer;
  private   InputPanel             inputPanel;

  private Recipients recipients;
  private long       threadId;
  private String messageRef;
  private ForstaThread forstaThread;
  private int        distributionType;
  private boolean    archived;
  private boolean    isSecureText;
  private Handler handler = new Handler();
  private ContentObserver threadObserver;

  private DynamicTheme    dynamicTheme    = new DynamicTheme();
  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull final MasterSecret masterSecret) {
    Log.w(TAG, "onCreate()");
    this.masterSecret = masterSecret;

    supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
    setContentView(R.layout.conversation_activity);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(),
                            masterSecret, dynamicLanguage.getCurrentLocale());

    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeDraft();
    updateDistribution();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    Log.w(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
      saveDraft();
      attachmentManager.clear();
      composeText.setText("");
    }

    setIntent(intent);
    initializeResources();
    initializeDraft();
    supportInvalidateOptionsMenu();

    if (fragment != null) {
      fragment.onNewIntent();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    quickAttachmentDrawer.onResume();

    MessageNotifier.setVisibleThread(threadId);

    initializeThread();
    markThreadAsRead();

    getContentResolver().registerContentObserver(Uri.parse(ThreadDatabase.THREAD_URI + threadId), true, threadObserver);
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
    quickAttachmentDrawer.onPause();
    inputPanel.onPause();
    getContentResolver().unregisterContentObserver(threadObserver);

    AudioSlidePlayer.stopAll();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.w(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    quickAttachmentDrawer.onConfigurationChanged();
    if (container.getCurrentInput() == emojiDrawer) container.hideAttachedInput(true);
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (recipients != null)              recipients.removeListener(this);
    if (recipientsClearReceiver != null) unregisterReceiver(recipientsClearReceiver);

    super.onDestroy();
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null && reqCode != TAKE_PHOTO || resultCode != RESULT_OK) return;

    switch (reqCode) {
    case PICK_IMAGE:
      boolean isGif = MediaUtil.isGif(MediaUtil.getMimeType(this, data.getData()));
      setMedia(data.getData(), isGif ? MediaType.GIF : MediaType.IMAGE);
      break;
    case PICK_VIDEO:
      setMedia(data.getData(), MediaType.VIDEO);
      break;
    case PICK_AUDIO:
      setMedia(data.getData(), MediaType.AUDIO);
      break;
    case PICK_CONTACT_INFO:
      addAttachmentContactInfo(data.getData());
      break;
    case TAKE_PHOTO:
      if (attachmentManager.getCaptureUri() != null) {
        setMedia(attachmentManager.getCaptureUri(), MediaType.IMAGE);
      }
      break;
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePicker.getPlace(data, this));
      attachmentManager.setLocation(masterSecret, place, getCurrentMediaConstraints());
      break;
    case PICK_DOCUMENT:
      setMedia(data.getData(), MediaType.DOCUMENT);
    }
  }

  @Override
  public void startActivity(Intent intent) {
    if (intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null) {
      intent.removeExtra(Browser.EXTRA_APPLICATION_ID);
    }

    try {
      super.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(this, R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    if (!forstaThread.isAnnouncement()) {
      int expireMessages = DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).getExpireMessages(threadId);
      if (expireMessages > 0) {
        inflater.inflate(R.menu.conversation_expiring_on, menu);

        final MenuItem item       = menu.findItem(R.id.menu_expiring_messages);
        final View     actionView = MenuItemCompat.getActionView(item);
        final TextView badgeView  = (TextView)actionView.findViewById(R.id.expiration_badge);

        badgeView.setText(ExpirationUtil.getExpirationAbbreviatedDisplayValue(this, expireMessages));
        actionView.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            onOptionsItemSelected(item);
          }
        });
      } else {
        inflater.inflate(R.menu.conversation_expiring_off, menu);
      }

      inflater.inflate(R.menu.conversation, menu);
      if ((recipients.isSingleRecipient() && !recipients.includesSelf(this)) || recipients.getRecipientsList().size() < 5) {
        Recipient recipient = recipients.getPrimaryRecipient();
        if (!TextUtils.isEmpty(recipient.getAddress())) {
          final MenuItem callItem = menu.findItem(R.id.menu_call_recipient);
          callItem.setVisible(true);
        }

        final MenuItem leaveConverationItem = menu.findItem(R.id.menu_leave_conversation);
        leaveConverationItem.setVisible(false);
      }
    }

    if (isGroupConversation()) {
      inflater.inflate(R.menu.conversation_group_options, menu);
    }

    inflater.inflate(R.menu.conversation_secure, menu);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_leave_conversation:        handleLeaveConversation();                         return true;
    case R.id.menu_add_attachment:            handleAddAttachment();                             return true;
    case R.id.menu_view_media:                handleViewMedia();                                 return true;
    case R.id.menu_reset_secure_session:      handleResetSecureSession();                        return true;
    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_invite:                    handleInviteLink();                                return true;
    case R.id.menu_mute_notifications:        handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:      handleUnmuteNotifications();                       return true;
    case R.id.menu_conversation_settings:     handleConversationSettings();                      return true;
    case R.id.menu_expiring_messages_off:
    case R.id.menu_expiring_messages:         handleSelectMessageExpiration();                   return true;
    case R.id.menu_call_recipient:            handleDial();                             return true;
    case android.R.id.home:                   handleReturnToConversationList();                  return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    Log.w(TAG, "onBackPressed()");
    if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else                         super.onBackPressed();
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
  }

  //////// Event Handlers

  private void handleLeaveConversation() {
    new AsyncTask<Void, Void, ForstaDistribution>() {
      @Override
      protected ForstaDistribution doInBackground(Void... voids) {
        try {
          ForstaUser self = ForstaUser.getLocalForstaUser(ConversationActivity.this);
          String expression = recipients.getRecipientExpression();
          expression = "(" + expression + ")-@" + self.getFullTag();
          return CcsmApi.getMessageDistribution(ConversationActivity.this, expression);
        } catch (Exception e) {
          Log.w(TAG, "Exception leaving conversation: " + e.getMessage());
        }
        return null;
      }

      @Override
      protected void onPostExecute(ForstaDistribution distribution) {
        try {
          if (distribution != null && distribution.isValid()) {
            DatabaseFactory.getThreadDatabase(ConversationActivity.this).updateForstaThread(threadId, distribution);
            initializeThread();
            MessageSender.sendThreadUpdate(ConversationActivity.this, masterSecret, recipients, threadId);
          } else {
            Toast.makeText(ConversationActivity.this, "Unable to leave conversation. Check your network connection and try again.", Toast.LENGTH_LONG);
          }
        } catch (Exception e) {
          e.printStackTrace();
          Toast.makeText(ConversationActivity.this, "Unable to leave conversation. An exception has occurred.", Toast.LENGTH_LONG);
        }
      }
    }.execute();
  }

  private void handleReturnToConversationList() {
    Intent intent = new Intent(this, (archived ? ConversationListArchiveActivity.class : ConversationListActivity.class));
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
  }

  private void handleSelectMessageExpiration() {
    ExpirationDialog.show(this, DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).getExpireMessages(threadId), new ExpirationDialog.OnClickListener() {
      @Override
      public void onClick(final int expirationTime) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).setExpireMessages(threadId, expirationTime);
            MessageSender.sendExpirationUpdate(getApplicationContext(), masterSecret, recipients, threadId, expirationTime);

            invalidateOptionsMenu();
            return null;
          }
        }.execute();
      }
    });
  }

  private void handleMuteNotifications() {
    MuteDialog.show(this, new MuteDialog.MuteSelectionListener() {
      @Override
      public void onMuted(final long until) {
        recipients.setMuted(until);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).setMuteUntil(threadId, until);

            return null;
          }
        }.execute();
      }
    });
  }

  private void handleConversationSettings() {
    titleView.performClick();
  }

  private void handleUnmuteNotifications() {
    recipients.setMuted(0);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).setMuteUntil(threadId, 0);

        return null;
      }
    }.execute();
  }

  private void handleUnblock() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.ConversationActivity_unblock_this_contact_question)
        .setMessage(R.string.ConversationActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.ConversationActivity_unblock, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            recipients.setBlocked(false);

            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).setBlocked(threadId, false);

//                ApplicationContext.getInstance(ConversationActivity.this)
//                                  .getJobManager()
//                                  .add(new MultiDeviceBlockedUpdateJob(ConversationActivity.this));

                return null;
              }
            }.execute();
          }
        }).show();
  }

  private void handleInviteLink() {
    try {
      boolean a = SecureRandom.getInstance("SHA1PRNG").nextBoolean();
      if (a) composeText.appendInvite(getString(R.string.ConversationActivity_lets_switch_to_signal, "https://www.forsta.io/benefits"));
      else   composeText.appendInvite(getString(R.string.ConversationActivity_lets_use_this_to_chat, "http://forsta.io/benefits"));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private void handleResetSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_reset_secure_session_question);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_this_may_help_if_youre_having_encryption_problems);
    builder.setPositiveButton(R.string.ConversationActivity_reset, (dialog, which) -> {
      final Context context = getApplicationContext();

      OutgoingTextMessage endTextMessage = new OutgoingTextMessage(recipients, "", 0, -1);
      OutgoingEndSessionMessage endSessionMessage = new OutgoingEndSessionMessage(endTextMessage);

      new AsyncTask<OutgoingEndSessionMessage, Void, Long>() {
        @Override
        protected Long doInBackground(OutgoingEndSessionMessage... messages) {
          return MessageSender.send(context, masterSecret, messages[0], threadId, false);
        }

        @Override
        protected void onPostExecute(Long result) {
                                              sendComplete(result);
                                                                   }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, endSessionMessage);
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleViewMedia() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(MediaOverviewActivity.RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
    startActivity(intent);
  }

  private void handleAddAttachment() {
    attachmentTypeSelector.show(this, attachButton);
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(this, getRecipients()).display();
  }

  private void handleDial() {
    Recipient recipient = recipients.getPrimaryRecipient();
    if (recipient == null) return;

    Permissions.with(ConversationActivity.this)
        .request(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        .ifNecessary()
        .withRationaleDialog(getString(R.string.ConversationActivity_to_call_needs_access_to_your_microphone_and_camera),
            R.drawable.ic_mic_white_48dp, R.drawable.ic_videocam_white_48dp)
        .withPermanentDenialDialog(getString(R.string.ConversationActivity_to_call_needs_access_to_your_microphone_and_camera))
        .onAllGranted(() -> {
          Intent intent = new Intent(this, WebRtcCallService.class);
          intent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
          intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, recipient.getAddress());
          intent.putExtra(WebRtcCallService.EXTRA_CALL_MEMBERS, recipients.getAddresses().toArray(new String[recipients.getAddresses().size()]));
          intent.putExtra(WebRtcCallService.EXTRA_THREAD_UID, forstaThread.getUid());
          startService(intent);

          Intent activityIntent = new Intent(this, WebRtcCallActivity.class);
          activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(activityIntent);
        })
        .execute();
  }

  ///// Initializers

  private void updateDistribution() {
    ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    if (isConnected) {
      new AsyncTask<Void, Void, ForstaDistribution>() {
        @Override
        protected ForstaDistribution doInBackground(Void... voids) {
          try {
            ForstaThread currentThread = DatabaseFactory.getThreadDatabase(ConversationActivity.this).getForstaThread(threadId);
            return CcsmApi.getMessageDistribution(ConversationActivity.this, currentThread.getDistribution());
          } catch (Exception e) {
            e.printStackTrace();
          }
          return null;
        }

        @Override
        protected void onPostExecute(ForstaDistribution distribution) {
          if (distribution != null && distribution.isValid()) {
            DatabaseFactory.getThreadDatabase(ConversationActivity.this).updateForstaThread(threadId, distribution);
          }
        }
      }.execute();
    }
  }

  @Override
  public void handleReplyMessage(MessageRecord messageRecord) {
    Recipient author;
    messageRef = messageRecord.getMessageId();

    if (messageRecord.isOutgoing()) {
      author = RecipientFactory.getRecipient(this, TextSecurePreferences.getLocalNumber(this), true);
    } else {
      author = messageRecord.getIndividualRecipient();
    }
    inputPanel.setQuote(messageRecord.getDateSent(), author, messageRecord.getPlainTextBody(), messageRecord.isMms() ? ((MediaMmsMessageRecord) messageRecord).getSlideDeck() : new SlideDeck());
  }

  private void initializeThread() {
    ThreadPreferenceDatabase threadDb = DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this);
    ThreadPreferenceDatabase.ThreadPreference threadPreference = threadDb.getThreadPreferences(threadId);
    forstaThread = DatabaseFactory.getThreadDatabase(ConversationActivity.this).getForstaThread(threadId);
    if (recipients != null) {
      recipients.removeListener(this);
    }
    recipients = RecipientFactory.getRecipientsForIds(ConversationActivity.this, forstaThread.getRecipientIds(), true);
    recipients.addListener(this);

    if (recipients == null || recipients.isEmpty() || (!recipients.isSingleRecipient() && !recipients.includesSelf(ConversationActivity.this))) {
      inputPanel.setVisibility(View.GONE);
    }

    // May have preferences prior to color preference addition
    if (threadPreference.getColor() == null) {
      threadDb.setColor(threadId, MaterialColors.getRandomConversationColor());
      threadPreference = threadDb.getThreadPreferences(threadId);
    }
    titleView.setTitle(recipients, forstaThread);
    setActionBarColor(threadPreference.getColor());

    if (forstaThread.isAnnouncement()) {
      setActionBarColor(MaterialColor.ANNOUNCEMENT);
      if (!forstaThread.getThreadCreator().equals(TextSecurePreferences.getLocalNumber(ConversationActivity.this))) {
        inputPanel.setVisibility(View.GONE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      }
    }
  }

  private void initializeDraft() {
    final String    draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri       draftMedia     = getIntent().getData();
    final MediaType draftMediaType = MediaType.from(getIntent().getType());

    if (draftText != null)                            composeText.setText(draftText);
    if (draftMedia != null && draftMediaType != null) setMedia(draftMedia, draftMediaType);

    if (draftText == null && draftMedia == null && draftMediaType == null) {
      initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
    }
  }

  private void initializeDraftFromDatabase() {
    new AsyncTask<Void, Void, List<DraftDatabase.Draft>>() {
      @Override
      protected List<DraftDatabase.Draft> doInBackground(Void... params) {
        MasterCipher masterCipher   = new MasterCipher(masterSecret);
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<DraftDatabase.Draft> results         = draftDatabase.getDrafts(masterCipher, threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<DraftDatabase.Draft> drafts) {
        for (DraftDatabase.Draft draft : drafts) {
          try {
            if (draft.getType().equals(DraftDatabase.Draft.TEXT)) {
              composeText.setText(draft.getValue());
            } else if (draft.getType().equals(DraftDatabase.Draft.LOCATION)) {
              attachmentManager.setLocation(masterSecret, SignalPlace.deserialize(draft.getValue()), getCurrentMediaConstraints());
            } else if (draft.getType().equals(DraftDatabase.Draft.IMAGE)) {
              setMedia(Uri.parse(draft.getValue()), MediaType.IMAGE);
            } else if (draft.getType().equals(DraftDatabase.Draft.AUDIO)) {
              setMedia(Uri.parse(draft.getValue()), MediaType.AUDIO);
            } else if (draft.getType().equals(DraftDatabase.Draft.VIDEO)) {
              setMedia(Uri.parse(draft.getValue()), MediaType.VIDEO);
            } else if (draft.getType().equals(DraftDatabase.Draft.DOCUMENT)) {
              setMedia(Uri.parse(draft.getValue()), MediaType.DOCUMENT);
            }
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        updateToggleButtonState();
      }
    }.execute();
  }


  private void initializeViews() {
    titleView             = (ConversationTitleView) getSupportActionBar().getCustomView();
    buttonToggle          = ViewUtil.findById(this, R.id.button_toggle);
    sendButton            = ViewUtil.findById(this, R.id.send_button);
    attachButton          = ViewUtil.findById(this, R.id.attach_button);
    composeText           = ViewUtil.findById(this, R.id.embedded_text_editor);
    charactersLeft        = ViewUtil.findById(this, R.id.space_left);
    emojiDrawer           = ViewUtil.findById(this, R.id.emoji_drawer);
    unblockButton         = ViewUtil.findById(this, R.id.unblock_button);
    composePanel          = ViewUtil.findById(this, R.id.bottom_panel);
    container             = ViewUtil.findById(this, R.id.layout_container);
    reminderView          = ViewUtil.findById(this, R.id.reminder);
    quickAttachmentDrawer = ViewUtil.findById(this, R.id.quick_attachment_drawer);
    quickAttachmentToggle = ViewUtil.findById(this, R.id.quick_attachment_toggle);
    inputPanel            = ViewUtil.findById(this, R.id.bottom_panel);

    ImageButton quickCameraToggle = ViewUtil.findById(this, R.id.quick_camera_toggle);
    View        composeBubble     = ViewUtil.findById(this, R.id.compose_bubble);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this, emojiDrawer);

    int[]      attributes   = new int[]{R.attr.conversation_item_bubble_background};
    TypedArray colors       = obtainStyledAttributes(attributes);
    int        defaultColor = colors.getColor(0, Color.WHITE);
    composeBubble.getBackground().setColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY);
    colors.recycle();

    attachmentTypeSelector = new AttachmentTypeSelector(this, new AttachmentTypeListener());
    attachmentManager      = new AttachmentManager(this, this);
    audioRecorder          = new AudioRecorder(this, masterSecret);

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    emojiDrawer.setEmojiEventListener(inputPanel);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    titleView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (threadId != -1) {
          Intent intent = new Intent(ConversationActivity.this, ThreadPreferenceActivity.class);
          intent.putExtra(ThreadPreferenceActivity.THREAD_ID_EXTRA, threadId);

          startActivitySceneTransition(intent, titleView.findViewById(R.id.title), "thread_preferences");
        }
      }
    });

    unblockButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        handleUnblock();
      }
    });

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    if (QuickAttachmentDrawer.isDeviceSupported(this)) {
      quickAttachmentDrawer.setListener(this);
      quickCameraToggle.setOnClickListener(new QuickCameraToggleListener());
    } else {
      quickCameraToggle.setVisibility(View.GONE);
      quickCameraToggle.setEnabled(false);
    }
  }

  protected void initializeActionBar() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setCustomView(R.layout.conversation_title_view);
    getSupportActionBar().setDisplayShowCustomEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    threadId = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    archived = getIntent().getBooleanExtra(IS_ARCHIVED_EXTRA, false);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      LinearLayout conversationContainer = ViewUtil.findById(this, R.id.conversation_container);
      conversationContainer.setClipChildren(true);
      conversationContainer.setClipToPadding(true);
    }
  }

  @Override
  public void onModified(final Recipients modifiedRecipients) {
    Log.w(TAG, "Recipients modified. From: " + TextUtils.join(",", recipients.getAddresses()) + " To: " + TextUtils.join(",", modifiedRecipients.getAddresses()));

//    supportInvalidateOptionsMenu();
    // Make up of thread can be modified by receipt of message in PushDecrypytJob.
    // this.recipients = recipients;
    // upstate forstaThread reference
    // initializeThread();
  }

  private void initializeReceivers() {
    recipientsClearReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Clear cache received.");
      }
    };

    IntentFilter clearFilter = new IntentFilter();
    clearFilter.addAction(RecipientFactory.RECIPIENT_CLEAR_ACTION);
    registerReceiver(recipientsClearReceiver, clearFilter);

    threadObserver = new ContentObserver(handler) {
      @Override
      public void onChange(boolean selfChange) {
        supportInvalidateOptionsMenu();
        initializeThread();
        markThreadAsRead();
      }
    };
  }

  //////// Helper Methods

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
    case AttachmentTypeSelectorAdapter.ADD_DOCUMENT:
      AttachmentManager.selectDocument(this, PICK_DOCUMENT); break;
    }
  }

  private void setMedia(Uri uri, MediaType mediaType) {
    if (uri == null) return;
    attachmentManager.setMedia(masterSecret, uri, mediaType, getCurrentMediaConstraints());
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

  private DraftDatabase.Drafts getDraftsForCurrentState() {
    DraftDatabase.Drafts drafts = new DraftDatabase.Drafts();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.TEXT, composeText.getText().toString()));
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio())    drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo())    drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.VIDEO, slide.getUri().toString()));
      else if (slide.hasLocation()) drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.LOCATION, ((LocationSlide)slide).getPlace().serialize()));
      else if (slide.hasImage())    drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.IMAGE, slide.getUri().toString()));
      else if (slide.hasDocument()) drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.DOCUMENT, slide.getUri().toString()));
    }

    return drafts;
  }

  protected ListenableFuture<Long> saveDraft() {
    final SettableFuture<Long> future = new SettableFuture<>();

    if (this.recipients == null || this.recipients.isEmpty()) {
      future.set(threadId);
      return future;
    }

    final DraftDatabase.Drafts drafts               = getDraftsForCurrentState();
    final long         thisThreadId         = this.threadId;
    final MasterSecret thisMasterSecret     = this.masterSecret.parcelClone();
    final int          thisDistributionType = this.distributionType;

    new AsyncTask<Long, Void, Long>() {
      @Override
      protected Long doInBackground(Long... params) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
        DraftDatabase  draftDatabase  = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        long           threadId       = params[0];

        if (drafts.size() > 0) {
          if (threadId == -1) threadId = threadDatabase.getThreadIdFor(getRecipients(), thisDistributionType);

          draftDatabase.insertDrafts(new MasterCipher(thisMasterSecret), threadId, drafts);
          ForstaThread threadData = DatabaseFactory.getThreadDatabase(ConversationActivity.this).getForstaThread(threadId);
          String snippet = ForstaMessageManager.createForstaMessageBody(ConversationActivity.this, drafts.getSnippet(ConversationActivity.this), recipients, attachmentManager.buildSlideDeck().asAttachments(), threadData);
          threadDatabase.updateDraftSnippet(threadId, snippet,
                                       drafts.getUriSnippet(ConversationActivity.this),
                                       System.currentTimeMillis(), Types.BASE_DRAFT_TYPE, true);
        } else if (threadId > 0) {
          threadDatabase.update(threadId, false);
        }

        return threadId;
      }

      @Override
      protected void onPostExecute(Long result) {
        future.set(result);
      }

    }.execute(thisThreadId);

    return future;
  }

  private void setActionBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));
    setStatusBarColor(color.toStatusBarColor(this));
  }

  private void setBlockedUserState(Recipients recipients) {
    if (recipients.isBlocked()) {
      unblockButton.setVisibility(View.VISIBLE);
      composePanel.setVisibility(View.GONE);
    } else {
      composePanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
    }
  }

  private boolean isGroupConversation() {
    return getRecipients() != null &&
        (!getRecipients().isSingleRecipient());
  }

  protected Recipients getRecipients() {
    return this.recipients;
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getText().toString();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    if (attachmentManager.isAttachmentPresent()) {
      String location = attachmentManager.buildSlideDeck().getBody();
      if (!TextUtils.isEmpty(location)) {
        rawText = location + " " + rawText;
      }
    }

    return rawText;
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return MediaConstraints.PUSH_CONSTRAINTS;
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        Context                 context    = ConversationActivity.this;
        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(params[0]);

        MessageNotifier.updateNotification(context, masterSecret);
        MarkReadReceiver.process(context, messageIds);

        return null;
      }
    }.execute(threadId);
  }

  protected void sendComplete(long threadId) {
    if (fragment == null || !fragment.isVisible() || isFinishing()) {
      return;
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();
  }

  private void sendMessage() {
    try {
      boolean    forceSms       = false;
      int        subscriptionId = -1;
      long       expiresIn      = DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).getExpireMessages(threadId) * 1000;

      if (recipients == null) {
        Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_recipient_is_not_valid, Toast.LENGTH_LONG).show();
      } else if(inputPanel.hasQuoteVisible()){
        sendReplyMessage(forceSms, expiresIn, subscriptionId, messageRef);
      } else {
        sendMediaMessage(forceSms, expiresIn, subscriptionId);
      }
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(final boolean forceSms, final long expiresIn, final int subscriptionId)
      throws InvalidMessageException
  {
    sendMediaMessage(forceSms, getMessage(), attachmentManager.buildSlideDeck(), expiresIn, subscriptionId);
  }

  private void sendReplyMessage(final boolean forceSms, final long expiresIn, final int subscriptionId, final String messageRef)
          throws InvalidMessageException
  {
    sendReplyMessage(forceSms, getMessage(), attachmentManager.buildSlideDeck(), expiresIn, subscriptionId, messageRef);
  }

  private ListenableFuture<Void> sendMediaMessage(final boolean forceSms, String body, final SlideDeck slideDeck, final long expiresIn, final int subscriptionId)
      throws InvalidMessageException
  {
    final SettableFuture<Void> future          = new SettableFuture<>();
    final Context              context         = getApplicationContext();

    attachmentManager.clear();
    composeText.setText("");
    inputPanel.clearQuote();
    this.messageRef = null;

    new AsyncTask<Void, Void, Long>() {
      @Override
      protected Long doInBackground(Void... nothing) {
        return MessageSender.sendContentMessage(context, masterSecret, body, recipients, slideDeck, threadId, expiresIn);
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
        future.set(null);
      }
    }.execute();


    return future;
  }

  private ListenableFuture<Void> sendReplyMessage(final boolean forceSms, String body, final SlideDeck slideDeck, final long expiresIn, final int subscriptionId, final String messageRef)
          throws InvalidMessageException
  {
    final SettableFuture<Void> future          = new SettableFuture<>();
    final Context              context         = getApplicationContext();

    attachmentManager.clear();
    composeText.setText("");
    inputPanel.clearQuote();
    this.messageRef = null;

    new AsyncTask<Void, Void, Long>() {
      @Override
      protected Long doInBackground(Void... nothing) {
        return MessageSender.sendContentReplyMesage(context, masterSecret, body, recipients, slideDeck, threadId, expiresIn, messageRef, 0);
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
        future.set(null);
      }
    }.execute();


    return future;
  }

  private void updateToggleButtonState() {
    if (composeText.getText().length() == 0 && !attachmentManager.isAttachmentPresent()) {
      buttonToggle.display(attachButton);
      quickAttachmentToggle.show();
    } else {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();
    }
  }

  private void recordSubscriptionIdPreference(final Optional<Integer> subscriptionId) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
//        DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
//                       .setDefaultSubscriptionId(recipients, subscriptionId.or(-1));
        return null;
      }
    }.execute();
  }

  @Override
  public void onAttachmentDrawerStateChanged(QuickAttachmentDrawer.DrawerState drawerState) {
    if (drawerState == QuickAttachmentDrawer.DrawerState.FULL_EXPANDED) {
      getSupportActionBar().hide();
    } else {
      getSupportActionBar().show();
    }

    if (drawerState == QuickAttachmentDrawer.DrawerState.COLLAPSED) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  public void onImageCapture(@NonNull final byte[] imageBytes) {
    setMedia(PersistentBlobProvider.getInstance(this)
                                   .create(masterSecret, imageBytes, ContentType.IMAGE_JPEG),
             MediaType.IMAGE);
    quickAttachmentDrawer.hide(false);
  }

  @Override
  public void onCameraFail() {
    Toast.makeText(this, R.string.ConversationActivity_quick_camera_unavailable, Toast.LENGTH_SHORT).show();
    quickAttachmentDrawer.hide(false);
    quickAttachmentToggle.disable();
  }

  @Override
  public void onCameraStart() {}

  @Override
  public void onCameraStop() {}

  @Override
  public void onRecorderStarted() {
    Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
    vibrator.vibrate(20);

    audioRecorder.startRecording();
  }

  @Override
  public void onRecorderFinished() {
    Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
    vibrator.vibrate(20);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final @NonNull Pair<Uri, Long> result) {
        try {
          boolean    forceSms       = false;
          int        subscriptionId = -1;
          long       expiresIn      = DatabaseFactory.getThreadPreferenceDatabase(ConversationActivity.this).getExpireMessages(threadId) * 1000;
          AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first, result.second, ContentType.AUDIO_AAC);
          SlideDeck  slideDeck      = new SlideDeck();
          slideDeck.addSlide(audioSlide);

          sendMediaMessage(forceSms, "", slideDeck, expiresIn, subscriptionId).addListener(new AssertedSuccessListener<Void>() {
            @Override
            public void onSuccess(Void nothing) {
              new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                  PersistentBlobProvider.getInstance(ConversationActivity.this).delete(result.first);
                  return null;
                }
              }.execute();
            }
          });
        } catch (InvalidMessageException e) {
          Log.w(TAG, e);
          Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_error_sending_voice_message, Toast.LENGTH_LONG).show();
        }
      }

      @Override
      public void onFailure(ExecutionException e) {
        Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onRecorderCanceled() {
    Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
    vibrator.vibrate(50);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final Pair<Uri, Long> result) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            PersistentBlobProvider.getInstance(ConversationActivity.this).delete(result.first);
            return null;
          }
        }.execute();
      }

      @Override
      public void onFailure(ExecutionException e) {}
    });
  }

  @Override
  public void onEmojiToggle() {
    if (container.getCurrentInput() == emojiDrawer) container.showSoftkey(composeText);
    else                                            container.show(composeText, emojiDrawer);
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void onAttachmentChanged() {
    updateToggleButtonState();
  }

  // Listeners

  private class AttachmentTypeListener implements AttachmentTypeSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      addAttachment(type);
    }
  }

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      if (!quickAttachmentDrawer.isShowing()) {
        composeText.clearFocus();
        container.show(composeText, quickAttachmentDrawer);
      } else {
        container.hideAttachedInput(false);
      }
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        return true;
      }
      return false;
    }
  }

  private class AttachButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private class AttachButtonLongClickListener implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(View v) {
      return sendButton.performLongClick();
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(ConversationActivity.this)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      container.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getText().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      if (composeText.getText().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(new Runnable() {
          @Override
          public void run() {
            updateToggleButtonState();
          }
        }, 50);
      }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  private ListenableFuture<Boolean> initializeDirectory()
  {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Recipients, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Recipients... params) {
        try {
          Context           context      = ConversationActivity.this;
          DirectoryHelper.refreshDirectoryFor(context, masterSecret, recipients);
          RecipientFactory.getRecipientsFor(context, recipients.getRecipientsList(), false);
        } catch (Exception e) {
          e.printStackTrace();
        }
        return true;
      }
    }.execute(recipients);

    return future;
  }
  }
