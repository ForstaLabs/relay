package io.forsta.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.jobs.PushContentReceiveJob;
import io.forsta.securesms.jobs.PushNotificationReceiveJob;
import io.forsta.securesms.util.TextSecurePreferences;

public class GcmBroadcastReceiver extends WakefulBroadcastReceiver {

  private static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

  private static final Executor MESSAGE_EXECUTOR = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, "GcmProcessing"));
  @Inject transient SignalServiceMessageReceiver receiver;

  private static int activeCount = 0;

  @Override
  public void onReceive(Context context, Intent intent) {
    ApplicationContext.getInstance(context).injectDependencies(this);

    GoogleCloudMessaging gcm         = GoogleCloudMessaging.getInstance(context);
    String               messageType = gcm.getMessageType(intent);
    Log.w(TAG, "PushNotification...");

    if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
      if (!TextSecurePreferences.isPushRegistered(context)) {
        Log.w(TAG, "Not push registered!");
        return;
      }

      String messageData = intent.getStringExtra("message");
      String receiptData = intent.getStringExtra("receipt");

      if      (!TextUtils.isEmpty(messageData)) {
        Log.w(TAG, "PushNotification message " + messageData);
        handleReceivedMessage(context, messageData);
      }
      else if (!TextUtils.isEmpty(receiptData)) {
        Log.w(TAG, "PushNotification receipt " + receiptData);
        handleReceivedMessage(context, receiptData);
      }
      else if (intent.hasExtra("notification")) {
        Log.w(TAG, "PushNotification notification");
        handleReceivedNotification(context);
      } else {
        Log.w(TAG, "PushNotification no notification information:");
      }
    }
  }

  private void handleReceivedMessage(Context context, String data) {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new PushContentReceiveJob(context, data));
  }

  private void handleReceivedNotification(Context context) {
    try {
      receiver.retrieveMessages(envelope -> {

      });
    } catch (IOException e) {
      e.printStackTrace();
    }

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new PushNotificationReceiveJob(context));
  }
}
