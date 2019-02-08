package io.forsta.securesms.service;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.messaging.IncomingMessage;
import io.forsta.ccsm.webrtc.CallRecipient;
import io.forsta.securesms.R;
import io.forsta.securesms.WebRtcCallActivity;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.events.WebRtcViewModel;
import io.forsta.securesms.notifications.AbstractNotificationBuilder;
import io.forsta.securesms.notifications.NotificationChannels;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.FutureTaskListener;
import io.forsta.securesms.util.ListenableFutureTask;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import io.forsta.securesms.util.concurrent.SettableFuture;
import io.forsta.securesms.webrtc.CallNotificationBuilder;
import io.forsta.securesms.webrtc.IncomingPstnCallReceiver;
import io.forsta.securesms.webrtc.PeerConnectionFactoryOptions;
import io.forsta.securesms.webrtc.PeerConnectionWrapper;
import io.forsta.securesms.webrtc.UncaughtExceptionHandlerManager;
import io.forsta.securesms.webrtc.WebRtcDataProtos;
import io.forsta.securesms.webrtc.audio.BluetoothStateManager;
import io.forsta.securesms.webrtc.audio.OutgoingRinger;
import io.forsta.securesms.webrtc.audio.SignalAudioManager;
import io.forsta.securesms.webrtc.locks.LockManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.forsta.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_RINGING;
import static io.forsta.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_CONNECTING;
import static io.forsta.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_MISSED;
import static io.forsta.securesms.webrtc.CallNotificationBuilder.TYPE_OUTGOING_RINGING;

public class WebRtcCallService extends Service implements InjectableType, BluetoothStateManager.BluetoothStateListener {

  private static final String TAG = WebRtcCallService.class.getSimpleName();

  private enum CallState {
    STATE_IDLE, STATE_ANSWERING, STATE_REMOTE_RINGING, STATE_LOCAL_RINGING, STATE_CONNECTED, STATE_REMOTE_JOINING
  }

  private static final String DATA_CHANNEL_NAME = "signaling";

  public static final String EXTRA_REMOTE_ADDRESS     = "remote_address";
  public static final String EXTRA_MUTE               = "mute_value";
  public static final String EXTRA_AVAILABLE          = "enabled_value";
  public static final String EXTRA_REMOTE_DESCRIPTION = "remote_description";
  public static final String EXTRA_TIMESTAMP          = "timestamp";
  public static final String EXTRA_CALL_ID            = "call_id";
  public static final String EXTRA_ICE_SDP            = "ice_sdp";
  public static final String EXTRA_ICE_SDP_MID        = "ice_sdp_mid";
  public static final String EXTRA_ICE_SDP_LINE_INDEX = "ice_sdp_line_index";
  public static final String EXTRA_RESULT_RECEIVER    = "result_receiver";
  public static final String EXTRA_THREAD_UID         = "thread_uid";
  public static final String EXTRA_PEER_ID            = "peer_id";
  public static final String EXTRA_CALL_MEMBERS       = "call_members";
  public static final String EXTRA_CALL_ORDER       = "call_order";

  public static final String ACTION_INCOMING_CALL        = "CALL_INCOMING";
  public static final String ACTION_OUTGOING_CALL        = "CALL_OUTGOING";
  public static final String ACTION_ANSWER_CALL          = "ANSWER_CALL";
  public static final String ACTION_DENY_CALL            = "DENY_CALL";
  public static final String ACTION_LOCAL_HANGUP         = "LOCAL_HANGUP";
  public static final String ACTION_SET_MUTE_AUDIO       = "SET_MUTE_AUDIO";
  public static final String ACTION_SET_MUTE_VIDEO       = "SET_MUTE_VIDEO";
  public static final String ACTION_BLUETOOTH_CHANGE     = "BLUETOOTH_CHANGE";
  public static final String ACTION_WIRED_HEADSET_CHANGE = "WIRED_HEADSET_CHANGE";
  public static final String ACTION_SCREEN_OFF           = "SCREEN_OFF";
  public static final String ACTION_CHECK_TIMEOUT        = "CHECK_TIMEOUT";
  public static final String ACTION_IS_IN_CALL_QUERY     = "IS_IN_CALL";

  public static final String ACTION_RESPONSE_MESSAGE  = "RESPONSE_MESSAGE";
  public static final String ACTION_ICE_MESSAGE       = "ICE_MESSAGE";
  public static final String ACTION_ICE_CANDIDATE     = "ICE_CANDIDATE";
  public static final String ACTION_CALL_CONNECTED    = "CALL_CONNECTED";
  public static final String ACTION_REMOTE_HANGUP     = "REMOTE_HANGUP";
  public static final String ACTION_REMOTE_BUSY       = "REMOTE_BUSY";
  public static final String ACTION_REMOTE_VIDEO_MUTE = "REMOTE_VIDEO_MUTE";
  public static final String ACTION_REMOTE_VIDEO_ENABLE = "REMOTE_VIDEO_MUTE";
  public static final String ACTION_ICE_CONNECTED     = "ICE_CONNECTED";
  private static final int MAX_PEERS = 4;

  private CallState callState          = CallState.STATE_IDLE;
  private boolean   microphoneEnabled  = true;
  private boolean   localVideoEnabled  = true;
  private boolean   bluetoothAvailable = false;

  private PeerConnectionFactory      peerConnectionFactory;
  private SignalAudioManager         audioManager;
  private BluetoothStateManager      bluetoothStateManager;
  private WiredHeadsetStateReceiver  wiredHeadsetStateReceiver;
  private PowerButtonReceiver        powerButtonReceiver;
  private LockManager                lockManager;

  private IncomingPstnCallReceiver        callReceiver;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;

  @Nullable private String callId;
  @Nullable private String threadUID;
  private CallMember localCallMember;

  private Map<String, CallMember> remoteCallMembers = new HashMap<>();
  private List<PeerConnection.IceServer> iceServiers;
  @NonNull  private AudioTrack localAudioTrack;
  @NonNull  private AudioSource localAudioSource;

  @Nullable private VideoCapturer localVideoCapturer;
  @Nullable private VideoSource localVideoSource;
  @Nullable private VideoTrack localVideoTrack;
  @Nullable private MediaStream localMediaStream;

  @Nullable public  static SurfaceViewRenderer localRenderer;
  @Nullable public  static SurfaceViewRenderer remoteRenderer;
  @Nullable private static EglBase             eglBase;

  private ExecutorService          serviceExecutor = Executors.newSingleThreadExecutor();
  private ExecutorService          networkExecutor = Executors.newSingleThreadExecutor();
  private ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

  @Override
  public void onCreate() {
    super.onCreate();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForeground(CallNotificationBuilder.WEBRTC_NOTIFICATION, new NotificationCompat.Builder(this, NotificationChannels.CALLS).build());
    }

    initializeResources();

    registerIncomingPstnCallReceiver();
    registerUncaughtExceptionHandler();
    registerWiredHeadsetStateReceiver();
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    Log.w(TAG, "onStartCommand...");
    if (intent == null || intent.getAction() == null) {
      return START_NOT_STICKY;
    }

    serviceExecutor.execute(new Runnable() {
      @Override
      public void run() {
//        if      (intent.getAction().equals(ACTION_INCOMING_CALL) && isBusy()) handleBusyCall(intent);
        if (intent.getAction().equals(ACTION_INCOMING_CALL))                  handleIncomingCall(intent); // X
        else if (intent.getAction().equals(ACTION_OUTGOING_CALL) && isIdle()) handleOutgoingCall(intent);
        else if (intent.getAction().equals(ACTION_ANSWER_CALL))               handleAnswerCall(intent); // X
        else if (intent.getAction().equals(ACTION_DENY_CALL))                 handleDenyCall(intent);
        else if (intent.getAction().equals(ACTION_LOCAL_HANGUP))              handleLocalHangup(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_HANGUP))             handleRemoteHangup(intent);
        else if (intent.getAction().equals(ACTION_SET_MUTE_AUDIO))            handleSetMuteAudio(intent);
        else if (intent.getAction().equals(ACTION_SET_MUTE_VIDEO))            handleSetMuteVideo(intent);
        else if (intent.getAction().equals(ACTION_BLUETOOTH_CHANGE))          handleBluetoothChange(intent);
        else if (intent.getAction().equals(ACTION_WIRED_HEADSET_CHANGE))      handleWiredHeadsetChange(intent);
        else if (intent.getAction().equals((ACTION_SCREEN_OFF)))              handleScreenOffChange(intent);
        else if (intent.getAction().equals(ACTION_RESPONSE_MESSAGE))          handleAcceptOffer(intent);
        else if (intent.getAction().equals(ACTION_ICE_MESSAGE))               handleIncomingIceCandidate(intent); // X
        else if (intent.getAction().equals(ACTION_ICE_CANDIDATE))             handleOutgoingIceCandidate(intent);
        else if (intent.getAction().equals(ACTION_ICE_CONNECTED))             handleIceConnected(intent);
        else if (intent.getAction().equals(ACTION_CALL_CONNECTED))            handleCallConnected(intent);
        else if (intent.getAction().equals(ACTION_CHECK_TIMEOUT))             handleCheckTimeout(intent);
        else if (intent.getAction().equals(ACTION_IS_IN_CALL_QUERY))          handleIsInCallQuery(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_VIDEO_ENABLE))       handleRemoveVideoEnable(intent);
      }
    });

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.w(TAG, "onDestroy...");
    super.onDestroy();

    if (callReceiver != null) {
      unregisterReceiver(callReceiver);
    }

    if (uncaughtExceptionHandlerManager != null) {
      uncaughtExceptionHandlerManager.unregister();
    }

    if (bluetoothStateManager != null) {
      bluetoothStateManager.onDestroy();
    }

    if (wiredHeadsetStateReceiver != null) {
      unregisterReceiver(wiredHeadsetStateReceiver);
      wiredHeadsetStateReceiver = null;
    }

    if (powerButtonReceiver != null) {
      unregisterReceiver(powerButtonReceiver);
      powerButtonReceiver = null;
    }
  }

  @Override
  public void onBluetoothStateChanged(boolean isAvailable) {
    Log.w(TAG, "onBluetoothStateChanged: " + isAvailable);

    Intent intent = new Intent(this, WebRtcCallService.class);
    intent.setAction(ACTION_BLUETOOTH_CHANGE);
    intent.putExtra(EXTRA_AVAILABLE, isAvailable);

    startService(intent);
  }

  // Initializers

  private void initializeResources() {
    this.callState             = CallState.STATE_IDLE;
    this.lockManager           = new LockManager(this);
    this.peerConnectionFactory = new PeerConnectionFactory(new PeerConnectionFactoryOptions());
    this.audioManager          = new SignalAudioManager(this);
    this.bluetoothStateManager = new BluetoothStateManager(this, this);
    this.localCallMember = new CallMember(this, TextSecurePreferences.getLocalNumber(this));
  }

  private void registerIncomingPstnCallReceiver() {
    callReceiver = new IncomingPstnCallReceiver();
    registerReceiver(callReceiver, new IntentFilter("android.intent.action.PHONE_STATE"));
  }

  private void registerUncaughtExceptionHandler() {
    uncaughtExceptionHandlerManager = new UncaughtExceptionHandlerManager();
    uncaughtExceptionHandlerManager.registerHandler(new ProximityLockRelease(lockManager));
  }

  private void registerWiredHeadsetStateReceiver() {
    wiredHeadsetStateReceiver = new WiredHeadsetStateReceiver();

    String action;

    if (Build.VERSION.SDK_INT >= 21) {
      action = AudioManager.ACTION_HEADSET_PLUG;
    } else {
      action = Intent.ACTION_HEADSET_PLUG;
    }

    registerReceiver(wiredHeadsetStateReceiver, new IntentFilter(action));
  }

  private void registerPowerButtonReceiver() {
    if (powerButtonReceiver == null) {
      powerButtonReceiver = new PowerButtonReceiver();

      registerReceiver(powerButtonReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
  }

  private void unregisterPowerButtonReceiver() {
    if (powerButtonReceiver != null) {
      unregisterReceiver(powerButtonReceiver);

      powerButtonReceiver = null;
    }
  }

  private void addCallMember(CallMember member, String incomingPeerId, String offer, List<PeerConnection.IceServer> iceServerUpdate) {
    try {
      member.createPeerConnection(iceServerUpdate, remoteRenderer, localMediaStream, incomingPeerId, member.callOrder);
      member.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, offer));

      try {
        if (callState == CallState.STATE_CONNECTED) {
          SessionDescription sdp = member.peerConnection.createAnswer(new MediaConstraints());
          member.peerConnection.setLocalDescription(sdp);
          ListenableFutureTask<Boolean> listenableFutureTask = sendAcceptOfferMessage(member.recipient, threadUID, callId, sdp, member.peerId);
          listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {

            @Override
            public void onFailureContinue(Throwable error) {
              Log.w(TAG, error);
              insertMissedCall(member.recipient, true);
              member.terminate();
              terminateCall(true);
            }
          });
          if (member.pendingIncomingIceUpdates != null) {
            for (IceCandidate candidate : member.pendingIncomingIceUpdates) {
              member.peerConnection.addIceCandidate(candidate);
            }
            member.pendingIncomingIceUpdates = null;
          }
        }
      } catch (PeerConnectionWrapper.PeerConnectionException e) {
        e.printStackTrace();
        member.terminate();
      }

    } catch (PeerConnectionWrapper.PeerConnectionException e) {
      Log.w(TAG, e);
      member.terminate();
    }
  }

  // Handlers
    private void handleIncomingCall(final Intent intent) {
    final String incomingCallId = intent.getStringExtra(EXTRA_CALL_ID);
    final String incomingAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    final String incomingPeerId = intent.getStringExtra(EXTRA_PEER_ID);
    final String[] members = intent.getStringArrayExtra(EXTRA_CALL_MEMBERS);
    final String offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION);
    final String incomingThreadId = intent.getStringExtra(EXTRA_THREAD_UID);

    Log.w(TAG, "handleIncomingCall callState: " + callState + " callId: " + incomingCallId + " address: " + incomingAddress);
    if (callId != null) {
      // Existing call. Member joining
      if (!callId.equals(incomingCallId)) {
        Log.w(TAG, "Missed call from new callId: " + incomingCallId);
        Recipient recipient = RecipientFactory.getRecipient(this, incomingAddress, false);
        insertMissedCall(recipient, true);
        return;
      }

      if (!remoteCallMembers.containsKey(incomingAddress)) {
        Log.w(TAG, "Remote address is not a call member: " + incomingAddress);
        return;
      }

      Log.w(TAG, "Adding new member to existing call");
      final CallMember member = remoteCallMembers.get(incomingAddress);

      if (isIncomingMessageExpired(intent)) {
        insertMissedCall(member.recipient, true);
        member.terminate();
        return;
      }

      timeoutExecutor.schedule(new TimeoutRunnable(member), 30, TimeUnit.SECONDS);

      if (iceServiers != null && iceServiers.size() > 0) {
        addCallMember(member, incomingPeerId, offer, iceServiers);
      } else {
        retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(callState, callId) {
          @Override
          public void onSuccessContinue(List<PeerConnection.IceServer> result) {
            addCallMember(member, incomingPeerId, offer, result);
          }
        });
      }

    } else {
      Log.w(TAG, "Accepting new call request from: " + incomingCallId);
      // New call.
      if (callState != CallState.STATE_IDLE) throw new IllegalStateException("Incoming on non-idle");
      threadUID = incomingThreadId;
      callId = incomingCallId;
      callState = CallState.STATE_ANSWERING;

      int memberCount = 0;
      for (String memberAddress : members) {
        if (!memberAddress.equals(localCallMember.getRecipient().getAddress())) {
          if (++memberCount > MAX_PEERS) break;
          remoteCallMembers.put(memberAddress, new CallMember(this, memberAddress));
        }
      }

      final CallMember incomingMember = remoteCallMembers.get(incomingAddress);
      if (incomingMember == null) {
        Log.w(TAG, "Unknown caller");
        terminateCall(false);
        return;
      }
      incomingMember.callOrder = 1;
      for (CallMember callMember : remoteCallMembers.values()) {
        if (!callMember.equals(incomingMember)) {
          callMember.callOrder = memberCount--;
        }
        Log.w(TAG, "CallMember: " + callMember);
      }

      if (isIncomingMessageExpired(intent)) {
        insertMissedCall(incomingMember.recipient, true);
        incomingMember.terminate();
        terminateCall(false);
        return;
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        setCallInProgressNotification(TYPE_INCOMING_CONNECTING, incomingMember.recipient);
      }

      for (CallMember callMember : remoteCallMembers.values()) {
        timeoutExecutor.schedule(new TimeoutRunnable(callMember), 30, TimeUnit.SECONDS);
      }

      initializeVideo();

      retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(callState, callId) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> result) {
          WebRtcCallService.this.iceServiers = result;
          try {
            incomingMember.createPeerConnection(result, remoteRenderer, localMediaStream, incomingPeerId, incomingMember.callOrder);
            incomingMember.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, offer));

            WebRtcCallService.this.lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
            WebRtcCallService.this.callState = CallState.STATE_LOCAL_RINGING;
            WebRtcCallService.this.lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

            sendMessage(WebRtcViewModel.State.CALL_INCOMING, remoteCallMembers.values(), incomingMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
            startCallCardActivity();
            audioManager.initializeAudioForCall();
            audioManager.startIncomingRinger();

            registerPowerButtonReceiver();

            setCallInProgressNotification(TYPE_INCOMING_RINGING, incomingMember.recipient);
          } catch (PeerConnectionWrapper.PeerConnectionException e) {
            Log.w(TAG, e);
            incomingMember.terminate();
            terminateCall(true);
          }
        }
      });
    }
  }

  private void handleOutgoingCall(Intent intent) {
    Log.w(TAG, "handleOutgoingCall callState: " + callState);

    if (callState != CallState.STATE_IDLE) throw new IllegalStateException("Dialing from non-idle?");

    try {
      this.callState = CallState.STATE_REMOTE_RINGING;
      this.audioManager.startOutgoingRinger(OutgoingRinger.Type.RINGING);
      this.threadUID = intent.getStringExtra(EXTRA_THREAD_UID);
      String[] members = intent.getStringArrayExtra(EXTRA_CALL_MEMBERS);
      this.callId = UUID.randomUUID().toString();
      final String localPeerId = UUID.randomUUID().toString();

      int memberCount = 0;
      for (String member : members) {
        if (!member.equals(localCallMember.getRecipient().getAddress())) {
          remoteCallMembers.put(member, new CallMember(this, member, ++memberCount));
        }
      }

      initializeVideo();

      for (CallMember callMember : remoteCallMembers.values()) {
        timeoutExecutor.schedule(new TimeoutRunnable(callMember), 30, TimeUnit.SECONDS);
      }

      lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
      audioManager.initializeAudioForCall();
      audioManager.startOutgoingRinger(OutgoingRinger.Type.SONAR);
      bluetoothStateManager.setWantsConnection(true);

      Recipients remoteRecipients = RecipientFactory.getRecipientsFromStrings(this, new ArrayList<>(remoteCallMembers.keySet()), false);

      setCallInProgressNotification(TYPE_OUTGOING_RINGING, remoteRecipients);

      retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.callState, this.callId) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> result) {
          sendMessage(WebRtcViewModel.State.CALL_OUTGOING, remoteCallMembers.values(), localVideoEnabled, bluetoothAvailable, microphoneEnabled);

          for (CallMember callMember : remoteCallMembers.values()) {
            try {
              callMember.createPeerConnection(result, remoteRenderer, localMediaStream, localPeerId, callMember.callOrder);
              SessionDescription sdp = callMember.peerConnection.createOffer(new MediaConstraints());
              callMember.peerConnection.setLocalDescription(sdp);

              Recipient remoteRecipient = RecipientFactory.getRecipient(WebRtcCallService.this, callMember.address, true);
              ListenableFutureTask<Boolean> listenableFutureTask = sendCallOfferMessage(remoteRecipient, remoteCallMembers.keySet(), threadUID, callId, sdp, localPeerId);
              listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
                @Override
                public void onFailureContinue(Throwable error) {
                  Log.w(TAG, error);
                  sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, callMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
                  callMember.terminate();
                }
              });
            } catch (PeerConnectionWrapper.PeerConnectionException e) {
              Log.w(TAG, e);
              callMember.terminate();
            }
          }
        }
      });
    } catch (Exception e) {
      Log.e(TAG, "Exception: " + e.getMessage());
    }
  }

  private void handleAcceptOffer(Intent intent) {

    CallMember member = getCallMember(intent);
    Log.w(TAG, "handleAcceptOffer callState: " + callState + " callId: " + getCallId(intent) + " member: " + member);
    try {
      if (member == null) {
        Log.w(TAG, "Got answer for unknown call member");
        return;
      }

      String remoteCallId = getCallId(intent);
      if (callId == null ||!callId.equals(remoteCallId)) {
        Log.w(TAG, "Got answer for remote call id we're not currently dialing: " + remoteCallId + " != " + callId);
        return;
      }

      if (member.peerConnection == null) {
        Log.w(TAG, "No peer connection for this call accept offer");
        return;
      }

      if (member.pendingOutgoingIceUpdates != null && !member.pendingOutgoingIceUpdates.isEmpty()) {
        Log.w(TAG, "handleAcceptOffer pendingOutgoingIceUpdates sendIceUpdateMessage");
        ListenableFutureTask<Boolean> listenableFutureTask = sendIceUpdateMessage(member.recipient, threadUID, callId, member.peerId, member.pendingOutgoingIceUpdates);
        listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
          @Override
          public void onFailureContinue(Throwable error) {
            Log.w(TAG, error);
            sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);

            member.terminate();
            terminateCall(true);
          }
        });
      }

      member.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION)));
      member.pendingOutgoingIceUpdates = null;
      if (callState != CallState.STATE_CONNECTED) {
        handleCallConnected(intent);
//        sendMessage(WebRtcViewModel.State.CALL_RINGING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      }
//      if (callState == CallState.STATE_CONNECTED) {
//        sendMessage(WebRtcViewModel.State.CALL_MEMBER_JOINING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
//      } else {
//        sendMessage(WebRtcViewModel.State.CALL_RINGING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
//      }
    } catch (PeerConnectionWrapper.PeerConnectionException e) {
      Log.w(TAG, e);
      member.terminate();
    }
  }

  private void handleIncomingIceCandidate(Intent intent) {
    Log.w(TAG, "handleIncomingIceCandidate...");
    CallMember connection = getCallMember(intent);

    if (connection != null && callId != null && callId.equals(getCallId(intent))) {
      IceCandidate candidate = new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
          intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
          intent.getStringExtra(EXTRA_ICE_SDP));

      connection.addIncomingIceCandidate(candidate);
    } else {
      Log.w(TAG, "No connection, or invalid callId");
    }
  }

  private void handleOutgoingIceCandidate(Intent intent) {
    Log.w(TAG, "handleOutgoingIceCandidate...");
    CallMember remoteMember = getCallMember(intent);

    if (callState == CallState.STATE_IDLE || callId == null || !callId.equals(getCallId(intent))) {
      Log.w(TAG, "State is now idle, ignoring ice candidate...");
      return;
    }

    if (remoteMember == null || remoteMember.recipient == null || callId == null) {
      Log.w(TAG, "No caller for this Ice Candidate");
//      throw new AssertionError("assert: " + callState + ", " + callId);
      return;
    }

    IceCandidate iceUpdateMessage = new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
        intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
        intent.getStringExtra(EXTRA_ICE_SDP));
    List<IceCandidate> candidates = new LinkedList<>();
    if (remoteMember.pendingOutgoingIceUpdates !=null) {
      remoteMember.addOutgoingIceCandidate(iceUpdateMessage);
      return;
    } else {
      candidates.add(iceUpdateMessage);
    }

    Log.w(TAG, "handleOutgoingIceCandidate sendIceUpdateMessage: " + iceUpdateMessage.toString());

    ListenableFutureTask<Boolean> listenableFutureTask = sendIceUpdateMessage(remoteMember.recipient, threadUID, callId, remoteMember.peerId, candidates);

    listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
      @Override
      public void onFailureContinue(Throwable error) {
        Log.w(TAG, error);
        sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, remoteMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);

        remoteMember.terminate();
        terminateCall(true);
      }
    });
  }

  private void handleIceConnected(Intent intent) {
    Log.w(TAG, "handleIceConnected callState: " + callState);

    CallMember member = getCallMember(intent);
    if (member.recipient == null) {
      Log.w(TAG, "No recipient for this call member");
      return;
    }

    if (callState == CallState.STATE_ANSWERING) {
      Log.w(TAG, "handleIceConnected answering...");
    } else if (callState == CallState.STATE_REMOTE_RINGING) {
      Log.w(TAG, "handleIceConnected remote ringing...");
    }

    if (callState == CallState.STATE_CONNECTED) {
      sendMessage(WebRtcViewModel.State.CALL_MEMBER_JOINING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleCallConnected(Intent intent) {
    Log.w(TAG, "handleCallConnected callState: " + callState);
    CallMember member = getCallMember(intent);

    String id = getCallId(intent);
    if (callId != null && !callId.equals(id)) {
      Log.w(TAG, "Ignoring connected for unknown call id: " + id);
      return;
    }

    if (member == null || member.recipient == null || member.peerConnection == null) {
      Log.w(TAG, "No call information for this caller");
      return;
    }

    if (callState != CallState.STATE_REMOTE_RINGING && callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Ignoring call connected for unknown state: " + callState);
      return;
    }

    audioManager.startCommunication(callState == CallState.STATE_REMOTE_RINGING);
    bluetoothStateManager.setWantsConnection(true);

    callState = CallState.STATE_CONNECTED;

    if (localVideoEnabled) lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
    else                   lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);

    sendMessage(WebRtcViewModel.State.CALL_CONNECTED, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);

    unregisterPowerButtonReceiver();

    setCallInProgressNotification(CallNotificationBuilder.TYPE_ESTABLISHED, member.recipient);

    setLocalVideoEnabled(localVideoEnabled);
    setLocalAudioEnabled(microphoneEnabled);
  }

  private void handleCheckTimeout(Intent intent) {
    Log.w(TAG, "handleCheckTimeout state: " + callState);

    CallMember member = getCallMember(intent);
    if (member != null && callId != null && callId.equals(intent.getStringExtra(EXTRA_CALL_ID)) && callState != CallState.STATE_CONNECTED) {
      Log.w(TAG, "Timing out call member: " + member + " CallId: " + callId);

      if (callState == CallState.STATE_ANSWERING || callState == CallState.STATE_LOCAL_RINGING) {
        if (member.callOrder == 1) {
          sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
          insertMissedCall(member.recipient, true);
          terminateCall(true);
        } else {
          sendMessage(WebRtcViewModel.State.CALL_MEMBER_LEAVING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
        }
      }

      if (callState == CallState.STATE_REMOTE_RINGING) {
        terminateCall(true);
      }

      member.terminate();
    }
  }

  private void handleIsInCallQuery(Intent intent) {
    ResultReceiver resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);

    if (resultReceiver != null) {
      resultReceiver.send(callState != CallState.STATE_IDLE ? 1 : 0, null);
    }
  }

  private void insertMissedCall(@NonNull Recipient recipient, boolean signal) {
    Log.w(TAG, "Missed call from: " + recipient.getAddress());
    insertStatusMessage(threadUID, getString(R.string.CallService_missed_call));
    setCallInProgressNotification(TYPE_INCOMING_MISSED, recipient);
  }

  private void handleAnswerCall(Intent intent) {
    Log.w(TAG, "handleAnswerCall callState: " + callState);
    if (callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Can only answer from ringing!");
      return;
    }

    CallMember originator = null;
    for (CallMember callMember : remoteCallMembers.values()) {
      if (callMember.peerId != null && callMember.peerConnection != null) {
        SessionDescription remoteDesc = callMember.peerConnection.getRemoteDescription();
        if (remoteDesc != null) {
          Log.w(TAG, "Have remote description for " + callMember);
          if (callMember.callOrder == 1) {
            originator = callMember;
          }
          try {
            SessionDescription sdp = callMember.peerConnection.createAnswer(new MediaConstraints());
            callMember.peerConnection.setLocalDescription(sdp);
            ListenableFutureTask<Boolean> listenableFutureTask = sendAcceptOfferMessage(callMember.recipient, threadUID, callId, sdp, callMember.peerId);
            listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
              @Override
              public void onFailureContinue(Throwable error) {
                Log.w(TAG, error);
                insertMissedCall(callMember.recipient, true);
                callMember.terminate();
              }
            });

            for (IceCandidate candidate : callMember.pendingIncomingIceUpdates) {
              callMember.peerConnection.addIceCandidate(candidate);
            }
            callMember.pendingIncomingIceUpdates = null;
          } catch (PeerConnectionWrapper.PeerConnectionException e) {
            e.printStackTrace();
          }
        }
      }
    }

    setLocalVideoEnabled(true);
    setLocalAudioEnabled(true);

    sendRemoteCallOffers();

    intent.putExtra(EXTRA_CALL_ID, callId);
    intent.putExtra(EXTRA_REMOTE_ADDRESS, originator.address);
    handleCallConnected(intent);
  }

  private void handleDenyCall(Intent intent) {
    if (callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Can only deny from ringing!");
      return;
    }

    for (CallMember member : remoteCallMembers.values()) {
      if (member.recipient == null || callId == null) {
        Log.w(TAG, "No call information or recipient information for this call");
        return;
//        throw new AssertionError("assert");
      }
      if (member.isActiveConnection()) {
        sendCallLeaveMessage(member.recipient, threadUID, callId);
        insertMissedCall(member.recipient, true);
        member.terminate();
      }
    }
    insertStatusMessage(threadUID, getString(R.string.CallService_declined_call));
    terminateCall(true);
  }

  private void handleLocalHangup(Intent intent) {
    Log.w(TAG, "handleLocalHangup");

    for (CallMember remoteCallMember : remoteCallMembers.values()) {
      if (remoteCallMember.recipient != null && remoteCallMember.isActiveConnection()) {
        sendCallLeaveMessage(remoteCallMember.recipient, threadUID, callId);
        sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, remoteCallMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
        remoteCallMember.terminate();
      }
    }
    insertStatusMessage(threadUID, getString(R.string.CallService_in_call));
    terminateCall(true);
  }

  private void handleRemoteHangup(Intent intent) {
    Log.w(TAG, "handleRemoteHangup");
    if (callId == null || (callId != null && !callId.equals(getCallId(intent)))) {
      Log.w(TAG, "hangup for non-active call...");
      return;
    }

    CallMember member = getCallMember(intent);
    if (member == null || member.recipient == null) {
      Log.w(TAG, "Received hangup from invalid call member");
    }

    if (member.videoEnabled) {
      for (CallMember otherMember : remoteCallMembers.values()) {
        if (otherMember.isActiveConnection() && !otherMember.videoEnabled) {
          otherMember.setVideoEnabled();
          break;
        }
      }
    }

    member.terminate();
    if (!hasActiveCalls()) {
      if (callState == CallState.STATE_REMOTE_RINGING) {
        sendMessage(WebRtcViewModel.State.RECIPIENT_UNAVAILABLE, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      } else {
        sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      }

      if (callState == CallState.STATE_ANSWERING || callState == CallState.STATE_LOCAL_RINGING) {
        insertMissedCall(member.recipient, true);
      }

      terminateCall(callState == CallState.STATE_REMOTE_RINGING || callState == CallState.STATE_CONNECTED);
    } else {
      sendMessage(WebRtcViewModel.State.CALL_MEMBER_LEAVING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleSetMuteAudio(Intent intent) {
    boolean muted = intent.getBooleanExtra(EXTRA_MUTE, false);
    this.microphoneEnabled = !muted;

    setLocalAudioEnabled(microphoneEnabled);
  }

  private void handleSetMuteVideo(Intent intent) {
    AudioManager audioManager = ServiceUtil.getAudioManager(this);
    boolean      muted        = intent.getBooleanExtra(EXTRA_MUTE, false);

    this.localVideoEnabled = !muted;
    setLocalVideoEnabled(localVideoEnabled);

    if (callState == CallState.STATE_CONNECTED) {
      if (localVideoEnabled) this.lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
      else                   this.lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
    }

    if (localVideoEnabled &&
        !audioManager.isSpeakerphoneOn() &&
        !audioManager.isBluetoothScoOn() &&
        !audioManager.isWiredHeadsetOn())
    {
      audioManager.setSpeakerphoneOn(true);
    }

    sendMessage(WebRtcViewModel.State.VIDEO_ENABLE, localCallMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  private void handleBluetoothChange(Intent intent) {
    this.bluetoothAvailable = intent.getBooleanExtra(EXTRA_AVAILABLE, false);

//    if (localCallMember != null && localCallMember.recipient != null) {
//      sendMessage(viewModelStateFor(callState), localCallMember.recipient, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
//    }
  }

  private void handleWiredHeadsetChange(Intent intent) {
    Log.w(TAG, "handleWiredHeadsetChange...");

    if (callState == CallState.STATE_CONNECTED ||
        callState == CallState.STATE_REMOTE_RINGING)
    {
      AudioManager audioManager = ServiceUtil.getAudioManager(this);
      boolean      present      = intent.getBooleanExtra(EXTRA_AVAILABLE, false);

      if (present && audioManager.isSpeakerphoneOn()) {
        audioManager.setSpeakerphoneOn(false);
        audioManager.setBluetoothScoOn(false);
      } else if (!present && !audioManager.isSpeakerphoneOn() && !audioManager.isBluetoothScoOn() && localVideoEnabled) {
        audioManager.setSpeakerphoneOn(true);
      }

//      if (localCallMember != null && localCallMember.recipient != null) {
//        sendMessage(viewModelStateFor(callState), localCallMember.recipient, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
//      }
    }
  }

  private void handleScreenOffChange(Intent intent) {
    if (callState == CallState.STATE_ANSWERING ||
        callState == CallState.STATE_LOCAL_RINGING)
    {
      Log.w(TAG, "Silencing incoming ringer...");
      audioManager.silenceIncomingRinger();
    }
  }

  private void handleRemoveVideoEnable(Intent intent) {
    CallMember member = getCallMember(intent);
    if (!member.videoEnabled) {
      for (CallMember callMember : remoteCallMembers.values()) {
        if (member.address.equals(callMember.address)) {
          callMember.setVideoEnabled();
        } else {
          callMember.disableVideo();
        }
      }
    }
  }

  /// Helper Methods

  private boolean hasActiveCalls() {
    for (CallMember callMember : remoteCallMembers.values()) {
      if (callMember.isActiveConnection()) {
        return true;
      }
    }
    return false;
  }

  private CallMember getCallMember(Intent intent) {
    String address = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    if (address != null) {
      CallMember member = remoteCallMembers.get(address);
      if (member != null) {
        Log.w(TAG, "Getting call member:" + member.toString());
      } else {
        Log.w(TAG, "Received intent from invalid call member");
      }
      return member;
    }
    return null;
  }

  private void sendRemoteCallOffers() {
    // Now setup peer connections and send call offers to all other(2) peers?
    final String localPeerId = UUID.randomUUID().toString();
    for (CallMember callMember : remoteCallMembers.values()) {
      if (callMember.peerConnection == null) {
        try {
          callMember.createPeerConnection(iceServiers, remoteRenderer, localMediaStream, localPeerId, callMember.callOrder);
          SessionDescription sdp = callMember.peerConnection.createOffer(new MediaConstraints());
          callMember.peerConnection.setLocalDescription(sdp);

          Recipient remoteRecipient = RecipientFactory.getRecipient(WebRtcCallService.this, callMember.address, true);
          Log.w(TAG, "Sending call offer to: " + remoteRecipient);
          ListenableFutureTask<Boolean> listenableFutureTask = sendCallOfferMessage(remoteRecipient, remoteCallMembers.keySet(), threadUID, callId, sdp, localPeerId);
          listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
            @Override
            public void onFailureContinue(Throwable error) {
              Log.w(TAG, error);
              sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, callMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
              callMember.terminate();
            }
          });
        } catch (PeerConnectionWrapper.PeerConnectionException e) {
          Log.e(TAG, "Error creating peer connection for " + callMember);
        }
      }
    }
  }

  private boolean isBusy() {
    TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);

    return callState != CallState.STATE_IDLE || telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
  }

  private boolean isIdle() {
    return callState == CallState.STATE_IDLE;
  }

  private boolean isIncomingMessageExpired(Intent intent) {
    return System.currentTimeMillis() - intent.getLongExtra(WebRtcCallService.EXTRA_TIMESTAMP, -1) > TimeUnit.MINUTES.toMillis(2);
  }

  private void initializeVideo() {
    Util.runOnMainSync(new Runnable() {
      @Override
      public void run() {
        eglBase        = EglBase.create();
        localRenderer  = new SurfaceViewRenderer(WebRtcCallService.this);
        remoteRenderer = new SurfaceViewRenderer(WebRtcCallService.this);

        localRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.init(eglBase.getEglBaseContext(), null);

        peerConnectionFactory.setVideoHwAccelerationOptions(eglBase.getEglBaseContext(),
                                                            eglBase.getEglBaseContext());
        initializeLocalVideo();
      }
    });
  }

  private void initializeLocalVideo() {
    MediaConstraints                constraints      = new MediaConstraints();
    MediaConstraints                audioConstraints = new MediaConstraints();

    constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    audioConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

    localVideoCapturer = createVideoCapturer(this);

    localMediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS");
    localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
    localAudioTrack  = peerConnectionFactory.createAudioTrack("ARDAMSa0", localAudioSource);
    localAudioTrack.setEnabled(false);
    localMediaStream.addTrack(localAudioTrack);

    if (localVideoCapturer != null) {
      localVideoSource = peerConnectionFactory.createVideoSource(localVideoCapturer);
      localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", localVideoSource);

      localVideoTrack.addRenderer(new VideoRenderer(localRenderer));
      localVideoTrack.setEnabled(true);
      localMediaStream.addTrack(localVideoTrack);
    } else {
      localVideoSource = null;
      localVideoTrack  = null;
    }
  }

  private void setCallInProgressNotification(int type, Recipients recipients) {
    startForeground(CallNotificationBuilder.WEBRTC_NOTIFICATION,
                    CallNotificationBuilder.getCallInProgressNotification(this, type, recipients));
  }

  private void setCallInProgressNotification(int type, Recipient recipient) {
    Recipients recipients = RecipientFactory.getRecipientsFor(this, recipient, false);
    startForeground(CallNotificationBuilder.WEBRTC_NOTIFICATION,
        CallNotificationBuilder.getCallInProgressNotification(this, type, recipients));
  }

  private synchronized void terminateCall(boolean removeNotification) {
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    stopForeground(removeNotification);

    audioManager.stop(callState == CallState.STATE_REMOTE_RINGING || callState == CallState.STATE_CONNECTED);
    bluetoothStateManager.setWantsConnection(false);

    for (CallMember member : remoteCallMembers.values()) {
      if (member.peerConnection != null) {
        member.terminate();
      }
    }

    if (eglBase != null && localRenderer != null && remoteRenderer != null) {
      localRenderer.release();
      remoteRenderer.release();
      eglBase.release();

      localRenderer = null;
      remoteRenderer = null;
      eglBase = null;
    }

    if (localVideoCapturer != null) {
      try {
        localVideoCapturer.stopCapture();
      } catch (InterruptedException e) {
        Log.w(TAG, e);
      }
      localVideoCapturer.dispose();
      localVideoCapturer = null;
    }

    if (localVideoSource != null) {
      localVideoSource.dispose();
      localVideoSource = null;
    }

    if (localAudioSource != null) {
      localAudioSource.dispose();
      localAudioSource = null;
    }

    this.callState = CallState.STATE_IDLE;
    remoteCallMembers.clear();
    this.callId = null;
    this.threadUID = null;
    this.microphoneEnabled = true;
    this.localVideoEnabled = true;
    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull CallMember callMember,
                           boolean localVideoEnabled,
                           boolean bluetoothAvailable, boolean microphoneEnabled) {
    sendMessage(state, null, callMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           Collection<CallMember> remoteCallMembers,
                           boolean localVideoEnabled,
                           boolean bluetoothAvailable, boolean microphoneEnabled) {
    sendMessage(state, remoteCallMembers, null, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           Collection<CallMember> remoteCallMembers,
                           @NonNull CallMember callMember,
                           boolean localVideoEnabled,
                           boolean bluetoothAvailable, boolean microphoneEnabled)
  {
    Map<Integer, CallRecipient> remoteCallRecipients = new HashMap<>();
    if (remoteCallMembers != null) {
      for (CallMember member : remoteCallMembers) {
        remoteCallRecipients.put(member.callOrder, new CallRecipient(member.recipient, state, member.videoEnabled));
        // Outgoing call with no specific callMember, get the first one.
        if (callMember == null) {
          callMember = member;
        }
      }
    } else {
      remoteCallRecipients.put(callMember.callOrder, new CallRecipient(callMember.getRecipient(), state, callMember.videoEnabled));
    }

    EventBus.getDefault().postSticky(new WebRtcViewModel(state, remoteCallRecipients, remoteCallRecipients.get(callMember.callOrder), callMember.callOrder, localVideoEnabled, callMember.videoEnabled, bluetoothAvailable, microphoneEnabled));
  }

  private ListenableFutureTask<Boolean> sendIceUpdateMessage(@NonNull final Recipient recipient, String threadUID,
                                                    @NonNull final String callId, @NonNull final String peerId, List<IceCandidate> updates)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());
        Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
        Set<String> members = getCallMembers();
        MessageSender.sendIceUpdate(getApplicationContext(), masterSecret, recipients, threadUID, callId, peerId, updates, members);
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private ListenableFutureTask<Boolean> sendAcceptOfferMessage(@NonNull final Recipient recipient, String threadUID,
                                                               @NonNull final String callId, SessionDescription sdp, String peerId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());
        Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
        Set<String> members = getCallMembers();
        MessageSender.sendCallAcceptOffer(getApplicationContext(), masterSecret, recipients, threadUID, callId, sdp, peerId, members);
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private ListenableFutureTask<Boolean> sendCallOfferMessage(@NonNull final Recipient recipient, Set<String> memberAddresses, String threadUID,
                                                               @NonNull final String callId, SessionDescription sdp, String peerId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());
        Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
        MessageSender.sendCallOffer(getApplicationContext(), masterSecret, recipients, new ArrayList(memberAddresses), threadUID, callId, sdp, peerId);
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private ListenableFutureTask<Boolean> sendCallLeaveMessage(@NonNull final Recipient recipient, String threadUID,
                                                    @NonNull final String callId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());
        Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
        MessageSender.sendCallLeave(getApplicationContext(), masterSecret, recipients, threadUID, callId);
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private ListenableFutureTask<Boolean> insertStatusMessage(@NonNull final String thread, @NonNull final String message)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Context context = getApplicationContext();
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());
        Recipients recipients = RecipientFactory.getRecipientsFor(context, localCallMember.recipient, false);
        long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdForUid(thread);
        if (threadId != -1) {
          IncomingMessage infoMessage = ForstaMessageManager.createLocalInformationalMessage(context, message, recipients, threadId, 0);
          DatabaseFactory.getMmsDatabase(context).insertSecureDecryptedMessageInbox(new MasterSecretUnion(masterSecret), infoMessage, threadId);
        }
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private void startCallCardActivity() {
    Intent activityIntent = new Intent();
    activityIntent.setClass(this, WebRtcCallActivity.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    this.startActivity(activityIntent);
  }

  private Set<String> getCallMembers() {
    HashSet<String> members = new HashSet<>(remoteCallMembers.keySet());
    members.add(localCallMember.getRecipient().getAddress());
    return members;
  }

  private String getCallId(Intent intent) {
    return intent.getStringExtra(EXTRA_CALL_ID);
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private ListenableFutureTask<List<PeerConnection.IceServer>> retrieveTurnServers() {
    Callable<List<PeerConnection.IceServer>> callable = new Callable<List<PeerConnection.IceServer>>() {
      @Override
      public List<PeerConnection.IceServer> call() {
        LinkedList<PeerConnection.IceServer> results = new LinkedList<>();

        try {
          JSONObject jsonResults = CcsmApi.getRtcServers(getApplicationContext());
          if (jsonResults.has("results")) {
            JSONArray servers =  jsonResults.getJSONArray("results");
            for (int i=0; i<servers.length(); i++) {
              JSONObject server = servers.getJSONObject(i);
              if (server.has("urls")) {
                JSONArray urls = server.getJSONArray("urls");
                String url = urls.getString(0);
                String username = server.optString("username");
                String credential = server.optString("credential");
                if (url.startsWith("turn")) {
                  results.add(new PeerConnection.IceServer(url, username, credential));
                }
                else {
                  results.add(new PeerConnection.IceServer(url));
                }
              }
            }
          } else {
            Log.w(TAG, "Error, No results for TURN Servers");
          }
        } catch (Exception e) {
          Log.w(TAG, "Error fetching RTC servers from Atlas: " + e.getMessage());
        }

        return results;
      }
    };

    ListenableFutureTask<List<PeerConnection.IceServer>> futureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(futureTask);

    return futureTask;
  }

  private static class WiredHeadsetStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      int state = intent.getIntExtra("state", -1);

      Intent serviceIntent = new Intent(context, WebRtcCallService.class);
      serviceIntent.setAction(WebRtcCallService.ACTION_WIRED_HEADSET_CHANGE);
      serviceIntent.putExtra(WebRtcCallService.EXTRA_AVAILABLE, state != 0);
      context.startService(serviceIntent);
    }
  }

  private static class PowerButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
        Intent serviceIntent = new Intent(context, WebRtcCallService.class);
        serviceIntent.setAction(WebRtcCallService.ACTION_SCREEN_OFF);
        context.startService(serviceIntent);
      }
    }
  }

  private class TimeoutRunnable implements Runnable {

    private final CallMember callMember;

    private TimeoutRunnable(CallMember callMember) {
      this.callMember = callMember;
    }

    public void run() {
      Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_CHECK_TIMEOUT);
      intent.putExtra(EXTRA_CALL_ID, callId);
      intent.putExtra(EXTRA_REMOTE_ADDRESS, callMember.address);
      intent.putExtra(EXTRA_PEER_ID, callMember.peerId);
      startService(intent);
    }
  }

  private static class ProximityLockRelease implements Thread.UncaughtExceptionHandler {
    private final LockManager lockManager;

    private ProximityLockRelease(LockManager lockManager) {
      this.lockManager = lockManager;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
      Log.d(TAG, "Uncaught exception - releasing proximity lock", throwable);
      lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
    }
  }

  private abstract class StateAwareListener<V> implements FutureTaskListener<V> {

    private final CallState expectedState;
    private final String      expectedCallId;

    StateAwareListener(CallState expectedState, String expectedCallId) {
      this.expectedState  = expectedState;
      this.expectedCallId = expectedCallId;
    }


    @Override
    public void onSuccess(V result) {
      if (!isConsistentState()) {
        Log.w(TAG, "State has changed since request, aborting success callback...");
      } else {
        onSuccessContinue(result);
      }
    }

    @Override
    public void onFailure(ExecutionException throwable) {
      if (!isConsistentState()) {
        Log.w(TAG, throwable);
        Log.w(TAG, "State has changed since request, aborting failure callback...");
      } else {
        onFailureContinue(throwable.getCause());
      }
    }

    private boolean isConsistentState() {
      return this.expectedState == callState && callId.equals(this.expectedCallId);
    }

    public abstract void onSuccessContinue(V result);
    public abstract void onFailureContinue(Throwable throwable);
  }

  private abstract class FailureListener<V> extends StateAwareListener<V> {
    FailureListener(CallState expectedState, String expectedCallId) {
      super(expectedState, expectedCallId);
    }

    @Override
    public void onSuccessContinue(V result) {}
  }

  private abstract class SuccessOnlyListener<V> extends StateAwareListener<V> {
    SuccessOnlyListener(CallState expectedState, String expectedCallId) {
      super(expectedState, expectedCallId);
    }

    @Override
    public void onFailureContinue(Throwable throwable) {
      Log.w(TAG, throwable);
      throw new AssertionError(throwable);
    }
  }

  @WorkerThread
  public static boolean isCallActive(Context context) {
    Log.w(TAG, "isCallActive()");

    HandlerThread handlerThread = null;

    try {
      handlerThread = new HandlerThread("webrtc-callback");
      handlerThread.start();

      final SettableFuture<Boolean> future = new SettableFuture<>();

      ResultReceiver resultReceiver = new ResultReceiver(new Handler(handlerThread.getLooper())) {
        protected void onReceiveResult(int resultCode, Bundle resultData) {
          Log.w(TAG, "Got result...");
          future.set(resultCode == 1);
        }
      };

      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(ACTION_IS_IN_CALL_QUERY);
      intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);

      context.startService(intent);

      Log.w(TAG, "Blocking on result...");
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      Log.w(TAG, e);
      return false;
    } finally {
      if (handlerThread != null) handlerThread.quit();
    }
  }

  public static void isCallActive(Context context, ResultReceiver resultReceiver) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_IS_IN_CALL_QUERY);
    intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);

    context.startService(intent);
  }

  private @Nullable
  CameraVideoCapturer createVideoCapturer(@NonNull Context context) {
    boolean camera2EnumeratorIsSupported = false;
    try {
      camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(context);
    } catch (final Throwable throwable) {
      Log.w(TAG, "Camera2Enumator.isSupport() threw.", throwable);
    }

    Log.w(TAG, "Camera2 enumerator supported: " + camera2EnumeratorIsSupported);
    CameraEnumerator enumerator;

    if (camera2EnumeratorIsSupported) enumerator = new Camera2Enumerator(context);
    else                              enumerator = new Camera1Enumerator(true);

    String[] deviceNames = enumerator.getDeviceNames();

    for (String deviceName : deviceNames) {
      if (enumerator.isFrontFacing(deviceName)) {
        Log.w(TAG, "Creating front facing camera capturer.");
        final CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          Log.w(TAG, "Found front facing capturer: " + deviceName);

          return videoCapturer;
        }
      }
    }

    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Log.w(TAG, "Creating other camera capturer.");
        final CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          Log.w(TAG, "Found other facing capturer: " + deviceName);
          return videoCapturer;
        }
      }
    }

    Log.w(TAG, "Video capture not supported!");
    return null;
  }

  public void setLocalVideoEnabled(boolean enabled) {
    if (localVideoTrack != null) {
      localVideoTrack.setEnabled(enabled);
    }

    if (localVideoCapturer != null) {
      try {
        if (enabled) localVideoCapturer.startCapture(1280, 720, 30);
        else         localVideoCapturer.stopCapture();
      } catch (InterruptedException e) {
        Log.w(TAG, e);
      }
    }
  }

  public void setLocalAudioEnabled(boolean enabled) {
    localAudioTrack.setEnabled(enabled);
  }

  public class CallMember implements PeerConnection.Observer {
    private volatile Context context;
    private String peerId;
    private String address;
    private Recipient recipient;
    private int callOrder = 0;
    @Nullable private PeerConnectionWrapper peerConnection;
    @Nullable private VideoTrack videoTrack;
    @Nullable private VideoRenderer videoRenderer;
    @Nullable private List<IceCandidate> pendingOutgoingIceUpdates;
    @Nullable private List<IceCandidate> pendingIncomingIceUpdates;
    @NonNull VideoRenderer.Callbacks renderer;
    private boolean videoEnabled = false;
    private boolean audioEnabled = true;

    private CallMember(Context context, String address) {
      this.context = context;
      this.address = address;
      this.recipient = RecipientFactory.getRecipientsFromString(getApplicationContext(), address, false).getPrimaryRecipient();
      this.pendingIncomingIceUpdates = new LinkedList<>();
    }

    private CallMember(Context context, String address, int callOrder) {
      this(context, address);
      this.callOrder = callOrder;
      this.pendingOutgoingIceUpdates = new LinkedList<>();
    }

    private void createPeerConnection(List<PeerConnection.IceServer> result, @NonNull VideoRenderer.Callbacks renderer, @NonNull MediaStream localMediaStream, String peerId, int callOrder) {
      this.peerId = peerId;
      this.renderer = renderer;
      this.callOrder = callOrder;
      Log.w(TAG, "createPeerConnection: " + this);
      this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, this, localMediaStream, result);
    }

    private void addIncomingIceCandidate(IceCandidate candidate) {
      if (peerConnection != null) {
        Log.w(TAG, "handleIncomingIceCandidate peerConnection: " + candidate.toString());
        peerConnection.addIceCandidate(candidate);
      } else if (pendingIncomingIceUpdates != null) {
        Log.w(TAG, "handleIncomingIceCandidate pending: " + candidate.toString());
        pendingIncomingIceUpdates.add(candidate);
      }
    }

    private void addOutgoingIceCandidate(IceCandidate candidate) {
      pendingOutgoingIceUpdates.add(candidate);
    }

    private boolean isActiveConnection() {
      return peerConnection != null;
    }

    public int getCallOrder() {
      return callOrder;
    }

    public Recipient getRecipient() {
      return recipient;
    }

    public void setVideoEnabled() {
      if (isActiveConnection()) {
        this.videoEnabled = true;
        videoRenderer = new VideoRenderer(renderer);
        videoTrack.addRenderer(videoRenderer);
        videoTrack.setEnabled(true);
      }
    }

    public void disableVideo() {
      this.videoEnabled = false;
      videoTrack.setEnabled(false);
      videoTrack.removeRenderer(videoRenderer);
    }

    private void terminate() {
      if (peerConnection != null) {
        peerConnection.dispose(localMediaStream);
        peerConnection = null;
      }

      videoEnabled = false;
      if (videoTrack != null) {
        if (videoRenderer != null) {
          videoTrack.removeRenderer(videoRenderer);
          videoRenderer.dispose();
        }
        videoTrack = null;
        videoRenderer = null;
      }

      pendingOutgoingIceUpdates = null;
      pendingIncomingIceUpdates = null;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
      Log.w(TAG, "onSignalingChange: " + newState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
      Log.w(TAG, "onIceConnectionChange:" + newState);

      if (newState == PeerConnection.IceConnectionState.CONNECTED ||
          newState == PeerConnection.IceConnectionState.COMPLETED)
      {
        Intent intent = new Intent(this.context, WebRtcCallService.class);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_REMOTE_ADDRESS, address);
        intent.putExtra(EXTRA_PEER_ID, peerId);
        intent.setAction(ACTION_ICE_CONNECTED);

        startService(intent);
      } else if (newState == PeerConnection.IceConnectionState.FAILED || newState == PeerConnection.IceConnectionState.DISCONNECTED) {
        Intent intent = new Intent(this.context, WebRtcCallService.class);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_REMOTE_ADDRESS, address);
        intent.putExtra(EXTRA_PEER_ID, peerId);
        intent.setAction(ACTION_REMOTE_HANGUP);

        startService(intent);
      }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
      Log.w(TAG, "onIceConnectionReceivingChange:" + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
      Log.w(TAG, "onIceGatheringChange:" + iceGatheringState);
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
      // Why doesn't this just add the candidate directly to the peerConnection?
      Log.w(TAG, "onIceCandidate:" + candidate);
      Intent intent = new Intent(context, WebRtcCallService.class);

      intent.setAction(ACTION_ICE_CANDIDATE);
      intent.putExtra(EXTRA_ICE_SDP_MID, candidate.sdpMid);
      intent.putExtra(EXTRA_ICE_SDP_LINE_INDEX, candidate.sdpMLineIndex);
      intent.putExtra(EXTRA_ICE_SDP, candidate.sdp);
      intent.putExtra(EXTRA_REMOTE_ADDRESS, address);
      intent.putExtra(EXTRA_CALL_ID, callId);

      startService(intent);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
      Log.w(TAG, "onIceCandidatesRemoved:" + (candidates != null ? candidates.length : null));
    }

    @Override
    public void onAddStream(MediaStream stream) {
      Log.w(TAG, "onAddStream:" + stream);

      for (AudioTrack audioTrack : stream.audioTracks) {
        audioTrack.setEnabled(true);
      }

      if (stream.videoTracks != null && stream.videoTracks.size() == 1) {
        videoTrack = stream.videoTracks.getFirst();
        if (callOrder == 1) {
          setVideoEnabled();
        }
      }
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
      Log.w(TAG, "onRemoveStream:" + mediaStream);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
      Log.w(TAG, "onDataChannel:" + dataChannel.label());
    }

    @Override
    public void onRenegotiationNeeded() {
      Log.w(TAG, "onRenegotiationNeeded");
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
      Log.w(TAG, "onAddTrack: " + mediaStreams);
    }

    @Override
    public String toString() {
      return "" + address + " " + recipient.getLocalTag() + " Peer ID: " + peerId + " callOrder: " + callOrder;
    }
  }
}

