/**
 * Copyright (C) 2012 Moxie Marlinspike
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
package io.forsta.securesms.database.model;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.TextUtils;

import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.securesms.R;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.documents.IdentityKeyMismatch;
import io.forsta.securesms.database.documents.NetworkFailure;
import io.forsta.securesms.mms.DocumentSlide;
import io.forsta.securesms.mms.Slide;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;

import java.util.List;

import io.forsta.securesms.database.SmsDatabase;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

public class MediaMmsMessageRecord extends MessageRecord {
  private final static String TAG = MediaMmsMessageRecord.class.getSimpleName();

  private final Context context;
  private final int partCount;
  private final @NonNull SlideDeck slideDeck;

  public MediaMmsMessageRecord(Context context, long id, Recipients recipients,
                               Recipient individualRecipient, int recipientDeviceId,
                               long dateSent, long dateReceived, int receiptCount,
                               long threadId, Body body,
                               @NonNull SlideDeck slideDeck,
                               int partCount, long mailbox,
                               List<IdentityKeyMismatch> mismatches,
                               List<NetworkFailure> failures, int subscriptionId,
                               long expiresIn, long expireStarted)
  {
    super(context, id, body, recipients, individualRecipient, recipientDeviceId, dateSent,
          dateReceived, threadId, SmsDatabase.Status.STATUS_NONE, receiptCount, mailbox, mismatches, failures,
          subscriptionId, expiresIn, expireStarted);

    this.context   = context.getApplicationContext();
    this.partCount = partCount;
    this.slideDeck = slideDeck;
  }

  public @NonNull SlideDeck getSlideDeck() {
    return slideDeck;
  }

  public boolean containsMediaSlide() {
    return slideDeck.containsMediaSlide();
  }

  public int getPartCount() {
    return partCount;
  }

  @Override
  public boolean isMms() {
    return true;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }

  @Override
  public boolean isMediaPending() {
    for (Slide slide : getSlideDeck().getSlides()) {
      if (slide.isInProgress() || slide.isPendingDownload()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public SpannableString getDisplayBody() {
    if (MmsDatabase.Types.isDecryptInProgressType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_decrypting_mms_please_wait));
    } else if (MmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
    } else if (MmsDatabase.Types.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (MmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
    } else if (isLegacyMessage()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (!getBody().isPlaintext()) {
      return emphasisAdded(context.getString(R.string.MessageNotifier_locked_message));
    }

    return super.getDisplayBody();
  }

  public String getDocumentAttachmentFileName() {
    DocumentSlide documentSlide = getSlideDeck().getDocumentSlide();
    String fileName = documentSlide.getFileName().or(context.getString(R.string.DocumentView_unknown_file));
    for (ForstaMessage.ForstaAttachment attachment : getForstaMessageAttachments()) {
      if (documentSlide.getContentType().equals(attachment.getType())) {
        fileName = !TextUtils.isEmpty(attachment.getName()) ? attachment.getName() : fileName;
        break;
      }
    }
    return fileName;
  }
}
