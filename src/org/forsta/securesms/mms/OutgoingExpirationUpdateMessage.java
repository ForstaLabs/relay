package org.forsta.securesms.mms;

import org.forsta.securesms.attachments.Attachment;
import org.forsta.securesms.database.ThreadDatabase;
import org.forsta.securesms.recipients.Recipients;

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
