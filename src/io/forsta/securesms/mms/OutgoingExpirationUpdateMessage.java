package io.forsta.securesms.mms;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.recipients.Recipients;

import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipients recipients, long sentTimeMillis, long expiresIn) {
    super(recipients, "", new LinkedList<Attachment>(), sentTimeMillis,
          ThreadDatabase.DistributionTypes.CONVERSATION, expiresIn);
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
