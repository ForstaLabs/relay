package io.forsta.ccsm.messaging;

import java.util.List;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.recipients.Recipients;

public class OutgoingMessage extends OutgoingMediaMessage {
  public OutgoingMessage(Recipients recipients, String body, List<Attachment> attachments, long sentTimeMillis, long expiresIn) {
    super(recipients, body, attachments, sentTimeMillis, -1, expiresIn, 2);
  }

  public OutgoingMessage(Recipients recipients, String body, List<Attachment> attachments, long sentTimeMillis, long expiresIn, String messageUid, String messageRef, int vote) {
    super(recipients, body, attachments, sentTimeMillis, -1, expiresIn, 2, messageUid, messageRef, vote);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
