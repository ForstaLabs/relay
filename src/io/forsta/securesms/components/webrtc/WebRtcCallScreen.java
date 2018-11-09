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

package io.forsta.securesms.components.webrtc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import io.forsta.ccsm.components.webrtc.CallMemberView;
import io.forsta.securesms.R;
//import io.forsta.securesms.mms.GlideApp;
import io.forsta.securesms.contacts.avatars.ContactPhotoFactory;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipient.RecipientModifiedListener;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.service.WebRtcCallService;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
//import io.forsta.securesms.util.VerifySpan;
import io.forsta.securesms.util.ViewUtil;
import org.webrtc.SurfaceViewRenderer;
import org.whispersystems.libsignal.IdentityKey;

import io.forsta.securesms.recipients.Recipient;

/**
 * A UI widget that encapsulates the entire in-call screen
 * for both initiators and responders.
 *
 * @author Moxie Marlinspike
 *
 */
public class WebRtcCallScreen extends FrameLayout implements Recipient.RecipientModifiedListener {

  @SuppressWarnings("unused")
  private static final String TAG = WebRtcCallScreen.class.getSimpleName();

  private FrameLayout   remoteRenderLayout2;
  private FrameLayout   remoteRenderLayout3;
  private TextView             status;
  private FloatingActionButton endCallButton;
  private WebRtcCallControls   controls;
  private ViewGroup            callHeader;

  private WebRtcAnswerDeclineButton incomingCallButton;

  private Recipient localRecipient;
  private boolean   minimized;
  private CallMemberView localMemberLayout;
  private CallMemberView remoteMemberLayout;

  public WebRtcCallScreen(Context context) {
    super(context);
    initialize();
  }

  public WebRtcCallScreen(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public WebRtcCallScreen(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setActiveCall(@NonNull Recipient personInfo, @NonNull String message, @Nullable String sas) {
    // Need to be able to select the appropriate layout for each recipient.
    remoteMemberLayout.setRecipient(personInfo);
    remoteMemberLayout.setCallStatus(message);
    setConnected(WebRtcCallService.localRenderer, WebRtcCallService.remoteRenderer, WebRtcCallService.remoteRenderer2, WebRtcCallService.remoteRenderer3);
    localMemberLayout.setCallStatus("Connected");
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.show();
  }

  public void setActiveCall(@NonNull Recipient personInfo, @NonNull String message) {
    // Need to be able to select the appropriate layout for each recipient.
    remoteMemberLayout.setRecipient(personInfo);
    remoteMemberLayout.setCallStatus(message);
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.show();
  }

  public void setIncomingCall(Recipient personInfo) {
    remoteMemberLayout.setRecipient(personInfo);
    remoteMemberLayout.setCallStatus("Incoming call");
    localMemberLayout.setRecipient(localRecipient);
    endCallButton.setVisibility(View.INVISIBLE);
    incomingCallButton.setVisibility(View.VISIBLE);
    incomingCallButton.startRingingAnimation();
  }

  public void setIncomingCallActionListener(WebRtcAnswerDeclineButton.AnswerDeclineListener listener) {
    incomingCallButton.setAnswerDeclineListener(listener);
  }

  public void setAudioMuteButtonListener(WebRtcCallControls.MuteButtonListener listener) {
    this.controls.setAudioMuteButtonListener(listener);
  }

  public void setVideoMuteButtonListener(WebRtcCallControls.MuteButtonListener listener) {
    this.controls.setVideoMuteButtonListener(listener);
  }

  public void setSpeakerButtonListener(WebRtcCallControls.SpeakerButtonListener listener) {
    this.controls.setSpeakerButtonListener(listener);
  }

  public void setBluetoothButtonListener(WebRtcCallControls.BluetoothButtonListener listener) {
    this.controls.setBluetoothButtonListener(listener);
  }

  public void setHangupButtonListener(final HangupButtonListener listener) {
    endCallButton.setOnClickListener(v -> listener.onClick());
  }

  public void updateAudioState(boolean isBluetoothAvailable, boolean isMicrophoneEnabled) {
    this.controls.updateAudioState(isBluetoothAvailable);
    this.controls.setMicrophoneEnabled(isMicrophoneEnabled);
  }

  public void setControlsEnabled(boolean enabled) {
    this.controls.setControlsEnabled(enabled);
  }

  public void setLocalVideoEnabled(boolean enabled) {
    this.controls.setVideoEnabled(true);
    localMemberLayout.requestLayout();
  }

  public void setRemoteVideoEnabled(boolean enabled) {
//    this.photo.setVisibility(View.INVISIBLE);
    remoteMemberLayout.requestLayout();
//    this.remoteRenderLayout.requestLayout();
    this.remoteRenderLayout2.requestLayout();
    this.remoteRenderLayout3.requestLayout();
  }

  public boolean isVideoEnabled() {
    return controls.isVideoEnabled();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.webrtc_call_screen, this, true);

    this.remoteRenderLayout2          = findViewById(R.id.remote2_render_layout);
    this.remoteRenderLayout3          = findViewById(R.id.remote3_render_layout);
    this.status                       = findViewById(R.id.callStateLabel);
    this.controls                     = findViewById(R.id.inCallControls);
    this.endCallButton                = findViewById(R.id.hangup_fab);
    this.incomingCallButton           = findViewById(R.id.answer_decline_button);
    this.callHeader                   = findViewById(R.id.call_info_1);
    this.localMemberLayout = findViewById(R.id.local_call_member);
    this.remoteMemberLayout = findViewById(R.id.remote_call_member);
    localRecipient = RecipientFactory.getRecipient(getContext(), TextSecurePreferences.getLocalNumber(getContext()), true);

    this.minimized = false;
  }

  private void setConnected(SurfaceViewRenderer localRenderer,
                            SurfaceViewRenderer remoteRenderer, SurfaceViewRenderer remoteRenderer2, SurfaceViewRenderer remoteRenderer3)
  {
    if (localMemberLayout.memberVideo.getChildCount() == 0 && remoteMemberLayout.memberVideo.getChildCount() == 0) {
      if (localRenderer.getParent() != null) {
        ((ViewGroup)localRenderer.getParent()).removeView(localRenderer);
      }

      if (remoteRenderer.getParent() != null) {
        ((ViewGroup)remoteRenderer.getParent()).removeView(remoteRenderer);
      }

      if (remoteRenderer2.getParent() != null) {
        ((ViewGroup)remoteRenderer2.getParent()).removeView(remoteRenderer2);
      }

      if (remoteRenderer3.getParent() != null) {
        ((ViewGroup)remoteRenderer3.getParent()).removeView(remoteRenderer3);
      }
//      localRenderer.setMirror(true);
//      localRenderer.setZOrderMediaOverlay(true);
      localMemberLayout.setActiveCall(localRenderer);
      remoteMemberLayout.setActiveCall(remoteRenderer);
      remoteRenderLayout2.addView(remoteRenderer2);
      remoteRenderLayout3.addView(remoteRenderer3);
    }
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(() -> {
      // Find layout for recipient and update
      remoteMemberLayout.setRecipient(recipient);
    });
  }

  public interface HangupButtonListener {
    void onClick();
  }
}
