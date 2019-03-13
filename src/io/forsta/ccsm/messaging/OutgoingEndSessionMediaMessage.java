package io.forsta.ccsm.messaging;

import java.util.ArrayList;
import java.util.List;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.recipients.Recipients;

public class OutgoingEndSessionMediaMessage extends OutgoingMessage {

  public OutgoingEndSessionMediaMessage(Recipients recipients, String message, long sentTimeMillis) {
    super(recipients, message, new ArrayList<>(), sentTimeMillis, 0);
  }

  @Override
  public boolean isEndSession() {
    return true;
  }
}
