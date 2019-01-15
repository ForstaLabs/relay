package io.forsta.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import io.forsta.securesms.ApplicationContext;
import io.forsta.securesms.R;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.jobmanager.requirements.NetworkRequirement;
import io.forsta.securesms.jobs.PushContentReceiveJob;
import io.forsta.securesms.jobs.PushNotificationReceiveJob;
import io.forsta.securesms.notifications.NotificationChannels;
import io.forsta.securesms.service.GenericForegroundService;
import io.forsta.securesms.util.TextSecurePreferences;

public class GcmBroadcastReceiver extends WakefulBroadcastReceiver implements InjectableType {

  private static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

  private static final Executor MESSAGE_EXECUTOR = newCachedSingleThreadExecutor("GcmProcessing");
  @Inject transient SignalServiceMessageReceiver messageReceiver;

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
    if (!incrementActiveGcmCount()) {
      Log.i(TAG, "Skipping GCM processing -- there's already one enqueued.");
      return;
    }

    TextSecurePreferences.setNeedsMessagePull(context, true);

    long          startTime    = System.currentTimeMillis();
    PendingResult callback     = goAsync();
    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    boolean       doze         = Build.VERSION.SDK_INT >= 23 ? powerManager.isDeviceIdleMode() : false;
    boolean       network      = new NetworkRequirement(context).isPresent();

    final Object         foregroundLock    = new Object();
    final AtomicBoolean foregroundRunning = new AtomicBoolean(false);
    final AtomicBoolean  taskCompleted     = new AtomicBoolean(false);

    if (doze || !network) {
      Log.i(TAG, "Starting a foreground task because we may be operating in a constrained environment. Doze: " + doze + " Network: " + network);
      showForegroundNotification(context);
      foregroundRunning.set(true);
      callback.finish();
    }

    MESSAGE_EXECUTOR.execute(() -> {
      try {
        new PushNotificationReceiveJob(context).pullAndProcessMessages(messageReceiver, TAG, startTime);
      } catch (IOException e) {
        Log.i(TAG, "Failed to retrieve the envelope. Scheduling on JobManager.", e);
        ApplicationContext.getInstance(context)
            .getJobManager()
            .add(new PushNotificationReceiveJob(context));
      } finally {
        synchronized (foregroundLock) {
          if (foregroundRunning.getAndSet(false)) {
            GenericForegroundService.stopForegroundTask(context);
          } else {
            callback.finish();
          }
          taskCompleted.set(true);
        }

        decrementActiveGcmCount();
        Log.i(TAG, "Processing complete.");
      }
    });

    if (!foregroundRunning.get()) {
      new Thread("GcmForegroundServiceTimer") {
        @Override
        public void run() {
          Util.sleep(4500);
          synchronized (foregroundLock) {
            if (!taskCompleted.get() && !foregroundRunning.getAndSet(true)) {
              Log.i(TAG, "Starting a foreground task because the job is running long.");
              showForegroundNotification(context);
              callback.finish();
            }
          }
        }
      }.start();
    }
  }

  private static synchronized boolean incrementActiveGcmCount() {
    if (activeCount < 2) {
      activeCount++;
      return true;
    }
    return false;
  }

  private static synchronized void decrementActiveGcmCount() {
    activeCount--;
  }

  private void showForegroundNotification(@NonNull Context context) {
    GenericForegroundService.startForegroundTask(context,
        context.getString(R.string.GcmBroadcastReceiver_retrieving_a_message),
        NotificationChannels.OTHER,
        R.drawable.icon);
  }

  public static ExecutorService newCachedSingleThreadExecutor(final String name) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, name));
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }
}
