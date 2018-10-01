package io.forsta.securesms.mms;

import io.forsta.ccsm.messaging.OutgoingMessage;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.recipients.Recipients;

import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingMessage {

  public OutgoingExpirationUpdateMessage(Recipients recipients, long sentTimeMillis, long expiresIn) {
    super(recipients, "", new LinkedList<Attachment>(), sentTimeMillis, expiresIn);
  }

  public OutgoingExpirationUpdateMessage(Recipients recipients, String body, long sentTimeMillis, long expiresIn) {
    super(recipients, body, new LinkedList<Attachment>(), sentTimeMillis, expiresIn);
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

}
