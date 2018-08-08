package io.forsta.securesms.jobs;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.attachments.AttachmentId;
import io.forsta.securesms.crypto.AsymmetricMasterSecret;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUtil;
import io.forsta.securesms.crypto.MediaKey;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.events.PartProgressEvent;
import io.forsta.securesms.jobs.requirements.MasterSecretRequirement;
import io.forsta.securesms.jobs.requirements.MediaNetworkRequirement;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.util.VisibleForTesting;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import org.greenrobot.eventbus.EventBus;
import ws.com.google.android.mms.MmsException;

public class AttachmentDownloadJob extends MasterSecretJob implements InjectableType {
  private static final long   serialVersionUID = 1L;
  private static final String TAG              = AttachmentDownloadJob.class.getSimpleName();

  @Inject transient SignalServiceMessageReceiver messageReceiver;

  private final long messageId;
  private final long partRowId;
  private final long partUniqueId;

  public AttachmentDownloadJob(Context context, long messageId, AttachmentId attachmentId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(AttachmentDownloadJob.class.getCanonicalName())
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withRequirement(new MediaNetworkRequirement(context, messageId, attachmentId))
                                .withPersistence()
                                .create());

    this.messageId    = messageId;
    this.partRowId    = attachmentId.getRowId();
    this.partUniqueId = attachmentId.getUniqueId();
  }

  @Override
  public void onAdded() {
  }

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    final AttachmentId attachmentId = new AttachmentId(partRowId, partUniqueId);
    final Attachment attachment   = DatabaseFactory.getAttachmentDatabase(context).getAttachment(attachmentId);

    if (attachment == null) {
      Log.w(TAG, "attachment no longer exists.");
      return;
    }

    if (!attachment.isInProgress()) {
      Log.w(TAG, "Attachment was already downloaded.");
      return;
    }

    Log.w(TAG, "Downloading push part " + attachmentId);

    retrieveAttachment(masterSecret, messageId, attachmentId, attachment);
    MessageNotifier.updateNotification(context, masterSecret);
  }

  @Override
  public void onCanceled() {
    final AttachmentId attachmentId = new AttachmentId(partRowId, partUniqueId);
    markFailed(messageId, attachmentId);
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return (exception instanceof PushNetworkException);
  }

  private void retrieveAttachment(MasterSecret masterSecret,
                                  long messageId,
                                  final AttachmentId attachmentId,
                                  final Attachment attachment)
      throws IOException
  {

    AttachmentDatabase database       = DatabaseFactory.getAttachmentDatabase(context);
    File               attachmentFile = null;

    try {
      attachmentFile = createTempFile();

      SignalServiceAttachmentPointer pointer = createAttachmentPointer(masterSecret, attachment);
      InputStream                    stream  = messageReceiver.retrieveAttachment(pointer, attachmentFile, new ProgressListener() {
        @Override
        public void onAttachmentProgress(long total, long progress) {
          EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress));
        }
      });

      database.insertAttachmentsForPlaceholder(masterSecret, messageId, attachmentId, stream);
    } catch (InvalidPartException | NonSuccessfulResponseCodeException | InvalidMessageException | MmsException e) {
      Log.w(TAG, e);
      markFailed(messageId, attachmentId);
    } finally {
      if (attachmentFile != null)
        attachmentFile.delete();
    }
  }

  @VisibleForTesting
  SignalServiceAttachmentPointer createAttachmentPointer(MasterSecret masterSecret, Attachment attachment)
      throws InvalidPartException
  {
    if (TextUtils.isEmpty(attachment.getLocation())) {
      throw new InvalidPartException("empty content id");
    }

    if (TextUtils.isEmpty(attachment.getKey())) {
      throw new InvalidPartException("empty encrypted key");
    }

    try {
      AsymmetricMasterSecret asymmetricMasterSecret = MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret);
      long                   id                     = Long.parseLong(attachment.getLocation());
      byte[]                 key                    = MediaKey.getDecrypted(masterSecret, asymmetricMasterSecret, attachment.getKey());
      String                 relay                  = null;

      if (TextUtils.isEmpty(attachment.getRelay())) {
        relay = attachment.getRelay();
      }

      return new SignalServiceAttachmentPointer(id, null, key, relay);
    } catch (InvalidMessageException | IOException e) {
      Log.w(TAG, e);
      throw new InvalidPartException(e);
    }
  }

  private File createTempFile() throws InvalidPartException {
    try {
      File file = File.createTempFile("push-attachment", "tmp", context.getCacheDir());
      file.deleteOnExit();

      return file;
    } catch (IOException e) {
      throw new InvalidPartException(e);
    }
  }

  private void markFailed(long messageId, AttachmentId attachmentId) {
    try {
      AttachmentDatabase database = DatabaseFactory.getAttachmentDatabase(context);
      database.setTransferProgressFailed(attachmentId, messageId);
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  @VisibleForTesting static class InvalidPartException extends Exception {
    public InvalidPartException(String s) {super(s);}
    public InvalidPartException(Exception e) {super(e);}
  }

}
