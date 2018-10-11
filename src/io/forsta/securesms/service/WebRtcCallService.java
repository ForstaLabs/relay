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
import android.telephony.TelephonyManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.WebRtcCallActivity;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.dependencies.InjectableType;
import io.forsta.securesms.events.WebRtcViewModel;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.FutureTaskListener;
import io.forsta.securesms.util.ListenableFutureTask;
import io.forsta.securesms.util.ServiceUtil;
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
import org.webrtc.AudioTrack;
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
import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

public class WebRtcCallService extends Service implements InjectableType, PeerConnection.Observer, BluetoothStateManager.BluetoothStateListener {

  private static final String TAG = WebRtcCallService.class.getSimpleName();

  private enum CallState {
    STATE_IDLE, STATE_DIALING, STATE_ANSWERING, STATE_REMOTE_RINGING, STATE_LOCAL_RINGING, STATE_CONNECTED
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
  public static final String EXTRA_THREAD_UID    = "thread_uid";
  public static final String EXTRA_PEER_ID    = "peer_id";
  public static final String EXTRA_CALL_MEMBERS = "call_members";

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
  public static final String ACTION_ICE_CONNECTED     = "ICE_CONNECTED";

  private CallState callState          = CallState.STATE_IDLE;
  private boolean   microphoneEnabled  = true;
  private boolean   localVideoEnabled  = true;
  private boolean   remoteVideoEnabled = true;
  private boolean   bluetoothAvailable = false;

//  @Inject public SignalServiceMessageSender  messageSender;
//  @Inject public SignalServiceAccountManager accountManager;

  private PeerConnectionFactory      peerConnectionFactory;
  private SignalAudioManager         audioManager;
  private BluetoothStateManager      bluetoothStateManager;
  private WiredHeadsetStateReceiver  wiredHeadsetStateReceiver;
  private PowerButtonReceiver        powerButtonReceiver;
  private LockManager                lockManager;

  private IncomingPstnCallReceiver        callReceiver;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;

  //** Encapsulate these into individual Peer objects so we can multiple going.
  @Nullable private String                   peerId;
  @Nullable private Recipient              recipient;
  @Nullable private PeerConnectionWrapper  peerConnection;
  //  @Nullable private DataChannel            dataChannel;
  @Nullable private List<IceCandidate> pendingOutgoingIceUpdates;
  @Nullable private List<IceCandidate> pendingIncomingIceUpdates;
  // **

  @Nullable private String                   callId;
  @Nullable private String threadUID;
  @Nullable private String              originator;
  private Map<String, CallMember> callMembers = new HashMap<>();

  @Nullable public  static SurfaceViewRenderer localRenderer;
  @Nullable public  static SurfaceViewRenderer remoteRenderer;
  @Nullable public  static SurfaceViewRenderer remoteRenderer2;
  @Nullable private static EglBase             eglBase;

  private ExecutorService          serviceExecutor = Executors.newSingleThreadExecutor();
  private ExecutorService          networkExecutor = Executors.newSingleThreadExecutor();
  private ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

  @Override
  public void onCreate() {
    super.onCreate();

    initializeResources();

    registerIncomingPstnCallReceiver();
    registerUncaughtExceptionHandler();
    registerWiredHeadsetStateReceiver();
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    Log.w(TAG, "onStartCommand...");
    if (intent == null || intent.getAction() == null) {
      Log.w(TAG, "Service intent is null");
      return START_NOT_STICKY;
    }

    serviceExecutor.execute(new Runnable() {
      @Override
      public void run() {
        if      (intent.getAction().equals(ACTION_INCOMING_CALL) && isBusy()) handleBusyCall(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_BUSY))               handleBusyMessage(intent);
        if (intent.getAction().equals(ACTION_INCOMING_CALL))                  handleIncomingCall(intent);
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
        else if (intent.getAction().equals(ACTION_REMOTE_VIDEO_MUTE))         handleRemoteVideoMute(intent);
        else if (intent.getAction().equals(ACTION_RESPONSE_MESSAGE))          handleResponseMessage(intent);
        else if (intent.getAction().equals(ACTION_ICE_MESSAGE))               handleRemoteIceCandidate(intent);
        else if (intent.getAction().equals(ACTION_ICE_CANDIDATE))             handleLocalIceCandidate(intent);
        else if (intent.getAction().equals(ACTION_ICE_CONNECTED))             handleIceConnected(intent);
        else if (intent.getAction().equals(ACTION_CALL_CONNECTED))            handleCallConnected(intent);
        else if (intent.getAction().equals(ACTION_CHECK_TIMEOUT))             handleCheckTimeout(intent);
        else if (intent.getAction().equals(ACTION_IS_IN_CALL_QUERY))          handleIsInCallQuery(intent);
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
//    ApplicationContext.getInstance(this).injectDependencies(this);

    this.callState             = CallState.STATE_IDLE;
    this.lockManager           = new LockManager(this);
    this.peerConnectionFactory = new PeerConnectionFactory(new PeerConnectionFactoryOptions());
    this.audioManager          = new SignalAudioManager(this);
    this.bluetoothStateManager = new BluetoothStateManager(this, this);
//    this.messageSender.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));
//    this.accountManager.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));
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

  // Handlers

  private void handleIncomingCall(final Intent intent) {
    Log.w(TAG, "handleIncomingCall()");
    if (callState != CallState.STATE_IDLE) throw new IllegalStateException("Incoming on non-idle");

    final String offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION);
    this.callId = intent.getStringExtra(EXTRA_CALL_ID);
    this.threadUID = intent.getStringExtra(EXTRA_THREAD_UID);
    Log.w(TAG, "Incoming Call ID: " + this.callId);
    this.callState                 = CallState.STATE_ANSWERING;

    this.pendingIncomingIceUpdates = new LinkedList<>();
    this.originator = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    this.recipient                 = getRemoteRecipient(intent);
    this.peerId = intent.getStringExtra(EXTRA_PEER_ID);
    String[] members = intent.getStringArrayExtra(EXTRA_CALL_MEMBERS);
    Log.w(TAG, "Address: " + this.recipient.getAddress());
    Log.w(TAG, "PeerId: " + this.peerId); //Random. Generated in call offer.
    Log.w(TAG, "Members: ");
    for (String item : members) {
      Log.w(TAG, item);
      this.callMembers.put(this.recipient.getAddress(), new CallMember(this, this.recipient, this.callId, this.peerId));
    }

    if (isIncomingMessageExpired(intent)) {
      insertMissedCall(this.recipient, true);
      terminate(false);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setCallInProgressNotification(TYPE_INCOMING_CONNECTING, this.recipient);
    }

    timeoutExecutor.schedule(new TimeoutRunnable(this.callId), 30, TimeUnit.SECONDS);

    initializeVideo();

    retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.callState, this.callId) {

      @Override
      public void onSuccessContinue(List<PeerConnection.IceServer> result) {
        try {

          boolean isAlwaysTurn = false;

          CallMember member = WebRtcCallService.this.callMembers.get(WebRtcCallService.this.recipient.getAddress());
//          member.createPeerConnection(new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, WebRtcCallService.this, localRenderer, result, isAlwaysTurn));
//          member.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, offer));

          WebRtcCallService.this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, WebRtcCallService.this, localRenderer, result, isAlwaysTurn);
          WebRtcCallService.this.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, offer));
          WebRtcCallService.this.lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);

          WebRtcCallService.this.callState = CallState.STATE_LOCAL_RINGING;
          WebRtcCallService.this.lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

          sendMessage(WebRtcViewModel.State.CALL_INCOMING, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
          startCallCardActivity();
          audioManager.initializeAudioForCall();
          audioManager.startIncomingRinger();

          registerPowerButtonReceiver();

          setCallInProgressNotification(TYPE_INCOMING_RINGING, recipient);

        } catch (PeerConnectionWrapper.PeerConnectionException e) {
          Log.w(TAG, e);
          terminate();
        }
      }
    });
  }

  private void handleOutgoingCall(Intent intent) {
    Log.w(TAG, "handleOutgoingCall...");

    if (callState != CallState.STATE_IDLE) throw new IllegalStateException("Dialing from non-idle?");

    try {
      this.callState                 = CallState.STATE_DIALING;
      this.recipient                 = getRemoteRecipient(intent);
      this.threadUID = intent.getStringExtra(EXTRA_THREAD_UID);
      this.callId                    = UUID.randomUUID().toString();
      this.peerId                    = UUID.randomUUID().toString();
      this.pendingOutgoingIceUpdates = new LinkedList<>();

      initializeVideo();

      sendMessage(WebRtcViewModel.State.CALL_OUTGOING, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
      audioManager.initializeAudioForCall();
      audioManager.startOutgoingRinger(OutgoingRinger.Type.SONAR);
      bluetoothStateManager.setWantsConnection(true);

      setCallInProgressNotification(TYPE_OUTGOING_RINGING, recipient);

      timeoutExecutor.schedule(new TimeoutRunnable(this.callId), 1, TimeUnit.MINUTES);

      retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.callState, this.callId) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> result) {
          try {
            boolean isAlwaysTurn = false;

            WebRtcCallService.this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, WebRtcCallService.this, localRenderer, result, isAlwaysTurn);
//            WebRtcCallService.this.dataChannel    = WebRtcCallService.this.peerConnection.createDataChannel(DATA_CHANNEL_NAME);
//            WebRtcCallService.this.dataChannel.registerObserver(WebRtcCallService.this);

            SessionDescription sdp = WebRtcCallService.this.peerConnection.createOffer(new MediaConstraints());
            WebRtcCallService.this.peerConnection.setLocalDescription(sdp);

            Log.w(TAG, "Sending callOffer: " + sdp.description);

            ListenableFutureTask<Boolean> listenableFutureTask = sendCallOfferMessage(recipient, threadUID, callId, sdp, peerId);
            listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
              @Override
              public void onFailureContinue(Throwable error) {
                Log.w(TAG, error);

                if (error instanceof UntrustedIdentityException) {
                  sendMessage(WebRtcViewModel.State.UNTRUSTED_IDENTITY, recipient, null, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                } else if (error instanceof UnregisteredUserException) {
                  sendMessage(WebRtcViewModel.State.NO_SUCH_USER, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                } else if (error instanceof IOException) {
                  sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
                }

                terminate();
              }
            });
          } catch (PeerConnectionWrapper.PeerConnectionException e) {
            Log.w(TAG, e);
            terminate();
          }
        }
      });
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private void handleResponseMessage(Intent intent) {
    try {
      Log.w(TAG, "handleResponseMessage: " + getCallId(intent));

      Recipient remoteRecipient = getRemoteRecipient(intent);
      String remoteCallId = getCallId(intent);
      if (callState != CallState.STATE_DIALING || !getRemoteRecipient(intent).equals(recipient) || this.callId == null ||!this.callId.equals(getCallId(intent))) {
        Log.w(TAG, "Got answer for recipient and call id we're not currently dialing: " + getCallId(intent) + ", " + getRemoteRecipient(intent));
        return;
      }

      if (peerConnection == null || pendingOutgoingIceUpdates == null) {
        throw new AssertionError("assert");
      }

      if (!pendingOutgoingIceUpdates.isEmpty()) {
        Log.w(TAG, "handleResponseMessage pendingOutgoingIceUpdates sendIceUpdateMessage");
        ListenableFutureTask<Boolean> listenableFutureTask = sendIceUpdateMessage(recipient, threadUID, callId, peerId, pendingOutgoingIceUpdates);

        listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
          @Override
          public void onFailureContinue(Throwable error) {
            Log.w(TAG, error);
            sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

            terminate();
          }
        });
      }

      this.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION)));
      this.pendingOutgoingIceUpdates = null;

      sendMessage(WebRtcViewModel.State.CALL_RINGING, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

    } catch (PeerConnectionWrapper.PeerConnectionException e) {
      Log.w(TAG, e);
      terminate();
    }
  }

  private void handleRemoteIceCandidate(Intent intent) {
    //First get the peer connection from the Map.
    //
    String address = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    CallMember connection = callMembers.get(address);
    if (connection == null) {
      Log.w(TAG, "No peer connection existings for this address");
    } else {
      Log.w(TAG, "Connection: " + connection.toString());
    }


    if (this.callId != null && this.callId.equals(getCallId(intent))) {
      IceCandidate candidate = new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
                                                intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
                                                intent.getStringExtra(EXTRA_ICE_SDP));


      if (peerConnection != null) {
        Log.w(TAG, "handleRemoteIceCandidate peerConnection: " + candidate.toString());
        peerConnection.addIceCandidate(candidate);
      } else if (pendingIncomingIceUpdates != null) {
        Log.w(TAG, "handleRemoteIceCandidate pending: " + candidate.toString());
        pendingIncomingIceUpdates.add(candidate);
      }
    }
  }

  private void handleLocalIceCandidate(Intent intent) {
    Log.w(TAG, "handleLocalIceCandidate");
    if (callState == CallState.STATE_IDLE || this.callId == null || !this.callId.equals(getCallId(intent))) {
      Log.w(TAG, "State is now idle, ignoring ice candidate...");
      return;
    }

    if (recipient == null || callId == null) {
      throw new AssertionError("assert: " + callState + ", " + callId);
    }

    IceCandidate iceUpdateMessage = new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
                                                             intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
                                                             intent.getStringExtra(EXTRA_ICE_SDP));
    List<IceCandidate> candidates = new LinkedList<>();
    if (pendingOutgoingIceUpdates != null) {
      Log.w(TAG, "handleLocalIceCandidate pending: " + iceUpdateMessage.toString());
      this.pendingOutgoingIceUpdates.add(iceUpdateMessage);
      return;
    } else {
      candidates.add(iceUpdateMessage);
    }
    Log.w(TAG, "handleLocalIceCandidate sendIceUpdateMessage: " + iceUpdateMessage.toString());
    ListenableFutureTask<Boolean> listenableFutureTask = sendIceUpdateMessage(recipient, threadUID, callId, peerId, candidates);

    listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
      @Override
      public void onFailureContinue(Throwable error) {
        Log.w(TAG, error);
        sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

        terminate();
      }
    });
  }

  private void handleIceConnected(Intent intent) {
    if (callState == CallState.STATE_ANSWERING) {
      Log.w(TAG, "handleIceConnected answering...");
      if (this.recipient == null) throw new AssertionError("assert");
      
      intent.putExtra(EXTRA_CALL_ID, callId);
      handleCallConnected(intent);
    } else if (callState == CallState.STATE_DIALING) {
      Log.w(TAG, "handleIceConnected dialing...");
      if (this.recipient == null) throw new AssertionError("assert");

      this.callState = CallState.STATE_REMOTE_RINGING;
      this.audioManager.startOutgoingRinger(OutgoingRinger.Type.RINGING);

      intent.putExtra(EXTRA_CALL_ID, callId);
      handleCallConnected(intent);
    }
  }

  private void handleCallConnected(Intent intent) {
    Log.w(TAG, "handleCallConnected...");
    if (callState != CallState.STATE_REMOTE_RINGING && callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Ignoring call connected for unknown state: " + callState);
      return;
    }

    String id = getCallId(intent);
    if (this.callId != null && !this.callId.equals(getCallId(intent))) {
      Log.w(TAG, "Ignoring connected for unknown call id: " + getCallId(intent));
      return;
    }

    if (recipient == null || peerConnection == null) {
      throw new AssertionError("assert");
    }

    audioManager.startCommunication(callState == CallState.STATE_REMOTE_RINGING);
    bluetoothStateManager.setWantsConnection(true);

    callState = CallState.STATE_CONNECTED;

    if (localVideoEnabled) lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
    else                   lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);

    sendMessage(WebRtcViewModel.State.CALL_CONNECTED, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

    unregisterPowerButtonReceiver();

    setCallInProgressNotification(CallNotificationBuilder.TYPE_ESTABLISHED, recipient);

    this.peerConnection.setAudioEnabled(microphoneEnabled);
    this.peerConnection.setVideoEnabled(localVideoEnabled);
  }

  private void handleBusyCall(Intent intent) {
    Recipient recipient = getRemoteRecipient(intent);
    String      callId    = getCallId(intent);

//    sendMessage(recipient, SignalServiceCallMessage.forBusy(new BusyMessage(callId)));
    insertMissedCall(getRemoteRecipient(intent), false);
  }

  private void handleBusyMessage(Intent intent) {
    Log.w(TAG, "handleBusyMessage...");

    final Recipient recipient = getRemoteRecipient(intent);
    final String      callId    = getCallId(intent);

    if (callState != CallState.STATE_DIALING || this.callId == null || !this.callId.equals(callId) || !recipient.equals(this.recipient)) {
      Log.w(TAG, "Got busy message for inactive session...");
      return;
    }

    sendMessage(WebRtcViewModel.State.CALL_BUSY, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

    audioManager.startOutgoingRinger(OutgoingRinger.Type.BUSY);
    Util.runOnMainDelayed(new Runnable() {
      @Override
      public void run() {
        Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
        intent.setAction(ACTION_LOCAL_HANGUP);
        intent.putExtra(EXTRA_CALL_ID, intent.getLongExtra(EXTRA_CALL_ID, -1));
        intent.putExtra(EXTRA_REMOTE_ADDRESS, intent.getStringExtra(EXTRA_REMOTE_ADDRESS));

        startService(intent);
      }
    }, WebRtcCallActivity.BUSY_SIGNAL_DELAY_FINISH);
  }

  private void handleCheckTimeout(Intent intent) {
    if (this.callId != null                                   &&
        this.callId.equals(intent.getStringExtra(EXTRA_CALL_ID)) &&
        this.callState != CallState.STATE_CONNECTED)
    {
      Log.w(TAG, "Timing out call: " + this.callId);
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

      if (this.callState == CallState.STATE_ANSWERING || this.callState == CallState.STATE_LOCAL_RINGING) {
        insertMissedCall(this.recipient, true);
      }

      terminate(this.callState == CallState.STATE_DIALING);
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
    setCallInProgressNotification(TYPE_INCOMING_MISSED, recipient);
  }

  private void handleAnswerCall(Intent intent) {
    Log.w(TAG, "handleAnswerCall");
    if (callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Can only answer from ringing!");
      return;
    }

    if (peerConnection == null || recipient == null || callId == null) {
      throw new AssertionError("assert");
    }

    try {
      SessionDescription sdp = this.peerConnection.createAnswer(new MediaConstraints());
      Log.w(TAG, "Answer SDP: " + sdp.description);
      this.peerConnection.setLocalDescription(sdp);
      ListenableFutureTask<Boolean> listenableFutureTask = sendAcceptOfferMessage(recipient, threadUID, this.callId, sdp, this.peerId);
      listenableFutureTask.addListener(new FailureListener<Boolean>(this.callState, this.callId) {

        @Override
        public void onFailureContinue(Throwable error) {
          Log.w(TAG, error);
          insertMissedCall(recipient, true);
          terminate();
        }
      });

      for (IceCandidate candidate : pendingIncomingIceUpdates) {
        WebRtcCallService.this.peerConnection.addIceCandidate(candidate);
      }
      WebRtcCallService.this.pendingIncomingIceUpdates = null;
    } catch (PeerConnectionWrapper.PeerConnectionException e) {
      e.printStackTrace();
    }

    this.peerConnection.setAudioEnabled(true);
    this.peerConnection.setVideoEnabled(true);

    intent.putExtra(EXTRA_CALL_ID, callId);
    intent.putExtra(EXTRA_REMOTE_ADDRESS, recipient.getAddress());
    handleCallConnected(intent);
  }

  private void handleDenyCall(Intent intent) {
    if (callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Can only deny from ringing!");
      return;
    }

    if (recipient == null || callId == null) {
      throw new AssertionError("assert");
    }

    //Could also add message to thread here.
    sendCallLeaveMessage(recipient, threadUID, callId);
    insertMissedCall(recipient, true);
    this.terminate();
  }

  private void handleLocalHangup(Intent intent) {
    if (this.recipient != null && this.callId != null) {
      sendCallLeaveMessage(this.recipient, this.threadUID, this.callId);
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }

    terminate();
  }

  private void handleRemoteHangup(Intent intent) {
    Log.w(TAG, "handleRemoteHangup");
    if (this.callId == null || (this.callId != null && !this.callId.equals(getCallId(intent)))) {
      Log.w(TAG, "hangup for non-active call...");
      return;
    }

    if (this.recipient == null) {
      throw new AssertionError("assert");
    }

    if (this.callState == CallState.STATE_DIALING || this.callState == CallState.STATE_REMOTE_RINGING) {
      sendMessage(WebRtcViewModel.State.RECIPIENT_UNAVAILABLE, this.recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    } else {
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }

    if (this.callState == CallState.STATE_ANSWERING || this.callState == CallState.STATE_LOCAL_RINGING) {
      insertMissedCall(this.recipient, true);
    }

    this.terminate(this.callState == CallState.STATE_DIALING || this.callState == CallState.STATE_CONNECTED);
  }

  private void handleSetMuteAudio(Intent intent) {
    boolean muted = intent.getBooleanExtra(EXTRA_MUTE, false);
    this.microphoneEnabled = !muted;

    if (this.peerConnection != null) {
      this.peerConnection.setAudioEnabled(this.microphoneEnabled);
    }
  }

  private void handleSetMuteVideo(Intent intent) {
    AudioManager audioManager = ServiceUtil.getAudioManager(this);
    boolean      muted        = intent.getBooleanExtra(EXTRA_MUTE, false);

    this.localVideoEnabled = !muted;

    if (this.peerConnection != null) {
      this.peerConnection.setVideoEnabled(this.localVideoEnabled);
    }

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

    sendMessage(viewModelStateFor(callState), this.recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  private void handleBluetoothChange(Intent intent) {
    this.bluetoothAvailable = intent.getBooleanExtra(EXTRA_AVAILABLE, false);

    if (recipient != null) {
      sendMessage(viewModelStateFor(callState), recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleWiredHeadsetChange(Intent intent) {
    Log.w(TAG, "handleWiredHeadsetChange...");

    if (callState == CallState.STATE_CONNECTED ||
        callState == CallState.STATE_DIALING   ||
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

      if (recipient != null) {
        sendMessage(viewModelStateFor(callState), recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      }
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

  private void handleRemoteVideoMute(Intent intent) {
    boolean muted  = intent.getBooleanExtra(EXTRA_MUTE, false);
    long    callId = intent.getLongExtra(EXTRA_CALL_ID, -1);

    if (this.recipient == null || this.callState != CallState.STATE_CONNECTED || this.callId == null || !this.callId.equals(callId)) {
      Log.w(TAG, "Got video toggle for inactive call, ignoring...");
      return;
    }

    this.remoteVideoEnabled = !muted;
    sendMessage(WebRtcViewModel.State.CALL_CONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  /// Helper Methods

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
      }
    });
  }

  private void setCallInProgressNotification(int type, Recipient recipient) {
    startForeground(CallNotificationBuilder.WEBRTC_NOTIFICATION,
                    CallNotificationBuilder.getCallInProgressNotification(this, type, recipient));
  }

  private synchronized void terminate() {
    terminate(true);
  }

  private synchronized void terminate(boolean removeNotification) {
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    stopForeground(removeNotification);

    audioManager.stop(callState == CallState.STATE_DIALING || callState == CallState.STATE_REMOTE_RINGING || callState == CallState.STATE_CONNECTED);
    bluetoothStateManager.setWantsConnection(false);

    if (peerConnection != null) {
      peerConnection.dispose();
      peerConnection = null;
    }

    if (eglBase != null && localRenderer != null && remoteRenderer != null) {
      localRenderer.release();
      remoteRenderer.release();
      eglBase.release();

      localRenderer  = null;
      remoteRenderer = null;
      eglBase        = null;
    }

    this.callState                 = CallState.STATE_IDLE;
    this.recipient                 = null;
    this.callId                    = null;
    this.microphoneEnabled         = true;
    this.localVideoEnabled         = true;
    this.remoteVideoEnabled        = true;
    this.pendingOutgoingIceUpdates = null;
    this.pendingIncomingIceUpdates = null;
    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
  }


  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull Recipient recipient,
                           boolean localVideoEnabled, boolean remoteVideoEnabled,
                           boolean bluetoothAvailable, boolean microphoneEnabled)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state, recipient, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled));
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull Recipient recipient,
                           @NonNull IdentityKey identityKey,
                           boolean localVideoEnabled, boolean remoteVideoEnabled,
                           boolean bluetoothAvailable, boolean microphoneEnabled)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state, recipient, identityKey, localVideoEnabled, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled));
  }

  private ListenableFutureTask<Boolean> sendIceUpdateMessage(@NonNull final Recipient recipient, String threadUID,
                                                    @NonNull final String callId, @NonNull final String peerId, List<IceCandidate> updates)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());
        Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
        MessageSender.sendIceUpdate(getApplicationContext(), masterSecret, recipients, threadUID, callId, peerId, updates);
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
        MessageSender.sendCallAcceptOffer(getApplicationContext(), masterSecret, recipients, threadUID, callId, sdp, peerId);
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private ListenableFutureTask<Boolean> sendCallOfferMessage(@NonNull final Recipient recipient, String threadUID,
                                                               @NonNull final String callId, SessionDescription sdp, String peerId)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());
        Recipients recipients = RecipientFactory.getRecipientsFor(getApplicationContext(), recipient, false);
        MessageSender.sendCallOffer(getApplicationContext(), masterSecret, recipients, threadUID, callId, sdp, peerId);
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

  private void startCallCardActivity() {
    Intent activityIntent = new Intent();
    activityIntent.setClass(this, WebRtcCallActivity.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    this.startActivity(activityIntent);
  }

  private @NonNull Recipient getRemoteRecipient(Intent intent) {
    String remoteAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS);
    if (remoteAddress == null) throw new AssertionError("No recipient in intent!");

    return RecipientFactory.getRecipientsFromString(getApplicationContext(), remoteAddress, false).getPrimaryRecipient();
  }

  private String getCallId(Intent intent) {
    return intent.getStringExtra(EXTRA_CALL_ID);
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /// PeerConnection Observer
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
      Intent intent = new Intent(this, WebRtcCallService.class);
      intent.setAction(ACTION_ICE_CONNECTED);

      startService(intent);
    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
      Intent intent = new Intent(this, WebRtcCallService.class);
      intent.setAction(ACTION_REMOTE_HANGUP);
      intent.putExtra(EXTRA_CALL_ID, this.callId);

      startService(intent);
    }
  }

  @Override
  public void onIceConnectionReceivingChange(boolean receiving) {
    Log.w(TAG, "onIceConnectionReceivingChange:" + receiving);
  }

  @Override
  public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
    Log.w(TAG, "onIceGatheringChange:" + newState);

  }

  @Override
  public void onIceCandidate(IceCandidate candidate) {
    Log.w(TAG, "onIceCandidate:" + candidate);
    Intent intent = new Intent(this, WebRtcCallService.class);

    intent.setAction(ACTION_ICE_CANDIDATE);
    intent.putExtra(EXTRA_ICE_SDP_MID, candidate.sdpMid);
    intent.putExtra(EXTRA_ICE_SDP_LINE_INDEX, candidate.sdpMLineIndex);
    intent.putExtra(EXTRA_ICE_SDP, candidate.sdp);
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
      VideoTrack videoTrack = stream.videoTracks.getFirst();
      videoTrack.setEnabled(true);
      videoTrack.addRenderer(new VideoRenderer(remoteRenderer));
    }
  }

  @Override
  public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
    Log.w(TAG, "onAddTrack: " + mediaStreams);
  }

  @Override
  public void onRemoveStream(MediaStream stream) {
    Log.w(TAG, "onRemoveStream:" + stream);
  }

  @Override
  public void onDataChannel(DataChannel dataChannel) {
    Log.w(TAG, "onDataChannel:" + dataChannel.label());
  }

  @Override
  public void onRenegotiationNeeded() {
    Log.w(TAG, "onRenegotiationNeeded");
    // TODO renegotiate
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

  private WebRtcViewModel.State viewModelStateFor(CallState state) {
    switch (state) {
      case STATE_CONNECTED:      return WebRtcViewModel.State.CALL_CONNECTED;
      case STATE_DIALING:        return WebRtcViewModel.State.CALL_OUTGOING;
      case STATE_REMOTE_RINGING: return WebRtcViewModel.State.CALL_RINGING;
      case STATE_LOCAL_RINGING:  return WebRtcViewModel.State.CALL_INCOMING;
      case STATE_ANSWERING:      return WebRtcViewModel.State.CALL_INCOMING;
      case STATE_IDLE:           return WebRtcViewModel.State.CALL_DISCONNECTED;
    }

    return WebRtcViewModel.State.CALL_DISCONNECTED;
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

    private final String callId;

    private TimeoutRunnable(String callId) {
      this.callId = callId;
    }

    public void run() {
      Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_CHECK_TIMEOUT);
      intent.putExtra(EXTRA_CALL_ID, callId);
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

  private class CallMember implements PeerConnection.Observer {
    private volatile Context context;
    private String callId;
    private String peerId;
    private Recipient recipient;
    @Nullable private PeerConnectionWrapper peerConnection;
    @Nullable private List<IceCandidate> pendingOutgoingIceUpdates;
    @Nullable private List<IceCandidate> pendingIncomingIceUpdates;

    public CallMember(Context context, Recipient recipient, String callId, String peerId) {
      this.context = context;
      this.callId = callId;
      this.recipient = recipient;
      this.peerId = peerId;
    }

    public void createPeerConnection(PeerConnectionWrapper peerConnection) {
      this.peerConnection = peerConnection;
    }

    public void setRemoteDescription(SessionDescription sdp) {
      try {
        this.peerConnection.setRemoteDescription(sdp);
      } catch (PeerConnectionWrapper.PeerConnectionException e) {
        e.printStackTrace();
        terminate();
      }
    }

    public void terminate() {
      if (this.peerConnection != null) {
        this.peerConnection.dispose();
        this.peerConnection = null;
      }

      this.pendingOutgoingIceUpdates = null;
      this.pendingIncomingIceUpdates = null;
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
        Intent intent = new Intent(this.context, CallMember.class);
        intent.setAction(ACTION_ICE_CONNECTED);

        startService(intent);
      } else if (newState == PeerConnection.IceConnectionState.FAILED) {
        Intent intent = new Intent(this.context, CallMember.class);
        intent.setAction(ACTION_REMOTE_HANGUP);
        intent.putExtra(EXTRA_CALL_ID, this.callId);

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
//      Log.w(TAG, "onIceCandidate:" + candidate);
//      Intent intent = new Intent(context, CallMember.class);
//
//      intent.setAction(ACTION_ICE_CANDIDATE);
//      intent.putExtra(EXTRA_ICE_SDP_MID, candidate.sdpMid);
//      intent.putExtra(EXTRA_ICE_SDP_LINE_INDEX, candidate.sdpMLineIndex);
//      intent.putExtra(EXTRA_ICE_SDP, candidate.sdp);
//      intent.putExtra(EXTRA_CALL_ID, callId);
//
//      startService(intent);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

    }

    @Override
    public void onAddStream(MediaStream stream) {
      Log.w(TAG, "onAddStream:" + stream);

      for (AudioTrack audioTrack : stream.audioTracks) {
        audioTrack.setEnabled(true);
      }

      if (stream.videoTracks != null && stream.videoTracks.size() == 1) {
        VideoTrack videoTrack = stream.videoTracks.getFirst();
        videoTrack.setEnabled(true);
        videoTrack.addRenderer(new VideoRenderer(remoteRenderer)); // This is static from the enclosing class. Can it be encapsulated into this class?
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
      return "" + this.recipient.getAddress() + " " + this.recipient.getLocalTag() + " Peer ID: " + this.peerId;
    }
  }
}

