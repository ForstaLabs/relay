package io.forsta.ccsm.webrtc;

import io.forsta.securesms.recipients.Recipient;

public class CallRecipient {

  private Recipient recipient;
  private String callStatus;

  public CallRecipient(Recipient recipient) {
    this.recipient = recipient;
  }

  public CallRecipient(Recipient recipient, String message) {
    this.recipient = recipient;
    this.callStatus = message;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public void setCallStatus(String message) {
    this.callStatus = message;
  }

  public String getCallStatus() {
    return callStatus;
  }
}
