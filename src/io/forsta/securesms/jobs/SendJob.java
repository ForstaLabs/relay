package io.forsta.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.TextSecureExpiredException;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.jobmanager.JobParameters;
import io.forsta.securesms.mms.MediaConstraints;
import io.forsta.securesms.mms.MediaStream;
import io.forsta.securesms.transport.UndeliverableMessageException;
import io.forsta.securesms.util.Util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.MmsException;

public abstract class SendJob extends MasterSecretJob {

  private final static String TAG = SendJob.class.getSimpleName();

  public SendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  @Override
  public final void onRun(MasterSecret masterSecret) throws Exception {
    if (Util.getDaysTillBuildExpiry() <= 0) {
      throw new TextSecureExpiredException(String.format("TextSecure expired (build %d, now %d)",
                                                         BuildConfig.BUILD_TIMESTAMP,
                                                         System.currentTimeMillis()));
    }

    onSend(masterSecret);
  }

  protected abstract void onSend(MasterSecret masterSecret) throws Exception;

  protected void markAttachmentsUploaded(long messageId, @NonNull List<Attachment> attachments) {
    AttachmentDatabase database = DatabaseFactory.getAttachmentDatabase(context);

    for (Attachment attachment : attachments) {
      database.markAttachmentUploaded(messageId, attachment);
    }
  }

  protected List<Attachment> scaleAttachments(@NonNull MasterSecret masterSecret,
                                              @NonNull MediaConstraints constraints,
                                              @NonNull List<Attachment> attachments)
      throws UndeliverableMessageException
  {
    AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
    List<Attachment>   results            = new LinkedList<>();

    for (Attachment attachment : attachments) {
      try {
        if (constraints.isSatisfied(context, masterSecret, attachment)) {
          results.add(attachment);
        } else if (constraints.canResize(attachment)) {
          MediaStream resized = constraints.getResizedMedia(context, masterSecret, attachment);
          results.add(attachmentDatabase.updateAttachmentData(masterSecret, attachment, resized));
        } else {
          throw new UndeliverableMessageException("Size constraints could not be met!");
        }
      } catch (IOException | MmsException e) {
        throw new UndeliverableMessageException(e);
      }
    }

    return results;
  }
}
