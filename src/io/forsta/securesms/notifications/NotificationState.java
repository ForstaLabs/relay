package io.forsta.securesms.notifications;

import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import io.forsta.securesms.ConversationActivity;
import io.forsta.securesms.ConversationPopupActivity;
import io.forsta.securesms.database.ThreadPreferenceDatabase;
import io.forsta.securesms.recipients.Recipients;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.forsta.securesms.database.RecipientPreferenceDatabase;

public class NotificationState {

  private final LinkedList<NotificationItem> notifications = new LinkedList<>();
  private final LinkedHashSet<Long> threads       = new LinkedHashSet<>();
  private boolean notify = false;
  private boolean vibrate = false;
  private String notificationChannel;

  private int notificationCount = 0;

  public NotificationState() {}

  public NotificationState(@NonNull List<NotificationItem> items) {
    for (NotificationItem item : items) {
      addNotification(item);
    }
  }

  public void addNotification(NotificationItem item) {
    notifications.addFirst(item);

    if (threads.contains(item.getThreadId())) {
      threads.remove(item.getThreadId());
    }

    threads.add(item.getThreadId());
    notificationCount++;
  }

  public @Nullable Uri getRingtone() {
    if (!notifications.isEmpty()) {
      ThreadPreferenceDatabase.ThreadPreference threadPreferences = notifications.getFirst().getThreadPreferences();
      if (threadPreferences != null) {
        return threadPreferences.getNotification();
      } else {
        return Settings.System.DEFAULT_NOTIFICATION_URI;
      }
    }

    return null;
  }

  public void setVibrateState(boolean vibrateState) {
    vibrate = vibrateState;
  }

  public boolean getVibrateState() {
    return vibrate;
  }

  public RecipientPreferenceDatabase.VibrateState getVibrate() {
    if (!notifications.isEmpty()) {
      Recipients recipients = notifications.getFirst().getRecipients();

      if (recipients != null) {
        return recipients.getVibrate();
      }
    }

    return RecipientPreferenceDatabase.VibrateState.DEFAULT;
  }

  public boolean hasMultipleThreads() {
    return threads.size() > 1;
  }

  public int getThreadCount() {
    return threads.size();
  }

  public int getMessageCount() {
    return notificationCount;
  }

  public List<NotificationItem> getNotifications() {
    return notifications;
  }

  public PendingIntent getMarkAsReadIntent(Context context) {
    long[] threadArray = new long[threads.size()];
    int    index       = 0;

    for (long thread : threads) {
      Log.w("NotificationState", "Added thread: " + thread);
      threadArray[index++] = thread;
    }

    Intent intent = new Intent(MarkReadReceiver.CLEAR_ACTION);
    intent.putExtra(MarkReadReceiver.THREAD_IDS_EXTRA, threadArray);
    intent.setPackage(context.getPackageName());

    // XXX : This is an Android bug.  If we don't pull off the extra
    // once before handing off the PendingIntent, the array will be
    // truncated to one element when the PendingIntent fires.  Thanks guys!
    Log.w("NotificationState", "Pending array off intent length: " +
        intent.getLongArrayExtra("thread_ids").length);

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getWearableReplyIntent(Context context, Recipients recipients) {
    if (threads.size() != 1) throw new AssertionError("We only support replies to single thread notifications!");

    Intent intent = new Intent(WearReplyReceiver.REPLY_ACTION);
    intent.putExtra(WearReplyReceiver.RECIPIENT_IDS_EXTRA, recipients.getIds());
    intent.setPackage(context.getPackageName());

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public PendingIntent getQuickReplyIntent(Context context, Recipients recipients) {
    if (threads.size() != 1) throw new AssertionError("We only support replies to single thread notifications!");

    Intent     intent           = new Intent(context, ConversationPopupActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, (long)threads.toArray()[0]);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public void setNotify(boolean state) {
    notify = state;
  }

  public boolean getNotify() {
    return notify;
  }

  public void setNotificationChannel(String channel) {
    this.notificationChannel = channel;
  }

  public String getNotificationChannel(Context context) {
    if (notificationChannel == null) {
      notificationChannel = NotificationChannels.getMessagesChannel(context);
      return notificationChannel;
    }
    return notificationChannel;
  }
}
