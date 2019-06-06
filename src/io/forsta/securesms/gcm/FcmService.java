package io.forsta.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
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
import io.forsta.securesms.jobs.FcmRefreshJob;
import io.forsta.securesms.jobs.PushNotificationReceiveJob;
import io.forsta.securesms.notifications.NotificationChannels;
import io.forsta.securesms.service.GenericForegroundService;
import io.forsta.securesms.util.PowerManagerCompat;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.TextSecurePreferences;

public class FcmService extends FirebaseMessagingService implements InjectableType {

  private static final String TAG = FcmService.class.getSimpleName();
  private static final Executor MESSAGE_EXECUTOR = newCachedSingleThreadExecutor("FcmMessageProcessing");

  @Inject
  SignalServiceMessageReceiver messageReceiver;

  private static int activeCount;

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.i(TAG, "FCM message... Original Priority: " + remoteMessage.getOriginalPriority() + ", Actual Priority: " + remoteMessage.getPriority());
    ApplicationContext.getInstance(getApplicationContext()).injectDependencies(this);
    handleReceivedNotification(getApplicationContext());
//    WakeLockUtil.runWithLock(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK, 60000, WAKE_LOCK_TAG, () -> {
//      handleReceivedNotification(getApplicationContext());
//    });
  }

  @Override
  public void onNewToken(String token) {
    Log.i(TAG, "onNewToken()");

    if (!TextSecurePreferences.isPushRegistered(getApplicationContext())) {
      Log.i(TAG, "Got a new FCM token, but the user isn't registered.");
      return;
    }

    ApplicationContext.getInstance(getApplicationContext())
        .getJobManager()
        .add(new FcmRefreshJob(getApplicationContext()));
  }

  private void handleReceivedNotification(Context context) {
    if (!incrementActiveGcmCount()) {
      Log.i(TAG, "Skipping FCM processing -- there's already one enqueued.");
      return;
    }

    TextSecurePreferences.setNeedsMessagePull(context, true);

    long         startTime    = System.currentTimeMillis();
    PowerManager powerManager = ServiceUtil.getPowerManager(getApplicationContext());
    boolean      doze         = PowerManagerCompat.isDeviceIdleMode(powerManager);
    boolean      network      = new NetworkRequirement(context).isPresent();

    final Object         foregroundLock    = new Object();
    final AtomicBoolean foregroundRunning = new AtomicBoolean(false);
    final AtomicBoolean  taskCompleted     = new AtomicBoolean(false);
    final CountDownLatch latch             = new CountDownLatch(1);

    if (doze || !network) {
      Log.i(TAG, "Starting a foreground task because we may be operating in a constrained environment. Doze: " + doze + " Network: " + network);
      showForegroundNotification(context);
      foregroundRunning.set(true);
      latch.countDown();
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
            latch.countDown();
          }
          taskCompleted.set(true);
        }

        decrementActiveGcmCount();
        Log.i(TAG, "Processing complete.");
      }
    });

    if (!foregroundRunning.get()) {
      new Thread("FcmForegroundServiceTimer") {
        @Override
        public void run() {
          Util.sleep(7000);
          synchronized (foregroundLock) {
            if (!taskCompleted.get() && !foregroundRunning.getAndSet(true)) {
              Log.i(TAG, "Starting a foreground task because the job is running long.");
              showForegroundNotification(context);
              latch.countDown();
            }
          }
        }
      }.start();
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      Log.w(TAG, "Latch was interrupted.", e);
    }
  }

  private void showForegroundNotification(@NonNull Context context) {
    GenericForegroundService.startForegroundTask(context,
        context.getString(R.string.GcmBroadcastReceiver_retrieving_a_message),
        NotificationChannels.OTHER,
        R.drawable.icon);
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

  public static ExecutorService newCachedSingleThreadExecutor(final String name) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, name));
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

}
