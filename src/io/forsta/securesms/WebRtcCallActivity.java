/*
 * Copyright (C) 2016 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.forsta.securesms;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.telecom.Call;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Map;

import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.messaging.IncomingMessage;
import io.forsta.ccsm.webrtc.CallMemberListAdapter;
import io.forsta.ccsm.webrtc.CallRecipient;
import io.forsta.securesms.components.webrtc.WebRtcAnswerDeclineButton;
import io.forsta.securesms.components.webrtc.WebRtcCallControls;
import io.forsta.securesms.components.webrtc.WebRtcCallScreen;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.events.WebRtcViewModel;
import io.forsta.securesms.permissions.Permissions;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.service.WebRtcCallService;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.ViewUtil;
import ws.com.google.android.mms.MmsException;

public class WebRtcCallActivity extends Activity {

  private static final String TAG = WebRtcCallActivity.class.getSimpleName();

  private static final int STANDARD_DELAY_FINISH    = 1000;
  public  static final int BUSY_SIGNAL_DELAY_FINISH = 5500;

  public static final String ANSWER_ACTION   = WebRtcCallActivity.class.getCanonicalName() + ".ANSWER_ACTION";
  public static final String DENY_ACTION     = WebRtcCallActivity.class.getCanonicalName() + ".DENY_ACTION";
  public static final String END_CALL_ACTION = WebRtcCallActivity.class.getCanonicalName() + ".END_CALL_ACTION";

  private WebRtcCallScreen callScreen;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.w(TAG, "onCreate()");
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.webrtc_call_activity);

    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

    initializeResources();
  }

  @Override
  public void onResume() {
    Log.w(TAG, "onResume()");
    super.onResume();
    initializeScreenshotSecurity();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onNewIntent(Intent intent){
    Log.w(TAG, "onNewIntent");
    if (ANSWER_ACTION.equals(intent.getAction())) {
      handleAnswerCall();
    } else if (DENY_ACTION.equals(intent.getAction())) {
      handleDenyCall();
    } else if (END_CALL_ACTION.equals(intent.getAction())) {
      handleEndCall();
    }
  }

  @Override
  public void onPause() {
    Log.w(TAG, "onPause");
    super.onPause();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void initializeScreenshotSecurity() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
        TextSecurePreferences.isScreenSecurityEnabled(this))
    {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }

  private void initializeResources() {
    callScreen = ViewUtil.findById(this, R.id.callScreen);
    callScreen.setHangupButtonListener(new HangupButtonListener());
    callScreen.setIncomingCallActionListener(new IncomingCallActionListener());
    callScreen.setAudioMuteButtonListener(new AudioMuteButtonListener());
    callScreen.setVideoMuteButtonListener(new VideoMuteButtonListener());
    callScreen.setSpeakerButtonListener(new SpeakerButtonListener());
    callScreen.setBluetoothButtonListener(new BluetoothButtonListener());
    callScreen.setCallRecipientsClickListener(new CallRecipientClickListener());
  }

  private void handleSetMuteAudio(boolean enabled) {
    Intent intent = new Intent(this, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_SET_MUTE_AUDIO);
    intent.putExtra(WebRtcCallService.EXTRA_MUTE, enabled);
    startService(intent);
  }

  private void handleSetMuteVideo(boolean muted) {
    Intent intent = new Intent(this, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_SET_MUTE_VIDEO);
    intent.putExtra(WebRtcCallService.EXTRA_MUTE, muted);
    startService(intent);
  }

  private void handleAnswerCall() {
    Log.w(TAG, "handleAnswerCall: ");
    WebRtcViewModel event = EventBus.getDefault().getStickyEvent(WebRtcViewModel.class);

    if (event != null) {
      Permissions.with(this)
                 .request(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.Permissions_required_microphone_answer, event.getCallRecipient().getRecipient().toShortString()),
                                      R.drawable.ic_mic_white_48dp, R.drawable.ic_videocam_white_48dp)
                 .withPermanentDenialDialog(getString(R.string.Permissions_required_microphone))
                 .onAllGranted(() -> {
                   Intent intent = new Intent(this, WebRtcCallService.class);
                   intent.setAction(WebRtcCallService.ACTION_ANSWER_CALL);
                   startService(intent);
                 })
                 .onAnyDenied(this::handleDenyCall)
                 .execute();
    }
  }

  private void handleDenyCall() {
    Log.w(TAG, "handleDenyCall...");
    WebRtcViewModel event = EventBus.getDefault().getStickyEvent(WebRtcViewModel.class);

    if (event != null) {
      Intent intent = new Intent(this, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_DENY_CALL);
      startService(intent);

      callScreen.updateCallMember(event.getCallRecipient(), event.getCallOrder());
      delayedFinish();
    }
  }

  private void handleEndCall() {
    Log.w(TAG, "Hangup pressed, handling termination now...");
    Intent intent = new Intent(WebRtcCallActivity.this, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_LOCAL_HANGUP);
    startService(intent);
  }

  private void handleIncomingCall(@NonNull WebRtcViewModel event) {
    callScreen.setIncomingCall(event.getCallRecipient(), event.getCallOrder(), event.getRemoteCallRecipients());
  }

  private void handleOutgoingCall(@NonNull WebRtcViewModel event) {
    callScreen.setOutgoingCall(event.getCallRecipient(), event.getCallOrder(), event.getRemoteCallRecipients());
  }

  private void handleTerminate(@NonNull WebRtcViewModel event) {
    Log.w(TAG, "handleTerminate called");

    Map<Integer, CallRecipient> callRecipients = event.getRemoteCallRecipients();
    for (Map.Entry<Integer, CallRecipient> terminateRecipient : callRecipients.entrySet()) {
      callScreen.updateCallMember(terminateRecipient.getValue(), terminateRecipient.getKey());
    }
    EventBus.getDefault().removeStickyEvent(WebRtcViewModel.class);

    delayedFinish();
  }

  private void handleCallRinging(@NonNull WebRtcViewModel event) {
    callScreen.updateCallMember(event.getCallRecipient(), event.getCallOrder());
  }

  private void handleCallBusy(@NonNull WebRtcViewModel event) {
    callScreen.updateCallMember(event.getCallRecipient(), event.getCallOrder());

    delayedFinish(BUSY_SIGNAL_DELAY_FINISH);
  }

  private void handleCallConnected(@NonNull WebRtcViewModel event) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);
    if (event.getCallOrder() == 0) {
      callScreen.setLocalVideoEnabled(event.isLocalVideoEnabled());
    } else {
      callScreen.setActiveCall(event.getCallRecipient(), event.getCallOrder());
    }
  }

  private void handleCallMemberJoining(@NonNull WebRtcViewModel event) {
    Log.d(TAG, "Member joining. " + event.getCallRecipient() + " callOrder: " +  event.getCallOrder());
    callScreen.updateCallMember(event.getCallRecipient(), event.getCallOrder());
  }

  private void handleCallMemberLeaving(@NonNull WebRtcViewModel event) {
    Log.w(TAG, "Member leaving. " + event.getCallRecipient() + " callOrder: " +  event.getCallOrder());
    callScreen.updateCallMember(event.getCallRecipient(), event.getCallOrder());
  }

  private void handleCallMemberVideoOn(@NonNull WebRtcViewModel event) {
    Log.w(TAG, "Member video on. " + event.getCallRecipient() + " callOrder: " +  event.getCallOrder());
    callScreen.updateVideoSelection(event.getCallRecipient(), event.getCallOrder());
  }

  private void handleRecipientUnavailable(@NonNull WebRtcViewModel event) {
    callScreen.updateCallMember(event.getCallRecipient(), event.getCallOrder());
    delayedFinish();
  }

  private void handleServerFailure(@NonNull WebRtcViewModel event) {
    callScreen.updateCallMember(event.getCallRecipient(), event.getCallOrder());
    delayedFinish();
  }

  private void handleNoSuchUser(final @NonNull WebRtcViewModel event) {
    if (isFinishing()) return; // XXX Stuart added this check above, not sure why, so I'm repeating in ignorance. - moxie
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(R.string.RedPhone_number_not_registered);
    dialog.setIconAttribute(R.attr.dialog_alert_icon);
    dialog.setMessage(R.string.RedPhone_the_number_you_dialed_does_not_support_secure_voice);
    dialog.setCancelable(true);
    dialog.setPositiveButton(R.string.RedPhone_got_it, new OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        WebRtcCallActivity.this.handleTerminate(event);
      }
    });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        WebRtcCallActivity.this.handleTerminate(event);
      }
    });
    dialog.show();
  }

  private void handleVideoEnable(final @NonNull WebRtcViewModel event) {
    callScreen.setLocalVideoEnabled(event.isLocalVideoEnabled());
  }

  private void delayedFinish() {
    delayedFinish(STANDARD_DELAY_FINISH);
  }

  private void delayedFinish(int delayMillis) {
    callScreen.postDelayed(new Runnable() {
      public void run() {

        WebRtcCallActivity.this.finish();
      }
    }, delayMillis);
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventMainThread(final WebRtcViewModel event) {
    Log.d(TAG, "EventBus message: " + event);

    switch (event.getState()) {
      case CALL_CONNECTED:          handleCallConnected(event);            break;
      case NETWORK_FAILURE:         handleServerFailure(event);            break;
      case CALL_RINGING:            handleCallRinging(event);              break;
      case CALL_DISCONNECTED:       handleTerminate(event);                break;
      case NO_SUCH_USER:            handleNoSuchUser(event);               break;
      case RECIPIENT_UNAVAILABLE:   handleRecipientUnavailable(event);     break;
      case CALL_INCOMING:           handleIncomingCall(event);             break;
      case CALL_OUTGOING:           handleOutgoingCall(event);             break;
      case CALL_BUSY:               handleCallBusy(event);                 break;
      case CALL_MEMBER_JOINING:     handleCallMemberJoining(event);        break;
      case CALL_MEMBER_LEAVING:     handleCallMemberLeaving(event);        break;
      case CALL_MEMBER_VIDEO:       handleCallMemberVideoOn(event);        break;
    }

    callScreen.setLocalVideoEnabled(event.isLocalVideoEnabled());
    callScreen.setRemoteVideoEnabled(true);
    callScreen.updateAudioState(event.isBluetoothAvailable(), event.isMicrophoneEnabled());
    callScreen.setControlsEnabled(event.getState() != WebRtcViewModel.State.CALL_INCOMING);
  }

  private class HangupButtonListener implements WebRtcCallScreen.HangupButtonListener {
    public void onClick() {
      handleEndCall();
    }
  }

  private class AudioMuteButtonListener implements WebRtcCallControls.MuteButtonListener {
    @Override
    public void onToggle(boolean isMuted) {
      WebRtcCallActivity.this.handleSetMuteAudio(isMuted);
    }
  }

  private class VideoMuteButtonListener implements WebRtcCallControls.MuteButtonListener {
    @Override
    public void onToggle(boolean isMuted) {
      WebRtcCallActivity.this.handleSetMuteVideo(isMuted);
    }
  }

  private class SpeakerButtonListener implements WebRtcCallControls.SpeakerButtonListener {
    @Override
    public void onSpeakerChange(boolean isSpeaker) {
      AudioManager audioManager = ServiceUtil.getAudioManager(WebRtcCallActivity.this);
      audioManager.setSpeakerphoneOn(isSpeaker);

      if (isSpeaker && audioManager.isBluetoothScoOn()) {
        audioManager.stopBluetoothSco();
        audioManager.setBluetoothScoOn(false);
      }
    }
  }

  private class BluetoothButtonListener implements WebRtcCallControls.BluetoothButtonListener {
    @Override
    public void onBluetoothChange(boolean isBluetooth) {
      AudioManager audioManager = ServiceUtil.getAudioManager(WebRtcCallActivity.this);

      if (isBluetooth) {
        audioManager.startBluetoothSco();
        audioManager.setBluetoothScoOn(true);
      } else {
        audioManager.stopBluetoothSco();
        audioManager.setBluetoothScoOn(false);
      }
    }
  }

  private class IncomingCallActionListener implements WebRtcAnswerDeclineButton.AnswerDeclineListener {
    @Override
    public void onAnswered() {
      WebRtcCallActivity.this.handleAnswerCall();
    }

    @Override
    public void onDeclined() {
      WebRtcCallActivity.this.handleDenyCall();
    }
  }

  private class CallRecipientClickListener implements CallMemberListAdapter.ItemClickListener {

    @Override
    public void onItemClick(int position) {
      CallRecipient recipient = callScreen.getCallRecipient(position + 1);
      Log.d(TAG, "Clicked CallRecipient: " + (position + 1) + " " + recipient);
      if (isValidCallState(recipient.getCallState()) && !recipient.isVideoEnabled()) {
        Intent intent = new Intent(WebRtcCallActivity.this, WebRtcCallService.class);
        intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, recipient.getRecipient().getAddress());
        intent.putExtra(WebRtcCallService.EXTRA_CALL_ORDER, position + 1);
        intent.setAction(WebRtcCallService.ACTION_REMOTE_VIDEO_ENABLE);
        startService(intent);
      } else if (recipient.getCallState() != WebRtcViewModel.State.CALL_MEMBER_LEAVING) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(WebRtcCallActivity.this);
        dialog.setTitle("Connection Failure");
        dialog.setIconAttribute(R.attr.dialog_alert_icon);
        dialog.setMessage("Restart connection?");
        dialog.setCancelable(true);
        dialog.setPositiveButton("Yes", new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(WebRtcCallActivity.this, WebRtcCallService.class);
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, recipient.getRecipient().getAddress());
            intent.putExtra(WebRtcCallService.EXTRA_CALL_ORDER, position + 1);
            intent.setAction(WebRtcCallService.ACTION_RESTART_CONNECTION);
            startService(intent);
          }
        });
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
          @Override
          public void onCancel(DialogInterface dialog) {
            return;
          }
        });
        dialog.show();

      }
    }

    private boolean isValidCallState(WebRtcViewModel.State state) {
      return state == WebRtcViewModel.State.CALL_CONNECTED || state == WebRtcViewModel.State.CALL_MEMBER_JOINING || state == WebRtcViewModel.State.CALL_MEMBER_VIDEO;
    }
  }
}