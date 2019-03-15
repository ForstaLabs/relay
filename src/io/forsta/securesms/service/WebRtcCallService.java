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
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import org.greenrobot.eventbus.EventBus;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.messaging.IncomingMessage;
import io.forsta.ccsm.messaging.OutgoingMessage;
import io.forsta.ccsm.webrtc.CallRecipient;
import io.forsta.securesms.R;
import io.forsta.securesms.WebRtcCallActivity;

import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.dependencies.TextSecureCommunicationModule;
import io.forsta.securesms.events.WebRtcViewModel;
import io.forsta.ccsm.messaging.OutgoingMediaMessage;
import io.forsta.securesms.notifications.NotificationChannels;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.transport.UndeliverableMessageException;
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
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;

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
    STATE_IDLE, STATE_REMOTE_RINGING, STATE_LOCAL_RINGING, STATE_CONNECTED, STATE_ANSWERING
  }

  private static final String DATA_CHANNEL_NAME = "signaling";

  public static final String EXTRA_REMOTE_ADDRESS     = "remote_address";
  public static final String EXTRA_MUTE               = "mute_value";
  public static final String EXTRA_AVAILABLE          = "enabled_value";
  public static final String EXTRA_REMOTE_DESCRIPTION = "remote_description";
  public static final String EXTRA_TIMESTAMP          = "timestamp";
  public static final String EXTRA_CALL_ID            = "call_id";
  public static final String EXTRA_DEVICE_ID            = "device_id";
  public static final String EXTRA_ICE_SDP            = "ice_sdp";
  public static final String EXTRA_ICE_SDP_MID        = "ice_sdp_mid";
  public static final String EXTRA_ICE_SDP_LINE_INDEX = "ice_sdp_line_index";
  public static final String EXTRA_ICE_SDP_LIST            = "ice_sdp_list";
  public static final String EXTRA_ICE_SDP_MID_LIST        = "ice_sdp_mid_list";
  public static final String EXTRA_ICE_SDP_LINE_INDEX_LIST = "ice_sdp_line_index_list";
  public static final String EXTRA_RESULT_RECEIVER    = "result_receiver";
  public static final String EXTRA_THREAD_UID         = "thread_uid";
  public static final String EXTRA_PEER_ID            = "peer_id";
  public static final String EXTRA_CALL_MEMBERS       = "call_members";
  public static final String EXTRA_CALL_ORDER       = "call_order";

  public static final String ACTION_INCOMING_CALL        = "CALL_INCOMING";
  public static final String ACTION_CALL_OFFER        = "CALL_OFFER";
  public static final String ACTION_JOIN_CALL        = "JOIN_CALL";

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
  public static final String ACTION_ADD_ICE_MESSAGE     = "ADD_ICE_MESSAGE";

  public static final String ACTION_RESPONSE_MESSAGE  = "RESPONSE_MESSAGE";
  public static final String ACTION_ICE_MESSAGE       = "ICE_MESSAGE";
  public static final String ACTION_ICE_CANDIDATE     = "ICE_CANDIDATE";
  public static final String ACTION_ICE_CANDIDATES     = "ICE_CANDIDATES";
  public static final String ACTION_CALL_CONNECTED    = "CALL_CONNECTED";
  public static final String ACTION_REMOTE_HANGUP     = "REMOTE_HANGUP";
  public static final String ACTION_REMOTE_BUSY       = "REMOTE_BUSY";
  public static final String ACTION_REMOTE_VIDEO_MUTE = "REMOTE_VIDEO_MUTE";
  public static final String ACTION_REMOTE_VIDEO_ENABLE = "REMOTE_VIDEO_MUTE";
  public static final String ACTION_ICE_CONNECTED     = "ICE_CONNECTED";
  public static final String ACTION_CONNECTION_FAILED     = "CONNECTION_FAILED";
  public static final String ACTION_RESTART_CONNECTION = "RESTART_CONNECTION";
  private static final int MAX_PEERS = 10;

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

  private RemoteCallMembers peerCallMembers;
  private List<PeerConnection.IceServer> iceServers;
  @NonNull  private AudioTrack localAudioTrack;
  @NonNull  private AudioSource localAudioSource;

  @Nullable private VideoCapturer localVideoCapturer;
  @Nullable private VideoSource localVideoSource;
  @Nullable private VideoTrack localVideoTrack;
  @Nullable private MediaStream localMediaStream;

  @Nullable public  static SurfaceViewRenderer localRenderer;
  @Nullable public  static SurfaceViewRenderer remoteRenderer;
  @Nullable private static EglBase             eglBase;

  // Inject this dependency after upgrade to dagger2
  private SignalServiceMessageSender messageSender;

  private ExecutorService          serviceExecutor = Executors.newSingleThreadExecutor();
  private ExecutorService          networkExecutor = Executors.newSingleThreadExecutor();
  private ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

  @Override
  public void onCreate() {
    super.onCreate();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForeground(CallNotificationBuilder.WEBRTC_NOTIFICATION, new NotificationCompat.Builder(this, NotificationChannels.CALLS).build());
    }

    messageSender = TextSecureCommunicationModule.createMessageSender(getApplicationContext());

    initializeResources();

    registerIncomingPstnCallReceiver();
    registerUncaughtExceptionHandler();
    registerWiredHeadsetStateReceiver();
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    Log.d(TAG, "onStartCommand..." + intent.getAction());
    if (intent == null || intent.getAction() == null) {
      return START_NOT_STICKY;
    }

    serviceExecutor.execute(new Runnable() {
      @Override
      public void run() {
        if (intent.getAction().equals(ACTION_CALL_OFFER))                     handleCallOffer(intent);
        else if (intent.getAction().equals(ACTION_JOIN_CALL))                 handleCallJoin(intent);
        else if (intent.getAction().equals(ACTION_OUTGOING_CALL) && isIdle()) handleOutgoingCall(intent);
        else if (intent.getAction().equals(ACTION_ANSWER_CALL))               handleAnswerCall(intent);
        else if (intent.getAction().equals(ACTION_DENY_CALL))                 handleDenyCall(intent);
        else if (intent.getAction().equals(ACTION_LOCAL_HANGUP))              handleLocalHangup(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_HANGUP))             handleRemoteHangup(intent);
        else if (intent.getAction().equals(ACTION_SET_MUTE_AUDIO))            handleSetMuteAudio(intent);
        else if (intent.getAction().equals(ACTION_SET_MUTE_VIDEO))            handleSetMuteVideo(intent);
        else if (intent.getAction().equals(ACTION_BLUETOOTH_CHANGE))          handleBluetoothChange(intent);
        else if (intent.getAction().equals(ACTION_WIRED_HEADSET_CHANGE))      handleWiredHeadsetChange(intent);
        else if (intent.getAction().equals((ACTION_SCREEN_OFF)))              handleScreenOffChange(intent);
        else if (intent.getAction().equals(ACTION_RESPONSE_MESSAGE))          handleAcceptOffer(intent);
        else if (intent.getAction().equals(ACTION_ICE_MESSAGE))               handleIncomingIceCandidates(intent);
        else if (intent.getAction().equals(ACTION_ICE_CANDIDATE))             handleOutgoingIceCandidate(intent);
        else if (intent.getAction().equals(ACTION_ICE_CONNECTED))             handleIceConnected(intent);
        else if (intent.getAction().equals(ACTION_CALL_CONNECTED))            handleCallConnected(intent);
        else if (intent.getAction().equals(ACTION_CHECK_TIMEOUT))             handleCheckTimeout(intent);
        else if (intent.getAction().equals(ACTION_IS_IN_CALL_QUERY))          handleIsInCallQuery(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_VIDEO_ENABLE))       handleRemoteVideoEnable(intent);
        else if (intent.getAction().equals(ACTION_CONNECTION_FAILED))         handleFailedConnection(intent);
        else if (intent.getAction().equals(ACTION_RESTART_CONNECTION))        handleRestartConnection(intent);
        else if (intent.getAction().equals(ACTION_ADD_ICE_MESSAGE))           handleAddIceCandidate(intent);
      }
    });

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy...");
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
    Log.d(TAG, "onBluetoothStateChanged: " + isAvailable);

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
    this.localCallMember.peerId = UUID.randomUUID().toString();
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

  private synchronized void acceptCallOffer(CallMember member, String incomingPeerId, String offer, List<PeerConnection.IceServer> iceServerUpdate) {
    Log.w(TAG, "acceptCallOffer: " + callState + " " + member);
      try {
        member.createPeerConnection(iceServerUpdate, remoteRenderer, localMediaStream, incomingPeerId, member.callOrder);
        member.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, offer));
        SessionDescription sdp = member.peerConnection.createAnswer(new MediaConstraints());
        member.peerConnection.setLocalDescription(sdp);
        if (member.pendingIncomingIceUpdates != null && !member.pendingIncomingIceUpdates.isEmpty()) {
          Log.w(TAG, "Adding pending ice updates...");
          for (IceCandidate candidate : member.pendingIncomingIceUpdates) {
            member.peerConnection.addIceCandidate(candidate);
          }
          member.pendingIncomingIceUpdates = null;
        }

        ListenableFutureTask<Boolean> listenableFutureTask = sendAcceptOffer(member.recipient, member.deviceId, threadUID, callId, sdp, member.peerId);
        listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {

          @Override
          public void onFailureContinue(Throwable error) {
            Log.w(TAG, error);
            insertMissedCall(member.recipient, true);
            member.terminate();
            terminateCall(true);
          }
        });

      } catch (PeerConnectionWrapper.PeerConnectionException e) {
        e.printStackTrace();
        member.terminate();
      }
  }

  // Handlers

  private void handleCallJoin(Intent intent) {
    final String incomingCallId = intent.getStringExtra(EXTRA_CALL_ID);
    final String incomingAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    final int incomingDeviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1);
    final String incomingPeerId = intent.getStringExtra(EXTRA_PEER_ID);
    final String[] members = intent.getStringArrayExtra(EXTRA_CALL_MEMBERS);
    final String incomingThreadId = intent.getStringExtra(EXTRA_THREAD_UID);
    Log.w(TAG, "handleCallJoin: " + callState + " callId: " + incomingCallId + " address: " + incomingAddress + ":" + incomingDeviceId);

    if (callState == CallState.STATE_IDLE) {
      // New call
      threadUID = incomingThreadId;
      callId = incomingCallId;
      callState = CallState.STATE_LOCAL_RINGING;
      peerCallMembers = new RemoteCallMembers(members);
      CallMember incomingMember = new CallMember(this, incomingPeerId, incomingAddress, incomingDeviceId, 1);
      peerCallMembers.addCallMember(incomingMember);
      Log.w(TAG, "New incoming call: " + callState + " " + incomingMember);

      if (isIncomingMessageExpired(intent)) {
        insertMissedCall(incomingMember.recipient, true);
        incomingMember.terminate();
        return;
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        setCallInProgressNotification(TYPE_INCOMING_CONNECTING, incomingMember.recipient);
      }

      initializeVideo();
      lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
      lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

      startCallCardActivity();
      audioManager.initializeAudioForCall();
      audioManager.startIncomingRinger();

      registerPowerButtonReceiver();

      setCallInProgressNotification(TYPE_INCOMING_RINGING, incomingMember.recipient);
      // This only includes the caller.
      sendMessage(WebRtcViewModel.State.CALL_INCOMING, peerCallMembers.members.values(), incomingMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      timeoutExecutor.schedule(new TimeoutRunnable(incomingMember), 30, TimeUnit.SECONDS);

    } else if (callState == CallState.STATE_CONNECTED || callState == CallState.STATE_REMOTE_RINGING) {
      if (!peerCallMembers.isCallMember(incomingAddress)) {
        Log.w(TAG, "Remote address is not a call member: " + incomingAddress);
        // Insert missed call?
        return;
      }

      CallMember currentMember = getCallMember(intent);
      if (currentMember == null) {
        currentMember = new CallMember(this, incomingPeerId, incomingAddress, incomingDeviceId, 0);
        peerCallMembers.addCallMember(currentMember);
      }

      final CallMember callMember = currentMember;
      retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.callState, this.callId) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> result) {
          try {
            callMember.createPeerConnection(result, remoteRenderer, localMediaStream, localCallMember.peerId, callMember.callOrder);
            SessionDescription sdp = callMember.peerConnection.createOffer(new MediaConstraints());
            callMember.peerConnection.setLocalDescription(sdp);

            ListenableFutureTask<Boolean> listenableFutureTask = sendCallOffer(callMember.recipient, callMember.deviceId, threadUID, callId, sdp, localCallMember.peerId);
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
      });

    } else if (callState == CallState.STATE_LOCAL_RINGING && (incomingAddress.equals(localCallMember.address) && incomingDeviceId != localCallMember.deviceId)) {
      Log.d(TAG, "Remote device answered... terminating call");
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, peerCallMembers.members.values(), localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      terminateCall(true);
    }
    else {
      Log.w(TAG, "callJoin for incorrect state: " + callState);
    }
  }

  private void handleCallOffer(Intent intent) {
    final String incomingCallId = intent.getStringExtra(EXTRA_CALL_ID);
    final String incomingAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    final int incomingDeviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1);
    final String incomingPeerId = intent.getStringExtra(EXTRA_PEER_ID);
    final String offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION);
    final String incomingThreadId = intent.getStringExtra(EXTRA_THREAD_UID);
    Log.w(TAG, "handleCallOffer: " + callState + " callId: " + incomingCallId + " address: " + incomingAddress + ":" + incomingDeviceId);

    if (!callId.equals(incomingCallId)) {
      Log.w(TAG, "Missed call from new callId: " + incomingCallId);
      Recipient recipient = RecipientFactory.getRecipient(this, incomingAddress, false);
      insertMissedCall(recipient, true);
      return;
    }

    CallMember callingMember = null;
    if (callState == CallState.STATE_ANSWERING || callState == CallState.STATE_CONNECTED || callState == CallState.STATE_REMOTE_RINGING) {
      callingMember = peerCallMembers.getCallMember(incomingAddress, incomingDeviceId);
      if (callingMember == null) {
        Log.w(TAG, "Got an offer from new call member");
        callingMember = new CallMember(this, incomingPeerId, incomingAddress, incomingDeviceId, 0);
        peerCallMembers.addCallMember(callingMember);
      }
    } else {
      Log.w(TAG, "Invalid call state for call offer: " + callState);
      return;
    }
    Log.w(TAG, "Adding member to call " + callState + " " + callingMember);

    if (isIncomingMessageExpired(intent)) {
      insertMissedCall(callingMember.recipient, true);
      callingMember.terminate();
      return;
    }

    if (iceServers != null && iceServers.size() > 0) {
      acceptCallOffer(callingMember, incomingPeerId, offer, iceServers);
      if (callState == CallState.STATE_ANSWERING) {
        handleCallConnected(intent);
      }
    } else {
      final CallMember finalMember = callingMember;
      retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(callState, callId) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> result) {
          acceptCallOffer(finalMember, incomingPeerId, offer, result);
          if (callState == CallState.STATE_ANSWERING) {
            handleCallConnected(intent);
          }
        }
      });
    }
  }

  private void handleAnswerCall(Intent intent) {
    Log.w(TAG, "handleAnswerCall callState: " + callState);

    if (callState != CallState.STATE_LOCAL_RINGING || callState == CallState.STATE_ANSWERING) {
      Log.w(TAG, "Can only answer from ringing!");
      return;
    }

    callState = CallState.STATE_ANSWERING;
    audioManager.silenceIncomingRinger();
    try {
      Recipients recipients = RecipientFactory.getRecipientsFromStrings(this, peerCallMembers.getCallAddresses(), false);
      ListenableFutureTask<Boolean> listenableFutureTask = sendCallJoin(recipients, threadUID, callId, localCallMember.peerId);
      listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
        @Override
        public void onFailureContinue(Throwable error) {
          Log.w(TAG, error);
          // terminateCall?
          // insert local failure message in thread?
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }

    setLocalVideoEnabled(true);
    setLocalAudioEnabled(true);
    CallMember member = peerCallMembers.getCallMember(1);
    if (member != null) {
      sendMessage(WebRtcViewModel.State.CALL_ANSWERING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
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
      this.callId = threadUID;

      peerCallMembers = new RemoteCallMembers(members);
      initializeVideo();

      lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
      audioManager.initializeAudioForCall();
      audioManager.startOutgoingRinger(OutgoingRinger.Type.SONAR);
      bluetoothStateManager.setWantsConnection(true);

      Recipients remoteRecipients = RecipientFactory.getRecipientsFromStrings(this, peerCallMembers.getCallAddresses(), false);
      ListenableFutureTask<Boolean> listenableFutureTask = sendCallJoin(remoteRecipients, threadUID, callId, localCallMember.peerId);
      listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
        @Override
        public void onFailureContinue(Throwable error) {
          Log.w(TAG, error);
//          sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, callMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
//          callMember.terminate();
        }
      });

      setCallInProgressNotification(TYPE_OUTGOING_RINGING, remoteRecipients);

      sendMessage(WebRtcViewModel.State.CALL_OUTGOING, null, null, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
//      timeoutExecutor.schedule(new TimeoutRunnable(new CallMember(this, localCallMember.address)), 30, TimeUnit.SECONDS);

    } catch (Exception e) {
      Log.e(TAG, "Exception: " + e.getMessage());
    }
  }

  private void handleAcceptOffer(Intent intent) {
    CallMember member = getCallMember(intent);
    String remoteCallId = getCallId(intent);

    Log.w(TAG, "handleAcceptOffer: " + callState + " callId: " + remoteCallId + " member: " + member);
    try {
      if (member == null) {
        Log.w(TAG, "Got answer for unknown call member");
        return;
      }

      if (callId == null || !callId.equals(remoteCallId)) {
        Log.w(TAG, "Got answer for remote call id we're not currently dialing: " + remoteCallId + " != " + callId);
        return;
      }

      if (member.peerConnection == null) {
        Log.w(TAG, "No peer connection for this call accept offer");
        return;
      }

      // ? May want to make sure we have a local description. In other words, we sent the offer.
      // Having an existing remote means that they already sent us an offer and are now sending an accept.
      if (member.peerConnection.getRemoteDescription() == null) {
        member.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION)));
      }

      // This should never happen. Outgoing are immediately sent as they are received.
      // Receiving end will queue them up if they are processed before a peerConnection is created.
      if (member.pendingOutgoingIceUpdates != null && !member.pendingOutgoingIceUpdates.isEmpty()) {
        ListenableFutureTask<Boolean> listenableFutureTask = sendIceUpdate(member.recipient, member.deviceId, threadUID, callId, member.peerId, member.pendingOutgoingIceUpdates);
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

      member.pendingOutgoingIceUpdates = null;
      if (callState != CallState.STATE_CONNECTED) {
        handleCallConnected(intent);
      }
    } catch (PeerConnectionWrapper.PeerConnectionException e) {
      Log.w(TAG, e);
      member.terminate();
    }
  }

  private void handleAddIceCandidate(Intent intent) {
    CallMember member = getCallMember(intent);
    Log.d(TAG, "handleAddIceCandidate: " + callState + " " + member);

    if (member != null && callId != null && callId.equals(getCallId(intent))) {
      IceCandidate candidate = new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
          intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
          intent.getStringExtra(EXTRA_ICE_SDP));
      member.addIncomingIceCandidate(candidate);
    } else {
      Log.w(TAG, "No connection, or invalid callId");
    }
  }

  private void handleIncomingIceCandidates(Intent intent) {
    CallMember member = getCallMember(intent);
    Log.w(TAG, "handleIncomingIceCandidates " + callState + " " + member);

    if (member != null && callId != null && callId.equals(getCallId(intent))) {
      ArrayList<String> sdps = intent.getStringArrayListExtra(EXTRA_ICE_SDP_LIST);
      ArrayList<String> sdpMids = intent.getStringArrayListExtra(EXTRA_ICE_SDP_MID_LIST);
      ArrayList<Integer> sdpMLineIndexes = intent.getIntegerArrayListExtra(EXTRA_ICE_SDP_LINE_INDEX_LIST);
      for (int i=0; i< sdps.size(); i++) {
        IceCandidate ice = new IceCandidate(sdpMids.get(i), sdpMLineIndexes.get(i), sdps.get(i));
        member.addIncomingIceCandidate(ice);
      }
    } else {
      Log.w(TAG, "No valid call member, or invalid callId");
    }
  }

  private void handleOutgoingIceCandidates(Intent intent) {
    CallMember remoteMember = getCallMember(intent);
    Log.d(TAG, "handleOutgoingIceCandidates: " + callState + " " + remoteMember);

    if (callState == CallState.STATE_IDLE || callId == null || !callId.equals(getCallId(intent))) {
      Log.w(TAG, "State is now idle, ignoring ice candidate...");
      return;
    }

    if (remoteMember == null || remoteMember.recipient == null || callId == null) {
      Log.w(TAG, "No caller for this Ice Candidate");
      return;
    }

    ListenableFutureTask<Boolean> listenableFutureTask = sendIceUpdate(remoteMember.recipient, remoteMember.deviceId, threadUID, callId, remoteMember.peerId, remoteMember.pendingOutgoingIceUpdates);
    listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
      @Override
      public void onFailureContinue(Throwable error) {
        Log.w(TAG, error);
        sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, remoteMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
        remoteMember.terminate();
      }
    });
    listenableFutureTask.addListener(new SuccessOnlyListener<Boolean>(callState, callId) {
      @Override
      public void onSuccessContinue(Boolean result) {
        remoteMember.pendingOutgoingIceUpdates.clear();
      }
    });
  }

  private void handleOutgoingIceCandidate(Intent intent) {
    CallMember remoteMember = getCallMember(intent);
    Log.d(TAG, "handleOutgoingIceCandidate: " + callState + " " + remoteMember);

    if (callState == CallState.STATE_IDLE || callId == null || !callId.equals(getCallId(intent))) {
      Log.w(TAG, "State is now idle, ignoring ice candidate...");
      return;
    }

    if (remoteMember == null || remoteMember.recipient == null || callId == null) {
      Log.w(TAG, "No caller for this Ice Candidate");
      return;
    }

    IceCandidate iceUpdateMessage = new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
        intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
        intent.getStringExtra(EXTRA_ICE_SDP));

    List<IceCandidate> candidates = new LinkedList<>();
    candidates.add(iceUpdateMessage);

    ListenableFutureTask<Boolean> listenableFutureTask = sendIceUpdate(remoteMember.recipient, remoteMember.deviceId, threadUID, callId, remoteMember.peerId, candidates);
    listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
      @Override
      public void onFailureContinue(Throwable error) {
        Log.w(TAG, error);
        sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, remoteMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
        remoteMember.terminate();
      }
    });
  }

  private void handleIceConnected(Intent intent) {
    Log.d(TAG, "handleIceConnected callState: " + callState);

    CallMember member = getCallMember(intent);
    if (member == null) {
      Log.w(TAG, "No call member for this call ");
      return;
    }

    if (callState == CallState.STATE_CONNECTED) {
      sendMessage(WebRtcViewModel.State.CALL_MEMBER_JOINING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleCallConnected(Intent intent) {
    CallMember member = getCallMember(intent);
    Log.w(TAG, "handleCallConnected callState: " + callState + " " + member);

    String id = getCallId(intent);
    if (callId != null && !callId.equals(id)) {
      Log.w(TAG, "Ignoring connected for unknown call id: " + id);
      return;
    }

    if (member == null || member.recipient == null || member.peerConnection == null) {
      Log.w(TAG, "No call information for this caller");
      return;
    }

    if (callState != CallState.STATE_REMOTE_RINGING && callState != CallState.STATE_ANSWERING) {
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
    CallMember member = getCallMember(intent);
    Log.w(TAG, "handleCheckTimeout state: " + callState + " " + member);

    if (callState == CallState.STATE_REMOTE_RINGING) {
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      terminateCall(true);
    } else {
      if (member != null && callId != null && callId.equals(intent.getStringExtra(EXTRA_CALL_ID)) && callState != CallState.STATE_CONNECTED) {
        Log.w(TAG, "Timing out call member: " + member + " CallId: " + callId);
        member.terminate();
        sendMessage(WebRtcViewModel.State.CALL_MEMBER_LEAVING, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);

        if (callState == CallState.STATE_LOCAL_RINGING) {
          if (member.callOrder == 1) {
            sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
            insertMissedCall(member.recipient, true);
            terminateCall(true);
          }
        }
      }
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

  private void handleDenyCall(Intent intent) {
    if (callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Can only deny from ringing!");
      return;
    }

    insertStatusMessage(threadUID, getString(R.string.CallService_declined_call));
    terminateCall(true);
  }

  private void handleLocalHangup(Intent intent) {
    Log.w(TAG, "handleLocalHangup: " + callState);
    Recipients recipients = RecipientFactory.getRecipientsFromStrings(this, peerCallMembers.getCallAddresses(), false);
    if (callId != null) {
      sendCallLeave(recipients, threadUID, callId);
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, peerCallMembers.members.values(), localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      insertStatusMessage(threadUID, getString(R.string.CallService_in_call));
    }
    terminateCall(true);
  }

  private void handleRemoteHangup(Intent intent) {
    CallMember member = getCallMember(intent);
    Log.w(TAG, "handleRemoteHangup: " + callState + " " + member);

    if (callId == null || (callId != null && !callId.equals(getCallId(intent)))) {
      Log.w(TAG, "hangup for non-active call...");
      return;
    }


    if (member == null || member.recipient == null) {
      Log.w(TAG, "Received hangup from invalid call member");
      return;
    }

    if (member.videoEnabled) {
      for (CallMember otherMember : peerCallMembers.members.values()) {
        if (otherMember.isActiveConnection() && !otherMember.videoEnabled) {
          otherMember.setVideoEnabled();
          sendMessage(WebRtcViewModel.State.CALL_MEMBER_VIDEO, otherMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
          break;
        }
      }
    }

    member.terminate();
    if (!peerCallMembers.hasActiveCalls()) {
      if (callState == CallState.STATE_REMOTE_RINGING) {
        sendMessage(WebRtcViewModel.State.RECIPIENT_UNAVAILABLE, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      } else {
        sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
      }

      if (callState == CallState.STATE_LOCAL_RINGING) {
        insertMissedCall(member.recipient, true);
      }

      insertStatusMessage(threadUID, getString(R.string.CallService_in_call));
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
    boolean      muted        = intent.getBooleanExtra(EXTRA_MUTE, false);

    this.localVideoEnabled = !muted;
    setLocalVideoEnabled(localVideoEnabled);

    if (callState == CallState.STATE_CONNECTED) {
      if (localVideoEnabled) this.lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
      else                   this.lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
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
    if (callState == CallState.STATE_LOCAL_RINGING)
    {
      Log.w(TAG, "Silencing incoming ringer...");
      audioManager.silenceIncomingRinger();
    }
  }

  private void handleRemoteVideoEnable(Intent intent) {
    CallMember member = getCallMember(intent);
    if (!member.videoEnabled) {
      for (CallMember callMember : peerCallMembers.members.values()) {
        if (member.address.equals(callMember.address)) {
          callMember.setVideoEnabled();
        } else {
          callMember.disableVideo();
        }
      }
    }
    sendMessage(WebRtcViewModel.State.CALL_MEMBER_VIDEO, member, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  private void handleRestartConnection(Intent intent) {
    try {
      CallMember callMember = getCallMember(intent);
      Log.w(TAG, "handleRestartConnection: " + callState + " " + callMember);

      if (callMember != null) {
        callMember.terminate();
        Recipients recipients = RecipientFactory.getRecipientsFor(this, callMember.recipient, false);
        ListenableFutureTask<Boolean> listenableFutureTask = sendCallJoin(recipients, threadUID, callId, localCallMember.peerId);
        listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
          @Override
          public void onFailureContinue(Throwable error) {
            Log.w(TAG, error);
            // terminateCall?
            // insert local failure message in thread?
          }
        });
      }

    } catch (Exception e) {

    }
  }

  private void handleFailedConnection(Intent intent) {
    try {
      CallMember callMember = getCallMember(intent);
      Log.w(TAG, "handleFailedConnection: " + callState + " " + callMember);

      if (callMember != null) {
        callMember.terminate();
        if (!peerCallMembers.hasActiveCalls()) {
          sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, callMember, localVideoEnabled, bluetoothAvailable, microphoneEnabled);
          terminateCall(true);
        }
      }
    } catch (Exception e) {
      Log.w(TAG, "Exception restarting connection: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /// Helper Methods

  private CallMember getCallMember(Intent intent) {
    String address = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    int deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1);
    if (address != null && deviceId !=-1) {
      if (peerCallMembers != null) {
        CallMember member = peerCallMembers.getCallMember(address, deviceId);
        if (member == null) {
          Log.w(TAG, "Received intent from invalid call member");
        }
        return member;
      }
    }
    return null;
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
    Log.w(TAG, "Terminating call...");
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    stopForeground(removeNotification);

    audioManager.stop(callState == CallState.STATE_REMOTE_RINGING || callState == CallState.STATE_CONNECTED);
    bluetoothStateManager.setWantsConnection(false);

    for (CallMember member : peerCallMembers.members.values()) {
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
    peerCallMembers.members.clear();
    this.callId = null;
    this.threadUID = null;
    this.microphoneEnabled = true;
    this.localVideoEnabled = true;
    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
  }

  private List<String> getAllMemberAddresses() {
    List<String> list = new ArrayList<>();
    for (String address : peerCallMembers.remoteAddresses) {
      list.add(address);
    }
    list.add(localCallMember.recipient.getAddress());
    return list;
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
                           CallMember callMember,
                           boolean localVideoEnabled,
                           boolean bluetoothAvailable, boolean microphoneEnabled)
  {
    Map<Integer, CallRecipient> remoteCallRecipients = new HashMap<>();
    if (remoteCallMembers != null) {
      for (CallMember member : remoteCallMembers) {
        remoteCallRecipients.put(member.callOrder, new CallRecipient(member.recipient, state, member.videoEnabled, member.deviceId));
      }
    } else {
      if (callMember != null)  {
        remoteCallRecipients.put(callMember.callOrder, new CallRecipient(callMember.getRecipient(), state, callMember.videoEnabled, callMember.deviceId));
      }
    }

    if (callMember == null) {
      EventBus.getDefault().postSticky(new WebRtcViewModel(state, localVideoEnabled, bluetoothAvailable, microphoneEnabled));
      return;
    } else {
      EventBus.getDefault().postSticky(new WebRtcViewModel(state, remoteCallRecipients, remoteCallRecipients.get(callMember.callOrder), callMember.callOrder, localVideoEnabled, callMember.videoEnabled, bluetoothAvailable, microphoneEnabled));
    }

  }

  // Only send to single device
  private ListenableFutureTask<Boolean> sendIceUpdate(@NonNull final Recipient recipient, int deviceId, String threadUID,
                                                      @NonNull final String callId, @NonNull final String peerId, List<IceCandidate> updates)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          Context context = getApplicationContext();
          ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadUID);
          ForstaUser user = ForstaUser.getLocalForstaUser(context);
          Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
          String payload = ForstaMessageManager.createIceCandidateMessage(user, recipients, thread, callId, peerId, updates);
          OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
          SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(message);
          List<SignalServiceAddress> addresses = getSignalAddresses(context, recipients);
          SignalServiceAddress address = new SignalServiceAddress(recipient.getAddress(), Optional.fromNullable(null));
          Log.d(TAG, "Sending ICE Update: " + recipients.toFullString() + ":" + deviceId);
          messageSender.sendMessage(address, deviceId, mediaMessage);
        } catch (Exception e) {
          e.printStackTrace();
        } catch (EncapsulatedExceptions encapsulatedExceptions) {
          encapsulatedExceptions.printStackTrace();
        }
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  // Only send to single device
  private ListenableFutureTask<Boolean> sendAcceptOffer(@NonNull final Recipient recipient, int deviceId, String threadUID,
                                                        @NonNull final String callId, SessionDescription sdp, String peerId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          Context context = getApplicationContext();
          ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadUID);
          ForstaUser user = ForstaUser.getLocalForstaUser(context);
          Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
          String payload = ForstaMessageManager.createAcceptCallOfferMessage(user, recipients, thread, callId, sdp.description, peerId);
          OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
          SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(message);
          SignalServiceAddress address = new SignalServiceAddress(recipient.getAddress(), Optional.fromNullable(null));
          Log.w(TAG, "Sending callAcceptOffer to: " + address.getNumber() + ":" + deviceId + " " + recipient.toShortString());
          messageSender.sendMessage(address, deviceId, mediaMessage);
        } catch (Exception e) {
          e.printStackTrace();
        } catch (EncapsulatedExceptions encapsulatedExceptions) {
          encapsulatedExceptions.printStackTrace();
        }
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }


  // Only send to single device
  private ListenableFutureTask<Boolean> sendCallOffer(@NonNull final Recipient recipient, int deviceId, String threadUID,
                                                      @NonNull final String callId, SessionDescription sdp, String peerId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          Context context = getApplicationContext();
          ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadUID);
          ForstaUser user = ForstaUser.getLocalForstaUser(context);
          Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
          String payload = ForstaMessageManager.createCallOfferMessage(user, recipients, thread, callId, sdp.description, peerId);
          OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);

          SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(message);
          SignalServiceAddress address = new SignalServiceAddress(recipient.getAddress(), Optional.fromNullable(null));
          Log.w(TAG, "Sending callOffer to: " + address.getNumber() + ":" + deviceId + " " + recipients.toShortString());
          messageSender.sendMessage(address, deviceId, mediaMessage);
        } catch (Exception e) {
          e.printStackTrace();
        } catch (EncapsulatedExceptions encapsulatedExceptions) {
          encapsulatedExceptions.printStackTrace();
        }
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }


  private ListenableFutureTask<Boolean> sendCallJoin(@NonNull final Recipients recipients, String threadUID,
                                                      @NonNull final String callId, String peerId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          Context context = getApplicationContext();
          ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadUID);
          ForstaUser user = ForstaUser.getLocalForstaUser(context);
          List<String> members = recipients.toStringList();
          if (!members.contains(user.getUid())) {
            members.add(user.getUid());
          }
          String payload = ForstaMessageManager.createCallJoinMessage(user, recipients, members, thread, callId, peerId);
          OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);

          SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(message);
          List<SignalServiceAddress> addresses = getSignalAddresses(context, recipients);
          Log.w(TAG, "Sending callJoin to: " + recipients.toShortString());
          messageSender.sendMessage(addresses, mediaMessage);
        } catch (Exception e) {
          e.printStackTrace();
        } catch (EncapsulatedExceptions encapsulatedExceptions) {
          encapsulatedExceptions.printStackTrace();
        }
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private ListenableFutureTask<Boolean> sendCallLeave(@NonNull final Recipients recipients, String threadUID,
                                                      @NonNull final String callId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          Context context = getApplicationContext();
          ForstaThread thread = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadUID);
          ForstaUser user = ForstaUser.getLocalForstaUser(context);
          String payload = ForstaMessageManager.createCallLeaveMessage(user, recipients, thread, callId);
          OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);

          SignalServiceDataMessage mediaMessage = createSignalServiceDataMessage(message);
          List<SignalServiceAddress> addresses = getSignalAddresses(context, recipients);
          Log.w(TAG, "Sending callLeave to: " + recipients.toShortString());
          messageSender.sendMessage(addresses, mediaMessage);
        } catch (Exception e) {
          e.printStackTrace();
        } catch (EncapsulatedExceptions encapsulatedExceptions) {
          encapsulatedExceptions.printStackTrace();
        }
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private List<SignalServiceAddress> getSignalAddresses(Context context, Recipients recipients) {
    List<SignalServiceAddress> addresses = new LinkedList<>();
    for (Recipient recipient : recipients.getRecipientsList()) {
      String localUid = TextSecurePreferences.getLocalNumber(context);
      if (!localUid.equals(recipient.getAddress())) {
        addresses.add(new SignalServiceAddress(recipient.getAddress(), Optional.fromNullable(null)));
      }
    }

    return addresses;
  }

  private SignalServiceDataMessage createSignalServiceDataMessage(OutgoingMediaMessage message) throws UndeliverableMessageException {
    List<Attachment>              scaledAttachments = new LinkedList<Attachment>();
    List<SignalServiceAttachment> attachmentStreams = new LinkedList<SignalServiceAttachment>();
    SignalServiceDataMessage      mediaMessage      = SignalServiceDataMessage.newBuilder()
        .withBody(message.getBody())
        .withAttachments(attachmentStreams)
        .withTimestamp(message.getSentTimeMillis())
        .withExpiration((int)(message.getExpiresIn() / 1000))
        .asExpirationUpdate(message.isExpirationUpdate())
        .build();
    return mediaMessage;
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
          Pair<Long, Long> messagePair = DatabaseFactory.getMmsDatabase(context).insertSecureDecryptedMessageInbox(new MasterSecretUnion(masterSecret), infoMessage, threadId);
          DatabaseFactory.getMmsDatabase(context).markAsRead(messagePair.first);
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
      intent.putExtra(EXTRA_DEVICE_ID, callMember.deviceId);
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
    private int deviceId = 0;
    private Recipient recipient;
    private int callOrder = 0;
    private int connectionRetryCount = 1;
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
      this.peerId = UUID.randomUUID().toString();
      this.recipient = RecipientFactory.getRecipientsFromString(getApplicationContext(), address, false).getPrimaryRecipient();
      this.pendingIncomingIceUpdates = new LinkedList<>();
      this.pendingOutgoingIceUpdates = new LinkedList<>();
    }

    private CallMember(Context context, String peerId, String address, int deviceId, int callOrder) {
      this(context, address);
      this.peerId = peerId;
      this.deviceId = deviceId;
      this.callOrder = callOrder;
    }

    private void createPeerConnection(List<PeerConnection.IceServer> result, @NonNull VideoRenderer.Callbacks renderer, @NonNull MediaStream localMediaStream, String peerId, int callOrder) {
      this.peerId = peerId;
      this.renderer = renderer;
      this.callOrder = callOrder;
      this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, this, localMediaStream, result);
      Log.w(TAG, "createPeerConnection: " + callState + " " + this);
    }

    private void addIncomingIceCandidate(IceCandidate candidate) {
      if (peerConnection != null) {
        peerConnection.addIceCandidate(candidate);
      } else if (pendingIncomingIceUpdates != null) {
        pendingIncomingIceUpdates.add(candidate);
      }
    }

    private void addOutgoingIceCandidate(IceCandidate candidate) {
      if (peerConnection != null) {
        pendingOutgoingIceUpdates.add(candidate);
      }
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
        if (videoTrack != null) {
          videoRenderer = new VideoRenderer(renderer);
          videoTrack.addRenderer(videoRenderer);
          videoTrack.setEnabled(true);
        }
      }
    }

    public void disableVideo() {
      this.videoEnabled = false;
      if (videoTrack != null) {
        videoTrack.setEnabled(false);
        videoTrack.removeRenderer(videoRenderer);
      }
    }

    private void terminate() {
      Log.w(TAG, "terminate: " + this);
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
      Log.d(TAG, "onSignalingChange: " + newState + " " + this);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
      Log.w(TAG, "onIceConnectionChange: " + newState + " " + this);

      if (newState == PeerConnection.IceConnectionState.CONNECTED ||
          newState == PeerConnection.IceConnectionState.COMPLETED)
      {
        Intent intent = new Intent(this.context, WebRtcCallService.class);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_REMOTE_ADDRESS, address);
        intent.putExtra(EXTRA_DEVICE_ID, deviceId);
        intent.putExtra(EXTRA_PEER_ID, peerId);
        intent.setAction(ACTION_ICE_CONNECTED);

        startService(intent);
      } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
        Intent intent = new Intent(this.context, WebRtcCallService.class);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_REMOTE_ADDRESS, address);
        intent.putExtra(EXTRA_DEVICE_ID, deviceId);
        intent.putExtra(EXTRA_PEER_ID, peerId);
        intent.setAction(ACTION_REMOTE_HANGUP);

        startService(intent);
      } else if (newState == PeerConnection.IceConnectionState.FAILED) {
        Intent intent = new Intent(this.context, WebRtcCallService.class);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_REMOTE_ADDRESS, address);
        intent.putExtra(EXTRA_DEVICE_ID, deviceId);
        intent.putExtra(EXTRA_PEER_ID, peerId);
        intent.setAction(ACTION_CONNECTION_FAILED);
        connectionRetryCount = connectionRetryCount > 0 ? connectionRetryCount-- : 0;
        startService(intent);
      }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
      Log.d(TAG, "onIceConnectionReceivingChange: " + this + " receiving: " + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
      Log.d(TAG, "onIceGatheringChange:" + iceGatheringState + " " + this);
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
      Log.d(TAG, "onIceCandidate: " + this);
      Intent intent = new Intent(context, WebRtcCallService.class);

      intent.setAction(ACTION_ICE_CANDIDATE);
      intent.putExtra(EXTRA_ICE_SDP_MID, candidate.sdpMid);
      intent.putExtra(EXTRA_ICE_SDP_LINE_INDEX, candidate.sdpMLineIndex);
      intent.putExtra(EXTRA_ICE_SDP, candidate.sdp);
      intent.putExtra(EXTRA_REMOTE_ADDRESS, address);
      intent.putExtra(EXTRA_DEVICE_ID, deviceId);
      intent.putExtra(EXTRA_CALL_ID, callId);

      startService(intent);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
      Log.d(TAG, "onIceCandidatesRemoved:" + this + " " + (candidates != null ? candidates.length : null));
    }

    @Override
    public void onAddStream(MediaStream stream) {
      Log.d(TAG, "onAddStream: " + this);

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
      Log.d(TAG, "onRemoveStream:" + this);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
      Log.d(TAG, "onDataChannel:" + dataChannel.label());
    }

    @Override
    public void onRenegotiationNeeded() {
      Log.w(TAG, "onRenegotiationNeeded: " + this);
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
      Log.d(TAG, "onAddTrack: " + this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(recipient.getLocalTag()).append(" (").append(address).append(":").append(")").append(" Peer ID: ").append(peerId).append(" callOrder: ").append(callOrder);
      if (peerConnection != null) {
        sb.append(" remote desc: ");
        sb.append(peerConnection.getRemoteDescription() != null ? "Yes" : "None");
        sb.append(" local desc: ");
        sb.append(peerConnection.getLocalDescription() != null ? "Yes" : "None");
      } else {

      }
      return sb.toString();
    }
  }

  private class RemoteCallMembers {
    private Set<String> remoteAddresses;
    private Map<SignalProtocolAddress, CallMember> members;
    private int callerCount = 0;

    RemoteCallMembers(String[] memberAddresses) {
      remoteAddresses = new HashSet<>();
      members = new ArrayMap<>();
      for (String address : memberAddresses) {
        if (!address.equals(localCallMember.getRecipient().getAddress())) {
          remoteAddresses.add(address);
        }
      }
    }

    List<String> getCallAddresses() {
      return new ArrayList<>(remoteAddresses);
    }

    boolean isCallMember(String address) {
      return remoteAddresses.contains(address) || address.equals(localCallMember.address);
    }

    CallMember getCallMember(String address, int deviceId) {
      return members.get(new SignalProtocolAddress(address, deviceId));
    }

    CallMember getCallMember(String peerId) {
      for (CallMember member : members.values()) {
        if (member.peerId.equals(peerId)) {
          return member;
        }
      }
      return null;
    }

    CallMember getCallMember(int callOrder) {
      for (CallMember member : members.values()) {
        if (member.callOrder == callOrder) {
          return member;
        }
      }
      return null;
    }

    void addCallMember(CallMember member) {
      callerCount++;
      if (member.callOrder == 0) {
        member.callOrder = callerCount;
      }
      members.put(new SignalProtocolAddress(member.address, member.deviceId), member);
    }

    private boolean hasActiveCalls() {
      for (CallMember callMember : members.values()) {
        if (callMember.isActiveConnection()) {
          return true;
        }
      }
      return false;
    }
  }
}

