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
package io.forsta.securesms.notifications;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.Pair;

import com.google.common.primitives.Chars;

import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.securesms.ConversationActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MessagingDatabase.MarkedMessageInfo;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.PushDatabase;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.database.ThreadPreferenceDatabase;
import io.forsta.securesms.database.model.MediaMmsMessageRecord;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.SpanUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.leolin.shortcutbadger.ShortcutBadger;


/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */

public class MessageNotifier {

  private static final String TAG = MessageNotifier.class.getSimpleName();
  private static final long ALARM_DEBOUNCE_TIME = 3000L;
  public static final int NOTIFICATION_ID = 1338;

  private volatile static long notificationThreadId;
  private volatile static long visibleThread = -1;
  private volatile static long lastUpdate = 0L;

  public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";

  public static void setVisibleThread(long threadId) {
    visibleThread = threadId;
  }

  public static void notifyMessageDeliveryFailed(Context context, Recipients recipients, long threadId) {
    if (visibleThread == threadId) {
      sendInThreadNotification(context, recipients);
    } else {
      Intent intent = new Intent(context, ConversationActivity.class);
      intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
      intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      intent.setData((Uri.parse("custom://" + System.currentTimeMillis())));

      FailedNotificationBuilder builder = new FailedNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context), intent);
      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify((int)threadId, builder.build());
    }
  }

  public static void updateNotification(@NonNull Context context, @Nullable MasterSecret masterSecret) {
    if (!TextSecurePreferences.isNotificationsEnabled(context) || masterSecret == null) {
      return;
    }

    updateNotification(context, masterSecret, false, false, 0);
  }

  // Main entry point for incoming message notifications.
  public static void updateNotification(@NonNull  Context context,
                                        @Nullable MasterSecret masterSecret,
                                        long threadId)
  {
    updateNotification(context, masterSecret, false, threadId);
  }

  public static void updateNotification(@NonNull  Context context,
                                        @Nullable MasterSecret masterSecret,
                                        boolean   includePushDatabase,
                                        long      threadId)
  {
    updateNotification(context, masterSecret, includePushDatabase, threadId, true);
  }

  public static void updateNotification(@NonNull  Context context,
                                        @Nullable MasterSecret masterSecret,
                                        boolean   includePushDatabase,
                                        long      threadId,
                                        boolean   signal)
  {
    boolean    isVisible  = visibleThread == threadId;
    ThreadDatabase threads    = DatabaseFactory.getThreadDatabase(context);

    if (isVisible) {
      List<MarkedMessageInfo> messageIds = threads.setRead(threadId);
      MarkReadReceiver.process(context, messageIds);
    }

    if (!TextSecurePreferences.isNotificationsEnabled(context))
    {
      return;
    }

    if (isVisible) {
      sendInThreadNotification(context, threads.getRecipientsForThreadId(threadId));
    } else {
      updateNotification(context, masterSecret, signal, includePushDatabase, 0);
    }
  }

  private static void updateNotification(@NonNull  Context context,
                                         @Nullable MasterSecret masterSecret,
                                         boolean signal,
                                         boolean includePushDatabase,
                                         int     reminderCount)
  {
    Cursor telcoCursor = null;
    Cursor pushCursor  = null;

    try {
      telcoCursor = DatabaseFactory.getMmsDatabase(context).getUnread();
      pushCursor  = includePushDatabase ? DatabaseFactory.getPushDatabase(context).getPending() : null;

      if ((telcoCursor == null || telcoCursor.isAfterLast()) &&
          (pushCursor == null || pushCursor.isAfterLast()))
      {
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(NOTIFICATION_ID);
        updateBadge(context, 0);
        clearReminder(context);
        return;
      }

      NotificationState notificationState = constructNotificationState(context, masterSecret, telcoCursor);

      if (includePushDatabase) {
        appendPushNotificationState(context, notificationState, pushCursor);
      }

      if (notificationState.getNotify()) {
        if (notificationState.hasMultipleThreads()) {
          sendMultipleThreadNotification(context, notificationState, signal);
        } else {
          sendSingleThreadNotification(context, masterSecret, notificationState, signal);
        }
      }

      int unreadMessageCount = telcoCursor.getCount();
      updateBadge(context, unreadMessageCount);

      if (signal) {
        scheduleReminder(context, reminderCount);
      }
    } finally {
      if (telcoCursor != null) telcoCursor.close();
      if (pushCursor != null)  pushCursor.close();
    }
  }

  private static void sendSingleThreadNotification(@NonNull  Context context,
                                                   @Nullable MasterSecret masterSecret,
                                                   @NonNull  NotificationState notificationState,
                                                   boolean signal)
  {


    if (notificationState.getNotifications().isEmpty()) {
      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(NOTIFICATION_ID);
      return;
    }

    SingleRecipientNotificationBuilder builder       = new SingleRecipientNotificationBuilder(context, masterSecret, TextSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>             notifications = notificationState.getNotifications();
    Recipients                         recipients    = notifications.get(0).getRecipients();

    builder.setThread(notifications.get(0).getRecipients(), notifications.get(0).getTitle().toString());
    builder.setMessageCount(notificationState.getMessageCount());
    builder.setPrimaryMessageBody(recipients, notifications.get(0).getIndividualRecipient(),
                                  notifications.get(0).getText(), notifications.get(0).getSlideDeck());
    builder.setContentIntent(notifications.get(0).getPendingIntent(context));

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(masterSecret,
                       notificationState.getMarkAsReadIntent(context),
                       notificationState.getQuickReplyIntent(context, notifications.get(0).getRecipients()),
                       notificationState.getWearableReplyIntent(context, notifications.get(0).getRecipients()));

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getRecipients(), item.getIndividualRecipient(), item.getText());
    }

    if (signal) {
      if (System.currentTimeMillis() - lastUpdate > ALARM_DEBOUNCE_TIME) {
        builder.setAlarms(notificationState.getRingtone(), notificationState.getVibrate());
      }
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notificationManager.createNotificationChannel(builder.getChannel());
    }

    notificationManager.notify(NOTIFICATION_ID, builder.build());

    lastUpdate = System.currentTimeMillis();
  }

  private static void sendMultipleThreadNotification(@NonNull  Context context,
                                                     @NonNull  NotificationState notificationState,
                                                     boolean signal)
  {
    MultipleRecipientNotificationBuilder builder       = new MultipleRecipientNotificationBuilder(context, TextSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>               notifications = notificationState.getNotifications();

    builder.setMessageCount(notificationState.getMessageCount(), notificationState.getThreadCount());
    builder.setMostRecentSender(notifications.get(0).getIndividualRecipient());

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(notificationState.getMarkAsReadIntent(context));

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      NotificationItem item = iterator.previous();
      builder.addMessageBody(item.getIndividualRecipient(), item.getText());
    }

    if (signal) {
      if (System.currentTimeMillis() - lastUpdate > ALARM_DEBOUNCE_TIME) {
        builder.setAlarms(notificationState.getRingtone(), notificationState.getVibrate());
      }
      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
      .notify(NOTIFICATION_ID, builder.build());

    lastUpdate = System.currentTimeMillis();
  }

  private static void sendInThreadNotification(Context context, Recipients recipients) {
    if (!TextSecurePreferences.isInThreadNotifications(context) ||
        ServiceUtil.getAudioManager(context).getRingerMode() != AudioManager.RINGER_MODE_NORMAL)
    {
      return;
    }

    Uri uri = recipients != null ? recipients.getRingtone() : null;

    if (uri == null) {
      String ringtone = TextSecurePreferences.getNotificationRingtone(context);

      if (ringtone == null) {
        Log.w(TAG, "ringtone preference was null.");
        return;
      }

      uri = Uri.parse(ringtone);

      if (uri == null) {
        Log.w(TAG, "couldn't parse ringtone uri " + ringtone);
        return;
      }
    }

    if (uri.toString().isEmpty()) {
      Log.d(TAG, "ringtone uri is empty");
      return;
    }

    Ringtone ringtone = RingtoneManager.getRingtone(context, uri);

    if (ringtone == null) {
      Log.w(TAG, "ringtone is null");
      return;
    }

    if (Build.VERSION.SDK_INT >= 21) {
      ringtone.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                                                               .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                                                               .build());
    } else {
      ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION);
    }

    ringtone.play();
  }

  private static void appendPushNotificationState(@NonNull Context context,
                                                  @NonNull NotificationState notificationState,
                                                  @NonNull Cursor cursor)
  {
    PushDatabase.Reader reader = null;
    SignalServiceEnvelope envelope;

    try {
      reader = DatabaseFactory.getPushDatabase(context).readerFor(cursor);

      while ((envelope = reader.getNext()) != null) {
        Recipients      recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
        Recipient       recipient  = recipients.getPrimaryRecipient();
        long            threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
        SpannableString body       = new SpannableString(context.getString(R.string.MessageNotifier_locked_message));
        body.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        ThreadPreferenceDatabase.ThreadPreference threadPreference = DatabaseFactory.getThreadPreferenceDatabase(context).getThreadPreferences(threadId);

        if (threadPreference == null || !threadPreference.isMuted()) {
          notificationState.addNotification(new NotificationItem(recipient, recipients, null, threadId, body, "Locked",0, null));
        }
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private static NotificationState constructNotificationState(@NonNull  Context context,
                                                              @Nullable MasterSecret masterSecret,
                                                              @NonNull  Cursor cursor)
  {
    NotificationState notificationState = new NotificationState();
    MessageRecord record;
    MmsDatabase.Reader reader;
    Set<String> filters = TextSecurePreferences.getNotificationPreferences(context);

    if (masterSecret == null) reader = DatabaseFactory.getMmsDatabase(context).readerFor(cursor);
    else                      reader = DatabaseFactory.getMmsDatabase(context).readerFor(masterSecret, cursor);

    while ((record = reader.getNext()) != null) {
      long         threadId         = record.getThreadId();
      Recipients   threadRecipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);;
      Recipient    recipient        = record.getIndividualRecipient();
      Recipients   recipients       = record.getRecipients();
      ForstaThread forstaThread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      CharSequence title = forstaThread != null ? forstaThread.getTitle() : "";
      CharSequence body             = record.getPlainTextBody();
      SlideDeck    slideDeck        = null;
      long         timestamp        = record.getTimestamp();
      boolean isDirectMessage = threadRecipients != null && threadRecipients.isSingleRecipient();
      boolean isNamed = record.isNamed(context);
      boolean isMentioned = record.isMentioned(context);
      boolean threadNotification = showThreadNotification(context, threadId);
      boolean messageNotification = showFilteredNotification(filters, isDirectMessage, isNamed, isMentioned);

      if (record.isMediaPending() && TextUtils.isEmpty(body)) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_media_message));
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
      } else if (record.isMediaPending() && !record.isMmsNotification()) {
        String message = context.getString(R.string.MessageNotifier_media_message_with_text, body);
        int    italicLength = message.length() - body.length();
        body = SpanUtil.italic(message, italicLength);
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
      }

      if (threadRecipients != null && threadNotification && messageNotification) {
        notificationState.setNotify(true);
        notificationState.addNotification(new NotificationItem(recipient, recipients, threadRecipients, threadId, body, title, timestamp, slideDeck));
      } else {
        notificationState.setNotify(false);
      }
    }

    reader.close();
    return notificationState;
  }

  private static void updateBadge(Context context, int count) {
    try {
      if (count == 0) ShortcutBadger.removeCount(context);
      else            ShortcutBadger.applyCount(context, count);
    } catch (Throwable t) {
      // NOTE :: I don't totally trust this thing, so I'm catching
      // everything.
      Log.w("MessageNotifier", t);
    }
  }

  private static void scheduleReminder(Context context, int count) {
    if (count >= TextSecurePreferences.getRepeatAlertsCount(context)) {
      return;
    }

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent       alarmIntent  = new Intent(ReminderReceiver.REMINDER_ACTION);
    alarmIntent.putExtra("reminder_count", count);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    long          timeout       = TimeUnit.MINUTES.toMillis(2);

    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, pendingIntent);
  }

  private static void clearReminder(Context context) {
    Intent        alarmIntent   = new Intent(ReminderReceiver.REMINDER_ACTION);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(pendingIntent);
  }

  public static class ReminderReceiver extends BroadcastReceiver {

    public static final String REMINDER_ACTION = "io.forsta.securesms.MessageNotifier.REMINDER_ACTION";

    @Override
    public void onReceive(final Context context, final Intent intent) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          MasterSecret masterSecret  = KeyCachingService.getMasterSecret(context);
          int          reminderCount = intent.getIntExtra("reminder_count", 0);
          MessageNotifier.updateNotification(context, masterSecret, true, true, reminderCount + 1);

          return null;
        }
      }.execute();
    }
  }

  public static class DeleteReceiver extends BroadcastReceiver {

    public static final String DELETE_REMINDER_ACTION = "io.forsta.securesms.MessageNotifier.DELETE_REMINDER_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
      clearReminder(context);
    }
  }

  private static boolean showThreadNotification(Context context, long threadId) {
    ThreadPreferenceDatabase.ThreadPreference threadPreference = DatabaseFactory.getThreadPreferenceDatabase(context).getThreadPreferences(threadId);
    if (TextSecurePreferences.isNotificationsEnabled(context) && (threadPreference != null && !threadPreference.isMuted()))
    {
      return true;
    }
    return false;
  }

  private static boolean showFilteredNotification(Set<String> filters, boolean isDirect, boolean isNamed, boolean isMentioned) {
    boolean result = true;
    if (filters.size() > 0) {
      result = false;
      for (String item : filters) {
        if (item.equals("dm")) {
          if (isDirect) {
            result = true;
          }
        }

        if (item.equals("mention")) {
          if (isMentioned) {
            result = true;
          }
        }

        if (item.equals("name")) {
          if (isNamed) {
            result = true;
          }
        }
      }
    }

    return result;
  }
}
