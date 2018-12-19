package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.crypto.IdentityKeyUtil;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.PreKeyUtil;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.jobmanager.requirements.NetworkRequirement;
import io.forsta.securesms.jobs.requirements.MasterSecretRequirement;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

public class CreateSignedPreKeyJob extends MasterSecretJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = CreateSignedPreKeyJob.class.getSimpleName();

  @Inject transient ForstaServiceAccountManager accountManager;

  public CreateSignedPreKeyJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public CreateSignedPreKeyJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withMasterSecretRequirement()
                                .withGroupId(CreateSignedPreKeyJob.class.getSimpleName())
                                .create());
  }

  @Override
  public void onAdded() {}

  @NonNull
  @Override
  protected Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {

  }

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    if (TextSecurePreferences.isSignedPreKeyRegistered(context)) {
      Log.w(TAG, "Signed prekey already registered...");
      return;
    }

    IdentityKeyPair    identityKeyPair    = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, identityKeyPair);

    accountManager.setSignedPreKey(signedPreKeyRecord);
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }
}
