package io.forsta.ccsm.messaging;

import java.util.List;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.recipients.Recipients;

public class OutgoingMessage extends OutgoingMediaMessage {
  public OutgoingMessage(Recipients recipients, String body, List<Attachment> attachments, long sentTimeMillis, long expiresIn) {
    super(recipients, body, attachments, sentTimeMillis, -1, expiresIn, 2);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
