package io.forsta.securesms.events;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArrayMap;

import io.forsta.ccsm.webrtc.CallRecipient;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.WebRtcCallService;

import org.whispersystems.libsignal.IdentityKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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
    CALL_MEMBER_LEAVING,
    CALL_MEMBER_VIDEO,
    VIDEO_ENABLE,
    CALL_ANSWERING,

    // Error states
    NETWORK_FAILURE,
    RECIPIENT_UNAVAILABLE,
    NO_SUCH_USER,
    UNTRUSTED_IDENTITY,
  }


  private final @NonNull  State       state;
  private final CallRecipient callRecipient;
  private final List<CallRecipient> callMembers;

  private int callOrder = 0; // Remove
  private final Map<Integer, CallRecipient> remoteCallRecipients; //Remove

  private final boolean localVideoEnabled;

  private final boolean isBluetoothAvailable;
  private final boolean isMicrophoneEnabled;

  public WebRtcViewModel(@NonNull State state,
                         Map<Integer, CallRecipient> remoteCallRecipients,
                         @NonNull CallRecipient callRecipient,
                         int callOrder,
                         boolean localVideoEnabled, boolean remoteVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled)
  {
    this.state                = state;
    this.remoteCallRecipients = remoteCallRecipients;
    this.callMembers = new LinkedList<>();
    this.callRecipient = callRecipient;
    this.callOrder = callOrder;
    this.localVideoEnabled    = localVideoEnabled;
    this.isBluetoothAvailable = isBluetoothAvailable;
    this.isMicrophoneEnabled  = isMicrophoneEnabled;
  }

  public WebRtcViewModel(@NonNull State state,
                         List<CallRecipient> remoteCallMembers,
                         CallRecipient callRecipient,
                         boolean localVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled)
  {
    this.state                = state;
    this.callMembers = remoteCallMembers;
    this.remoteCallRecipients = new ArrayMap<>();
    this.callRecipient = callRecipient;
    this.localVideoEnabled    = localVideoEnabled;
    this.isBluetoothAvailable = isBluetoothAvailable;
    this.isMicrophoneEnabled  = isMicrophoneEnabled;
  }

  public WebRtcViewModel(@NonNull State state, boolean localVideoEnabled,
                         boolean isBluetoothAvailable, boolean isMicrophoneEnabled) {
    this.state = state;
    this.callRecipient = null;
    this.callMembers = new ArrayList<>();
    this.remoteCallRecipients = new ArrayMap<>();
    this.localVideoEnabled    = localVideoEnabled;
    this.isBluetoothAvailable = isBluetoothAvailable;
    this.isMicrophoneEnabled  = isMicrophoneEnabled;
  }

  public @NonNull State getState() {
    return state;
  }

  public @NonNull Map<Integer, CallRecipient> getRemoteCallRecipients() {
    return remoteCallRecipients;
  }

  public @NonNull
  CallRecipient getCallRecipient() {
    return callRecipient;
  }

  public int getCallOrder() {
    return callOrder;
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
    return "[State: " + state + ", recipient: " + (callRecipient != null ? (callRecipient.getRecipient() + " videoEnabled: " + callRecipient.isVideoEnabled()) : "null") + " callOrder: " + callOrder + ", localVideo: " + localVideoEnabled + "]";
  }
}
