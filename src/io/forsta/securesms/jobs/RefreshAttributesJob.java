package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;

import java.io.IOException;

import javax.inject.Inject;

public class RefreshAttributesJob extends ContextJob implements InjectableType {

  public static final long serialVersionUID = 1L;

  private static final String TAG = RefreshAttributesJob.class.getSimpleName();

  @Inject transient ForstaServiceAccountManager textSecureAccountManager;

  public RefreshAttributesJob() {
    super(null, null);
  }

  public RefreshAttributesJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .create());
  }

  @Override
  public void onAdded() {}

  @Override
  protected void initialize(@NonNull SafeData data) {
  }

  @Override
  protected @NonNull
  Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  public void onRun() throws IOException {
    String signalingKey      = TextSecurePreferences.getSignalingKey(context);
    /* XXX: Need this now? */
    String gcmRegistrationId = TextSecurePreferences.getGcmRegistrationId(context);
    int    registrationId    = TextSecurePreferences.getLocalRegistrationId(context);

    String token = textSecureAccountManager.getAccountVerificationToken();

    textSecureAccountManager.setAccountAttributes(signalingKey, registrationId, true);
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to update account attributes!");
  }
}
