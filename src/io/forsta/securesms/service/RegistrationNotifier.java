package io.forsta.securesms.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.notifications.NotificationChannels;

public class RegistrationNotifier extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannels.OTHER);
    builder.setSmallIcon(R.drawable.icon_notification);
    builder.setContentTitle(intent.getStringExtra(RegistrationService.NOTIFICATION_TITLE));
    builder.setContentText(intent.getStringExtra(RegistrationService.NOTIFICATION_TEXT));
    builder.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, ConversationListActivity.class), 0));
    builder.setWhen(System.currentTimeMillis());
    builder.setDefaults(Notification.DEFAULT_VIBRATE);
    builder.setAutoCancel(true);

    Notification notification = builder.build();
    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(31337, notification);
  }
}
