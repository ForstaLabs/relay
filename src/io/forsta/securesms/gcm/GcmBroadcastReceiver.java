package io.forsta.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.jobs.PushContentReceiveJob;
import io.forsta.securesms.jobs.PushNotificationReceiveJob;
import io.forsta.securesms.util.TextSecurePreferences;

public class GcmBroadcastReceiver extends WakefulBroadcastReceiver {

  private static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    GoogleCloudMessaging gcm         = GoogleCloudMessaging.getInstance(context);
    String               messageType = gcm.getMessageType(intent);
    Log.w(TAG, "onReceive..." + messageType);

    if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        Log.w(TAG, "Not push registered!");
        return;
      }

      String messageData = intent.getStringExtra("message");
      String receiptData = intent.getStringExtra("receipt");

      if      (!TextUtils.isEmpty(messageData)) {
        Log.w(TAG, "Gcm message " + messageData);
        handleReceivedMessage(context, messageData);
      }
      else if (!TextUtils.isEmpty(receiptData)) {
        Log.w(TAG, "Gcm reciept " + receiptData);
        handleReceivedMessage(context, receiptData);
      }
      else if (intent.hasExtra("notification")) {
        Log.w(TAG, "Gcm notification");
        handleReceivedNotification(context);
      }
    }
  }

  private void handleReceivedMessage(Context context, String data) {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new PushContentReceiveJob(context, data));
  }

  private void handleReceivedNotification(Context context) {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new PushNotificationReceiveJob(context));
  }
}
