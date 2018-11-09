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

  private ImageView            photo;
  private FrameLayout   localRenderLayout;
  private FrameLayout   remoteRenderLayout;
  private FrameLayout   remoteRenderLayout2;
  private FrameLayout   remoteRenderLayout3;
  private TextView             name;
  private TextView             elapsedTime;
  private TextView             status;
  private FloatingActionButton endCallButton;
  private WebRtcCallControls   controls;
  private RelativeLayout       expandedInfo;
  private ViewGroup            callHeader;

  private WebRtcAnswerDeclineButton incomingCallButton;

  private Recipient recipient;
  private Recipient localRecipient;
  private boolean   minimized;
  private CallMemberView localMemberLayout;

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
    setCard(personInfo, message);
    setConnected(WebRtcCallService.localRenderer, WebRtcCallService.remoteRenderer, WebRtcCallService.remoteRenderer2, WebRtcCallService.remoteRenderer3);
    localMemberLayout.setCallStatus("Connected");
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.show();
  }

  public void setActiveCall(@NonNull Recipient personInfo, @NonNull String message) {
    setCard(personInfo, message);
    localMemberLayout.setRecipient(localRecipient);
    localMemberLayout.setCallStatus("Connected");
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.show();
  }

  public void setIncomingCall(Recipient personInfo) {
    setCard(personInfo, getContext().getString(R.string.CallScreen_Incoming_call));
    localMemberLayout.setRecipient(localRecipient);
    localMemberLayout.setCallStatus("Incoming call");
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
//    if (enabled && this.localRenderLayout.isHidden()) {
//      this.controls.setVideoEnabled(true);
//      this.localRenderLayout.setHidden(false);
//      this.localRenderLayout.requestLayout();
//    } else  if (!enabled && !this.localRenderLayout.isHidden()){
//      this.controls.setVideoEnabled(false);
//      this.localRenderLayout.setHidden(true);
//      this.localRenderLayout.requestLayout();
//    }
    this.controls.setVideoEnabled(true);
//    this.localRenderLayout.requestLayout();
    localMemberLayout.requestLayout();
  }

  public void setRemoteVideoEnabled(boolean enabled) {
//    if (enabled && this.remoteRenderLayout.isHidden()) {
//      this.photo.setVisibility(View.INVISIBLE);
//      setMinimized(true);
//
//      this.remoteRenderLayout.setHidden(false);
//      this.remoteRenderLayout.requestLayout();
//
//      if (localRenderLayout.isHidden()) this.controls.displayVideoTooltip(callHeader);
//    } else if (!enabled && !this.remoteRenderLayout.isHidden()){
//      setMinimized(false);
//      this.photo.setVisibility(View.VISIBLE);
//      this.remoteRenderLayout.setHidden(true);
//      this.remoteRenderLayout.requestLayout();
//    }

    this.photo.setVisibility(View.INVISIBLE);
    this.remoteRenderLayout.requestLayout();
    this.remoteRenderLayout2.requestLayout();
    this.remoteRenderLayout3.requestLayout();
  }

  public boolean isVideoEnabled() {
    return controls.isVideoEnabled();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.webrtc_call_screen, this, true);

    this.elapsedTime                  = findViewById(R.id.elapsedTime);
    this.photo                        = findViewById(R.id.photo);
//    this.localRenderLayout            = findViewById(R.id.local_render_layout);
    this.remoteRenderLayout           = findViewById(R.id.remote_render_layout);
    this.remoteRenderLayout2          = findViewById(R.id.remote2_render_layout);
    this.remoteRenderLayout3          = findViewById(R.id.remote3_render_layout);
    this.name                         = findViewById(R.id.name);
    this.status                       = findViewById(R.id.callStateLabel);
    this.controls                     = findViewById(R.id.inCallControls);
    this.endCallButton                = findViewById(R.id.hangup_fab);
    this.incomingCallButton           = findViewById(R.id.answer_decline_button);
    this.expandedInfo                 = findViewById(R.id.expanded_info);
    this.callHeader                   = findViewById(R.id.call_info_1);
    this.localMemberLayout = findViewById(R.id.local_call_member);
    localRecipient = RecipientFactory.getRecipient(getContext(), TextSecurePreferences.getLocalNumber(getContext()), true);

//    this.localRenderLayout.setHidden(true);
//    this.remoteRenderLayout.setHidden(true);
    this.minimized = false;

//    this.remoteRenderLayout.setOnClickListener(v -> setMinimized(!minimized));
  }

  private void setConnected(SurfaceViewRenderer localRenderer,
                            SurfaceViewRenderer remoteRenderer, SurfaceViewRenderer remoteRenderer2, SurfaceViewRenderer remoteRenderer3)
  {
//    if (localRenderLayout.getChildCount() == 0 && remoteRenderLayout.getChildCount() == 0) {
    if (localMemberLayout.memberVideo.getChildCount() == 0 && remoteRenderLayout.getChildCount() == 0) {
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
//      localRenderLayout.addView(localRenderer);
      localMemberLayout.setActiveCall(localRenderer);
      remoteRenderLayout.addView(remoteRenderer);
      remoteRenderLayout2.addView(remoteRenderer2);
      remoteRenderLayout3.addView(remoteRenderer3);
    }
  }

  private void setPersonInfo(final @NonNull Recipient recipient) {
    this.recipient = recipient;
    this.recipient.addListener(this);

    if (!TextUtils.isEmpty(recipient.getGravitarUrl())) {
      Glide.with(getContext().getApplicationContext()).load(recipient.getGravitarUrl()).asBitmap()
          .placeholder(ContactPhotoFactory.getDefaultContactPhoto(recipient.getName()).asDrawable(getContext().getApplicationContext(), recipient.getColor().toConversationColor(getContext().getApplicationContext()), false))
          .into(this.photo);
    }

    this.name.setText(recipient.getName());
  }

  private void setCard(Recipient recipient, String status) {
    setPersonInfo(recipient);
    this.status.setText(status);
  }

  private void setMinimized(boolean minimized) {
    if (minimized) {
      ViewCompat.animate(callHeader).translationY(-1 * expandedInfo.getHeight());
      ViewCompat.animate(status).alpha(0);
      ViewCompat.animate(endCallButton).translationY(endCallButton.getHeight() + ViewUtil.dpToPx(getContext(), 40));
      ViewCompat.animate(endCallButton).alpha(0);

      this.minimized = true;
    } else {
      ViewCompat.animate(callHeader).translationY(0);
      ViewCompat.animate(status).alpha(1);
      ViewCompat.animate(endCallButton).translationY(0);
      ViewCompat.animate(endCallButton).alpha(1).withEndAction(() -> {
        // Note: This is to work around an Android bug, see #6225
        endCallButton.requestLayout();
      });

      this.minimized = false;
    }
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(() -> {
      if (recipient == WebRtcCallScreen.this.recipient) {
        setPersonInfo(recipient);
      }
    });
  }

  public interface HangupButtonListener {
    void onClick();
  }
}
