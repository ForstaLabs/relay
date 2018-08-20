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

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.util.Log;

import com.google.android.gms.security.ProviderInstaller;

import io.forsta.securesms.crypto.PRNGFixes;
import io.forsta.securesms.dependencies.AxolotlStorageModule;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.dependencies.TextSecureCommunicationModule;
import io.forsta.securesms.jobs.CreateSignedPreKeyJob;
import io.forsta.securesms.jobs.GcmRefreshJob;
import io.forsta.securesms.jobs.persistence.EncryptingJobSerializer;
import io.forsta.securesms.jobs.requirements.MasterSecretRequirementProvider;
import io.forsta.securesms.jobs.requirements.MediaNetworkRequirementProvider;
import io.forsta.securesms.jobs.requirements.ServiceRequirementProvider;
import io.forsta.securesms.service.DirectoryRefreshListener;
import io.forsta.securesms.service.ExpiringMessageManager;
import io.forsta.securesms.util.TextSecurePreferences;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.dependencies.DependencyInjector;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
//import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;
//import org.whispersystems.libsignal.util.AndroidSignalProtocolLogger;

import java.util.HashSet;
import java.util.Set;

import dagger.ObjectGraph;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends Application implements DependencyInjector {

  private static final String TAG = ApplicationContext.class.getName();

  private ExpiringMessageManager expiringMessageManager;
  private JobManager             jobManager;
  private ObjectGraph            objectGraph;

  private MediaNetworkRequirementProvider mediaNetworkRequirementProvider = new MediaNetworkRequirementProvider();

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
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

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    System.out.println("XXX: Too lazy to port logging provider stuff from android build of libsignal-protocol");
    //SignalProtocolLoggerProvider.setProvider(new AndroidSignalProtocolLogger());
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("TextSecureJobs")
                                .withDependencyInjector(this)
                                .withJobSerializer(new EncryptingJobSerializer())
                                .withRequirementProviders(new MasterSecretRequirementProvider(this),
                                                          new ServiceRequirementProvider(this),
                                                          new NetworkRequirementProvider(this),
                                                          mediaNetworkRequirementProvider)
                                .withConsumerThreads(5)
                                .build();
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
        TextSecurePreferences.getGcmRegistrationId(this) == null)
    {
      this.jobManager.add(new GcmRefreshJob(this));
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

        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true);
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

}
