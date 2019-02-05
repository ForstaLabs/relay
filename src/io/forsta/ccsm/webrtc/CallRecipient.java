package io.forsta.ccsm.webrtc;

import io.forsta.securesms.recipients.Recipient;

public class CallRecipient {

  private Recipient recipient;
  private String callStatus;
  private boolean videoEnabled = false;
  private boolean audioEnabled = false;

  public CallRecipient(Recipient recipient) {
    this.recipient = recipient;
  }

  public CallRecipient(Recipient recipient, String message) {
    this.recipient = recipient;
    this.callStatus = message;
  }

  public void setVideoEnabled(boolean state) {
    videoEnabled = state;
  }

  public void setAudioEnabled(boolean state) {
    audioEnabled = state;
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  public boolean isAudioEnabled() {
    return audioEnabled;
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
