package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.jobs.requirements.MasterSecretRequirement;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.forsta.securesms.dependencies.AxolotlStorageModule;

public class CleanPreKeysJob extends MasterSecretJob implements InjectableType {

  private static final String TAG = CleanPreKeysJob.class.getSimpleName();

  private static final int ARCHIVE_AGE_DAYS = 15;

  @Inject transient ForstaServiceAccountManager accountManager;
  @Inject transient AxolotlStorageModule.SignedPreKeyStoreFactory signedPreKeyStoreFactory;

  public CleanPreKeysJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public CleanPreKeysJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(CleanPreKeysJob.class.getSimpleName())
                                .withMasterSecretRequirement()
                                .withRetryCount(5)
                                .create());
  }


  @Override
  public void onAdded() {

  }

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
    try {
      SignedPreKeyStore  signedPreKeyStore   = signedPreKeyStoreFactory.create();
      SignedPreKeyEntity currentSignedPreKey = accountManager.getSignedPreKey();

      if (currentSignedPreKey == null) return;

      SignedPreKeyRecord             currentRecord = signedPreKeyStore.loadSignedPreKey(currentSignedPreKey.getKeyId());
      List<SignedPreKeyRecord>       allRecords    = signedPreKeyStore.loadSignedPreKeys();
      LinkedList<SignedPreKeyRecord> oldRecords    = removeRecordFrom(currentRecord, allRecords);

      Collections.sort(oldRecords, new SignedPreKeySorter());

      Log.w(TAG, "Old signed prekey record count: " + oldRecords.size());

      boolean foundAgedRecord = false;

      for (SignedPreKeyRecord oldRecord : oldRecords) {
        long archiveDuration = System.currentTimeMillis() - oldRecord.getTimestamp();

        if (archiveDuration >= TimeUnit.DAYS.toMillis(ARCHIVE_AGE_DAYS)) {
          if (!foundAgedRecord) {
            foundAgedRecord = true;
          } else {
            Log.w(TAG, "Removing signed prekey record: " + oldRecord.getId() + " with timestamp: " + oldRecord.getTimestamp());
            signedPreKeyStore.removeSignedPreKey(oldRecord.getId());
          }
        }
      }
    } catch (InvalidKeyIdException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception throwable) {
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    if (throwable instanceof PushNetworkException)               return true;
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to execute clean signed prekeys task.");
  }

  private LinkedList<SignedPreKeyRecord> removeRecordFrom(SignedPreKeyRecord currentRecord,
                                                          List<SignedPreKeyRecord> records)

  {
    LinkedList<SignedPreKeyRecord> others = new LinkedList<>();

    for (SignedPreKeyRecord record : records) {
      if (record.getId() != currentRecord.getId()) {
        others.add(record);
      }
    }

    return others;
  }

  private static class SignedPreKeySorter implements Comparator<SignedPreKeyRecord> {
    @Override
    public int compare(SignedPreKeyRecord lhs, SignedPreKeyRecord rhs) {
      if      (lhs.getTimestamp() > rhs.getTimestamp()) return -1;
      else if (lhs.getTimestamp() < rhs.getTimestamp()) return 1;
      else                                              return 0;
    }
  }

}
