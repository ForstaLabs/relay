package io.forsta.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.ConversationActivity;
import io.forsta.securesms.database.ThreadPreferenceDatabase;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;

public class NotificationItem {

  private final @NonNull  Recipient individualRecipient;
  private final @NonNull Recipients        threadRecipients;
  private final @Nullable ThreadPreferenceDatabase.ThreadPreference threadPreferences;
  private final long                        threadId;
  private final @Nullable CharSequence      title;
  private final @Nullable CharSequence      text;
  private final long                        timestamp;
  private final @Nullable SlideDeck         slideDeck;

  public NotificationItem(@NonNull   Recipient individualRecipient,
                          @Nullable ThreadPreferenceDatabase.ThreadPreference threadPreferences,
                          @NonNull  Recipients threadRecipients,
                          long threadId, @Nullable CharSequence text, @Nullable CharSequence title, long timestamp,
                          @Nullable SlideDeck slideDeck)
  {
    this.individualRecipient = individualRecipient;
    this.threadPreferences = threadPreferences;
    this.threadRecipients    = threadRecipients;
    this.text                = text;
    this.title               = title;
    this.threadId            = threadId;
    this.timestamp           = timestamp;
    this.slideDeck           = slideDeck;
  }

  public @NonNull  Recipients getRecipients() {
    return threadRecipients;
  }

  public @Nullable ThreadPreferenceDatabase.ThreadPreference getThreadPreferences() {
    return threadPreferences;
  }

  public @NonNull  Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public CharSequence getText() {
    return text;
  }

  public CharSequence getTitle() {
    return title;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getThreadId() {
    return threadId;
  }

  public @Nullable SlideDeck getSlideDeck() {
    return slideDeck;
  }

  public PendingIntent getPendingIntent(Context context) {
    Intent     intent           = new Intent(context, ConversationActivity.class);
    intent.putExtra("recipients", threadRecipients.getIds());
    intent.putExtra("thread_id", threadId);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return TaskStackBuilder.create(context)
                           .addNextIntentWithParentStack(intent)
                           .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
