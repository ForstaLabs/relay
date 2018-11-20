package io.forsta.securesms.events;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.WebRtcCallService;

import org.whispersystems.libsignal.IdentityKey;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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
  private final @NonNull WebRtcCallService.CallMember callMember;
  private final Collection<WebRtcCallService.CallMember> remoteCallMembers;

  private final boolean remoteVideoEnabled;
  private final boolean localVideoEnabled;

  private final boolean isBluetoothAvailable;
  private final boolean isMicrophoneEnabled;

  public WebRtcViewModel(@NonNull State state,
                         Collection<WebRtcCallService.CallMember> remoteCallMembers,
                         @NonNull WebRtcCallService.CallMember callMember,
                         boolean localVideoEnabled, boolean remoteVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled)
  {
    this.state                = state;
    this.remoteCallMembers = remoteCallMembers;
    this.callMember = callMember;
    this.localVideoEnabled    = localVideoEnabled;
    this.remoteVideoEnabled   = remoteVideoEnabled;
    this.isBluetoothAvailable = isBluetoothAvailable;
    this.isMicrophoneEnabled  = isMicrophoneEnabled;
  }

  public @NonNull State getState() {
    return state;
  }

  public Collection<WebRtcCallService.CallMember> getRemoteCallMembers() {
    return remoteCallMembers;
  }

  public @NonNull
  WebRtcCallService.CallMember getCallMember() {
    return callMember;
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
    return "[State: " + state + ", recipient: " + callMember + ", remoteVideo: " + remoteVideoEnabled + ", localVideo: " + localVideoEnabled + "]";
  }
}
