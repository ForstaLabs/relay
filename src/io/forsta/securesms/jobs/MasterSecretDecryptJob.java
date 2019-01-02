package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.securesms.crypto.AsymmetricMasterCipher;
import io.forsta.securesms.crypto.AsymmetricMasterSecret;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.crypto.MasterSecretUtil;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.notifications.MessageNotifier;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.IOException;

public class MasterSecretDecryptJob extends MasterSecretJob {

  private static final long   serialVersionUID = 1L;
  private static final String TAG              = MasterSecretDecryptJob.class.getSimpleName();

  public MasterSecretDecryptJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MasterSecretDecryptJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withMasterSecretRequirement()
                                .create());
  }

  @Override
  public void onRun(MasterSecret masterSecret) {
    MmsDatabase        mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    MmsDatabase.Reader mmsReader   = mmsDatabase.getDecryptInProgressMessages(masterSecret);

    MessageRecord mmsRecord;

    while ((mmsRecord = mmsReader.getNext()) != null) {
      try {
        String body = getAsymmetricDecryptedBody(masterSecret, mmsRecord.getBody().getBody());
        mmsDatabase.updateMessageBody(new MasterSecretUnion(masterSecret), mmsRecord.getId(), body);
      } catch (InvalidMessageException e) {
        Log.w(TAG, e);
      }
    }

    mmsReader.close();

    MessageNotifier.updateNotification(context, masterSecret);
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
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
  public void onCanceled() {

  }

  private String getAsymmetricDecryptedBody(MasterSecret masterSecret, String body)
      throws InvalidMessageException
  {
    try {
      AsymmetricMasterSecret asymmetricMasterSecret = MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret);
      AsymmetricMasterCipher asymmetricMasterCipher = new AsymmetricMasterCipher(asymmetricMasterSecret);

      if (TextUtils.isEmpty(body)) return "";
      else                         return asymmetricMasterCipher.decryptBody(body);

    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }


}
