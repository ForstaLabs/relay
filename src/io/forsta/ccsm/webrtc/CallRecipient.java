package io.forsta.ccsm.webrtc;

import io.forsta.securesms.events.WebRtcViewModel;
import io.forsta.securesms.recipients.Recipient;

public class CallRecipient {

  private Recipient recipient;
  private String callStatus;
  private int deviceId;
  private WebRtcViewModel.State callState = WebRtcViewModel.State.CALL_DISCONNECTED;
  private boolean videoEnabled = false;
  private boolean audioEnabled = true;

  public CallRecipient(Recipient recipient, WebRtcViewModel.State state, boolean videoEnabled, int deviceId) {
    this.recipient = recipient;
    this.callState = state;
    this.callStatus = callState.name();
    this.videoEnabled = videoEnabled;
    this.deviceId = deviceId;
  }

  public void setVideoEnabled(boolean state) {
    videoEnabled = state;
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  public void setCallState(WebRtcViewModel.State state) {
    callState = state;
  }

  public WebRtcViewModel.State getCallState() {
    return callState;
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

  public int getDeviceId() {
    return deviceId;
  }

  @Override
  public String toString() {
    return callState + " " + recipient + " deviceId: " + deviceId + " videoEnabled: " + videoEnabled;
  }
}
