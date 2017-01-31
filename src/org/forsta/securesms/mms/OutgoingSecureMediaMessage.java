package org.forsta.securesms.mms;

import org.forsta.securesms.attachments.Attachment;
import org.forsta.securesms.recipients.Recipients;

import java.util.List;

public class OutgoingSecureMediaMessage extends OutgoingMediaMessage {

  public OutgoingSecureMediaMessage(Recipients recipients, String body,
                                    List<Attachment> attachments,
                                    long sentTimeMillis,
                                    int distributionType,
                                    long expiresIn)
  {
    super(recipients, body, attachments, sentTimeMillis, -1, expiresIn, distributionType);
  }

  public OutgoingSecureMediaMessage(OutgoingMediaMessage base) {
    super(base);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
