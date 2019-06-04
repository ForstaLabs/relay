/*
 * Copyright (C) 2013 Open Whisper Systems
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

import android.app.ActivityManager;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDexApplication;
import android.util.Log;

import androidx.work.WorkManager;
import io.forsta.securesms.crypto.PRNGFixes;
import io.forsta.securesms.dependencies.AxolotlStorageModule;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.dependencies.TextSecureCommunicationModule;
import io.forsta.securesms.jobs.CreateSignedPreKeyJob;
import io.forsta.securesms.jobs.FcmRefreshJob;
import io.forsta.securesms.jobs.PushNotificationReceiveJob;
import io.forsta.securesms.jobs.requirements.MediaNetworkRequirementProvider;
import io.forsta.securesms.service.ExpiringMessageManager;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.util.TextSecurePreferences;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import io.forsta.securesms.jobmanager.JobManager;
import io.forsta.securesms.jobmanager.dependencies.DependencyInjector;
import io.forsta.securesms.notifications.NotificationChannels;

import java.util.HashSet;
import java.util.Set;

import dagger.ObjectGraph;
import io.forsta.securesms.util.Util;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DependencyInjector, DefaultLifecycleObserver {

  private static final String TAG = ApplicationContext.class.getName();

  private ExpiringMessageManager expiringMessageManager;
  private JobManager             jobManager;
  private ObjectGraph            objectGraph;
  private boolean                initialized;
  private volatile boolean       isAppVisible;

  private MediaNetworkRequirementProvider mediaNetworkRequirementProvider = new MediaNetworkRequirementProvider();

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    synchronized(this) {
      super.onCreate();
      initializeRandomNumberFix();
      initializeLogging();
      initializeDependencyInjection();
      initializeJobManager();
      initializeExpiringMessageManager();
      initializeGcmCheck();
      initializeSignedPreKeyCheck();
//    initializePeriodicTasks();
//    initializeCircumvention();
      initializeWebRtc();
      initializePendingMessages();
      NotificationChannels.create(this);
      ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
      initialized = true;
      notifyAll();
    }
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
    Log.i(TAG, "App is now visible.");
    KeyCachingService.onAppForegrounded(this);
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    Log.i(TAG, "App is no longer visible.");
    KeyCachingService.onAppBackgrounded(this);
  }

  public void ensureInitialized() {
    synchronized (this) {
      while (!initialized) {
        Util.wait(this, 0);
      }
    }
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      ApplicationContext.getInstance(this).getJobManager().add(new PushNotificationReceiveJob(this));
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    System.out.println("XXX: Too lazy to port logging provider stuff from android build of libsignal-protocol");
    //SignalProtocolLoggerProvider.setProvider(new AndroidSignalProtocolLogger());
  }

  private void initializeJobManager() {
    this.jobManager = new JobManager(this, WorkManager.getInstance());
  }

  public void notifyMediaControlEvent() {
    mediaNetworkRequirementProvider.notifyMediaControlEvent();
  }

  private void initializeDependencyInjection() {
    this.objectGraph = ObjectGraph.create(new TextSecureCommunicationModule(this),
                                          new AxolotlStorageModule(this));
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this) &&
        TextSecurePreferences.getFcmToken(this) == null)
    {
      this.jobManager.add(new FcmRefreshJob(this));
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (TextSecurePreferences.isPushRegistered(this) &&
        !TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeWebRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (Build.VERSION.SDK_INT >= 11) {
        if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
          WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
        }

        if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
          WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
        }

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableVideoHwAcceleration(true)
            .createInitializationOptions());
      }
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, e);
    }
  }

//  private void initializePeriodicTasks() {
//    RotateSignedPreKeyListener.schedule(this);
//    DirectoryRefreshListener.schedule(this);
//
//    if (BuildConfig.PLAY_STORE_DISABLED) {
//      UpdateApkRefreshListener.schedule(this);
//    }
//  }
//
//  private void initializeCircumvention() {
//    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
//      @Override
//      protected Void doInBackground(Void... params) {
//        if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
//          try {
//            ProviderInstaller.installIfNeeded(ApplicationContext.this);
//          } catch (Throwable t) {
//            Log.w(TAG, t);
//          }
//        }
//        return null;
//      }
//    };
//
//    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  public void clearApplicationData() {
    ((ActivityManager) getSystemService(ACTIVITY_SERVICE))
        .clearApplicationUserData();
  }
}
