package io.forsta.ccsm.webrtc;

import io.forsta.securesms.recipients.Recipient;

public class CallRecipient {

  private Recipient recipient;
  private String callStatus;

  public CallRecipient(Recipient recipient) {
    this.recipient = recipient;
  }

  public CallRecipient(Recipient recipient, String message) {
    this.callStatus = message;
  }

  public void setCallStatus(String message) {
    this.callStatus = message;
  }
}
