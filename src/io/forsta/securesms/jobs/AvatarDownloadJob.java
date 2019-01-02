package io.forsta.securesms.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.work.Data;
import androidx.work.WorkerParameters;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.jobmanager.SafeData;
import io.forsta.securesms.jobs.requirements.MasterSecretRequirement;
import io.forsta.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import io.forsta.securesms.push.TextSecurePushTrustStore;
import io.forsta.securesms.util.BitmapDecodingException;
import io.forsta.securesms.util.BitmapUtil;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class AvatarDownloadJob extends MasterSecretJob {

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private static final String KEY_GROUP_ID = "group_id";

  private byte[] groupId;

  public AvatarDownloadJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public AvatarDownloadJob(Context context, byte[] groupId) {
    super(context, JobParameters.newBuilder()
                                .withMasterSecretRequirement()
                                .withNetworkRequirement()
                                .create());

    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  protected void initialize(@NonNull SafeData data) {
    try {
      groupId = GroupUtil.getDecodedId(data.getString(KEY_GROUP_ID));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putString(KEY_GROUP_ID, GroupUtil.getEncodedId(groupId)).build();
  }

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    GroupDatabase             database   = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.GroupRecord record     = database.getGroup(groupId);
    File                      attachment = null;

    try {
      if (record != null) {
        long   avatarId = record.getAvatarId();
        byte[] key      = record.getAvatarKey();
        String relay    = record.getRelay();

        if (avatarId == -1 || key == null) {
          return;
        }

        attachment = downloadAttachment(relay, avatarId);
        Bitmap avatar = BitmapUtil.createScaledBitmap(context, new AttachmentModel(attachment, key), 500, 500);

        database.updateAvatar(groupId, avatar);
      }
    } catch (BitmapDecodingException | NonSuccessfulResponseCodeException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  private File downloadAttachment(String relay, long contentLocation) throws IOException {
    PushServiceSocket socket = new PushServiceSocket(TextSecurePreferences.getServer(context),
                                                     new TextSecurePushTrustStore(context),
                                                     new StaticCredentialsProvider(TextSecurePreferences.getLocalNumber(context),
                                                                                   TextSecurePreferences.getPushServerPassword(context),
                                                                                   null),
                                                     TextSecurePreferences.getUserAgent(context));

    File destination = File.createTempFile("avatar", "tmp");

    destination.deleteOnExit();

    socket.retrieveAttachment(relay, contentLocation, destination, null);

    return destination;
  }

}
