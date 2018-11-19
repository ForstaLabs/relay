package io.forsta.securesms.events;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;

import org.whispersystems.libsignal.IdentityKey;

public class WebRtcViewModel {

  public enum State {
    // Normal states
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_CONNECTED,
    CALL_RINGING,
    CALL_BUSY,
    CALL_DISCONNECTED,
    CALL_MEMBER_JOINING,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY,
  }


  private final @NonNull  State       state;
  private final @NonNull  Recipient   recipient;
  private final Recipients remoteRecipients;
  private final int callOrder;

  private final boolean remoteVideoEnabled;
  private final boolean localVideoEnabled;

  private final boolean isBluetoothAvailable;
  private final boolean isMicrophoneEnabled;

  public WebRtcViewModel(@NonNull State state,
                         Recipients remoteRecipients,
                         @NonNull Recipient recipient,
                         @NonNull int callOrder,
                         boolean localVideoEnabled, boolean remoteVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled)
  {
    this.state                = state;
    this.remoteRecipients = remoteRecipients;
    this.recipient            = recipient;
    this.callOrder = callOrder;
    this.localVideoEnabled    = localVideoEnabled;
    this.remoteVideoEnabled   = remoteVideoEnabled;
    this.isBluetoothAvailable = isBluetoothAvailable;
    this.isMicrophoneEnabled  = isMicrophoneEnabled;
  }

  public @NonNull State getState() {
    return state;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @NonNull int getCallOrder() {
    return callOrder;
  }

  public boolean isRemoteVideoEnabled() {
    return remoteVideoEnabled;
  }

  public boolean isLocalVideoEnabled() {
    return localVideoEnabled;
  }

  public boolean isBluetoothAvailable() {
    return isBluetoothAvailable;
  }

  public boolean isMicrophoneEnabled() {
    return isMicrophoneEnabled;
  }

  public String toString() {
    return "[State: " + state + ", recipient: " + recipient.getAddress() + ", callOrder: " + callOrder + ", remoteVideo: " + remoteVideoEnabled + ", localVideo: " + localVideoEnabled + "]";
  }
}
