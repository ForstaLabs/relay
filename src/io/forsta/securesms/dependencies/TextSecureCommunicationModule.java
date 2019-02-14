package io.forsta.securesms.dependencies;

import android.content.Context;

import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.DeviceListFragment;
import io.forsta.securesms.crypto.storage.SignalProtocolStoreImpl;
import io.forsta.securesms.gcm.GcmBroadcastReceiver;
import io.forsta.securesms.jobs.AttachmentDownloadJob;
import io.forsta.securesms.jobs.CleanPreKeysJob;
import io.forsta.securesms.jobs.CreateSignedPreKeyJob;
import io.forsta.securesms.jobs.DeliveryReceiptJob;
import io.forsta.securesms.jobs.GcmRefreshJob;
import io.forsta.securesms.jobs.MultiDeviceBlockedUpdateJob;
import io.forsta.securesms.jobs.MultiDeviceContactUpdateJob;
import io.forsta.securesms.jobs.MultiDeviceGroupUpdateJob;
import io.forsta.securesms.jobs.MultiDeviceReadUpdateJob;
import io.forsta.securesms.jobs.PushMediaSendJob;
import io.forsta.securesms.jobs.PushNotificationReceiveJob;
import io.forsta.securesms.jobs.PushTextSendJob;
import io.forsta.securesms.jobs.RefreshAttributesJob;
import io.forsta.securesms.jobs.RefreshPreKeysJob;
import io.forsta.securesms.push.SecurityEventListener;
import io.forsta.securesms.push.TextSecurePushTrustStore;
import io.forsta.securesms.service.MessageRetrievalService;
import io.forsta.securesms.service.WebRtcCallService;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     DeliveryReceiptJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     MultiDeviceReadUpdateJob.class,
                                     MultiDeviceBlockedUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmBroadcastReceiver.class,
                                     GcmRefreshJob.class,WebRtcCallService.class})
public class TextSecureCommunicationModule {

  private final Context context;

  public TextSecureCommunicationModule(Context context) {
    this.context = context;
  }

  public static SignalServiceMessageSender createMessageSender(Context context) {
    return new SignalServiceMessageSender(TextSecurePreferences.getServer(context),
        new TextSecurePushTrustStore(context),
        TextSecurePreferences.getLocalNumber(context),
        TextSecurePreferences.getLocalDeviceId(context),
        TextSecurePreferences.getPushServerPassword(context),
        new SignalProtocolStoreImpl(context),
        TextSecurePreferences.getUserAgent(context),
        Optional.<SignalServiceMessageSender.EventListener>of(new SecurityEventListener(context)));
  }

  @Provides ForstaServiceAccountManager provideTextSecureAccountManager() {
    return new ForstaServiceAccountManager(TextSecurePreferences.getServer(context),
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getLocalDeviceId(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           TextSecurePreferences.getUserAgent(context));
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public SignalServiceMessageSender create() {
        return new SignalServiceMessageSender(TextSecurePreferences.getServer(context),
                                              new TextSecurePushTrustStore(context),
                                              TextSecurePreferences.getLocalNumber(context),
                                              TextSecurePreferences.getLocalDeviceId(context),
                                              TextSecurePreferences.getPushServerPassword(context),
                                              new SignalProtocolStoreImpl(context),
                                              TextSecurePreferences.getUserAgent(context),
                                              Optional.<SignalServiceMessageSender.EventListener>of(new SecurityEventListener(context)));
      }
    };
  }

  @Provides SignalServiceMessageReceiver provideTextSecureMessageReceiver() {
    return new SignalServiceMessageReceiver(TextSecurePreferences.getServer(context),
                                         new TextSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context),
                                         TextSecurePreferences.getUserAgent(context));
  }

  public static interface TextSecureMessageSenderFactory {
    public SignalServiceMessageSender create();
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context) + "." + TextSecurePreferences.getLocalDeviceId(context);

    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

}
