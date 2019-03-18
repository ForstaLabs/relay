package io.forsta.ccsm.messaging;

import java.util.ArrayList;
import java.util.List;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.OutgoingEndSessionMessage;

public class OutgoingEndSessionMediaMessage extends OutgoingMessage {

  public OutgoingEndSessionMediaMessage(Recipients recipients, String message, long sentTimeMillis) {
    super(recipients, message, new ArrayList<>(), sentTimeMillis, 0);
  }

  public OutgoingEndSessionMediaMessage(Recipients recipients, String body, List<Attachment> attachments, long sentTimeMillis, long expiresIn, String messageUid, String messageRef, int vote) {
    super(recipients, body, attachments, sentTimeMillis, expiresIn, messageUid, messageRef, vote);
  }

  @Override
  public boolean isEndSession() {
    return true;
  }
}
