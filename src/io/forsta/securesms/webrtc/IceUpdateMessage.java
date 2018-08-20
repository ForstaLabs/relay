package io.forsta.securesms.webrtc;

public class IceUpdateMessage {

  private final String callId;
  private final String sdpMid;
  private final int sdpMLineIndex;
  private final String sdp;

  public IceUpdateMessage(String callId, String sdpMid, int sdpMLineIndex, String sdp) {

    this.callId = callId;
    this.sdpMid = sdpMid;
    this.sdpMLineIndex = sdpMLineIndex;
    this.sdp = sdp;
  }

  public String getSdpMid() {
    return sdpMid;
  }

  public int getSdpMLineIndex() {
    return sdpMLineIndex;
  }

  public String getSdp() {
    return sdp;
  }

  public String getCallId() {
    return callId;
  }
}
