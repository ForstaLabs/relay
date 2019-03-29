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
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import io.forsta.ccsm.webrtc.CallMemberListAdapter;
import io.forsta.ccsm.webrtc.CallRecipient;
import io.forsta.securesms.R;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.service.WebRtcCallService;
import io.forsta.securesms.util.TextSecurePreferences;
import org.webrtc.SurfaceViewRenderer;
import java.util.HashMap;
import java.util.Map;

/**
 * A UI widget that encapsulates the entire in-call screen
 * for both initiators and responders.
 *
 * @author Moxie Marlinspike
 *
 */
public class WebRtcCallScreen extends FrameLayout {

  @SuppressWarnings("unused")
  private static final String TAG = WebRtcCallScreen.class.getSimpleName();

  private ImageButton endCallButton;
  private ImageButton callMembersButton;
  private WebRtcCallControls   controls;
  private ViewGroup            callHeader;

  private WebRtcAnswerDeclineButton incomingCallButton;

  private Recipient localRecipient;
  private FrameLayout localMemberLayout;
  private FrameLayout remoteMemberLayout;
  private Map<Integer, CallRecipient> remoteCallMembers = new HashMap<>();
  private RecyclerView remoteCallMemberList;
  private CallMemberListAdapter callMemberListAdapter;
  private CallMemberListAdapter.ItemClickListener callMemberClickListener;

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

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.webrtc_call_screen, this, true);

    this.localMemberLayout = findViewById(R.id.local_call_member);
    this.remoteMemberLayout = findViewById(R.id.remote_call_member);
    this.controls                     = findViewById(R.id.inCallControls);
    this.endCallButton                = findViewById(R.id.hangup_button);
    this.incomingCallButton           = findViewById(R.id.answer_decline_button);
    this.callHeader                   = findViewById(R.id.call_info_1);
    this.remoteCallMemberList = findViewById(R.id.call_member_list_recyclerview);
    this.callMembersButton = findViewById(R.id.call_members_button);

    localRecipient = RecipientFactory.getRecipient(getContext(), TextSecurePreferences.getLocalNumber(getContext()), true);

    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
    remoteCallMemberList.setLayoutManager(layoutManager);
    callMembersButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (remoteCallMemberList.getVisibility() == GONE) {
          remoteCallMemberList.setVisibility(VISIBLE);
        } else {
          remoteCallMemberList.setVisibility(GONE);
        }
      }
    });
  }

  public CallRecipient getCallRecipient(int callOrder) {
    return remoteCallMembers.get(callOrder);
  }

  public void setActiveCall(@NonNull CallRecipient callRecipient, int callOrder) {
    updateCallMember(callRecipient, callOrder);
    setConnected(WebRtcCallService.localRenderer, WebRtcCallService.remoteRenderer);
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    localMemberLayout.setVisibility(VISIBLE);
    endCallButton.setVisibility(VISIBLE);
  }

  public void setCallAnswering() {
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    localMemberLayout.setVisibility(VISIBLE);
  }

  public void updateVideoSelection(@NonNull CallRecipient callRecipient, int callOrder) {
    for (CallRecipient recipient : remoteCallMembers.values()) {
      recipient.setVideoEnabled(false);
    }
    remoteCallMembers.put(callOrder, callRecipient);
    remoteCallMemberList.getAdapter().notifyDataSetChanged();
  }

  public void updateCallMember(@NonNull CallRecipient callRecipient, int callOrder) {
    remoteCallMembers.put(callOrder, callRecipient);
    if (remoteCallMemberList.getAdapter() != null) {
      remoteCallMemberList.getAdapter().notifyDataSetChanged();
    } else {
      callMemberListAdapter = new CallMemberListAdapter(remoteCallMembers);
      remoteCallMemberList.setAdapter(callMemberListAdapter);
      if (callMemberClickListener != null) {
        callMemberListAdapter.setItemClickListener(callMemberClickListener);
      }
    }
  }

  public void setOutgoingCall() {
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.setVisibility(VISIBLE);
  }

  public void setOutgoingCall(CallRecipient callRecipient, int callOrder, Map<Integer, CallRecipient> remoteCallRecipients) {
    remoteCallMembers = remoteCallRecipients;
    incomingCallButton.stopRingingAnimation();
    incomingCallButton.setVisibility(View.GONE);
    endCallButton.setVisibility(VISIBLE);
    updateCallMember(callRecipient, callOrder);
    callMemberListAdapter = new CallMemberListAdapter(remoteCallMembers);
    if (callMemberClickListener != null) {
      callMemberListAdapter.setItemClickListener(callMemberClickListener);
    }
    remoteCallMemberList.setAdapter(callMemberListAdapter);
  }

  public void setIncomingCall(CallRecipient recipient, int callOrder, Map<Integer, CallRecipient> remoteCallRecipients) {
    remoteCallMembers = remoteCallRecipients;
    updateCallMember(recipient, callOrder);
    endCallButton.setVisibility(View.INVISIBLE);
    incomingCallButton.setVisibility(View.VISIBLE);
    incomingCallButton.startRingingAnimation();
    callMemberListAdapter = new CallMemberListAdapter(remoteCallMembers);
    if (callMemberClickListener != null) {
      callMemberListAdapter.setItemClickListener(callMemberClickListener);
    }
    remoteCallMemberList.setAdapter(callMemberListAdapter);
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

  public void setCallRecipientsClickListener(CallMemberListAdapter.ItemClickListener listener) {
    callMemberClickListener = listener;
  }

  public void updateAudioState(boolean isBluetoothAvailable, boolean isMicrophoneEnabled) {
    this.controls.updateAudioState(isBluetoothAvailable);
    this.controls.setMicrophoneEnabled(isMicrophoneEnabled);
  }

  public void setControlsEnabled(boolean enabled) {
    this.controls.setControlsEnabled(enabled);
  }

  public void setLocalVideoEnabled(boolean enabled) {
    this.controls.setVideoEnabled(enabled);
    View video = localMemberLayout.getChildAt(0);
    if (video != null) {
      if (!enabled) {
        video.setVisibility(INVISIBLE);
      } else {
        video.setVisibility(VISIBLE);
      }
    }
    localMemberLayout.requestLayout();
  }

  public void setRemoteVideoEnabled(boolean enabled) {
    remoteMemberLayout.requestLayout();
  }

  public boolean isVideoEnabled() {
    return controls.isVideoEnabled();
  }

  private void setConnected(SurfaceViewRenderer localRenderer,
                            SurfaceViewRenderer remoteRenderer)
  {
    if (localMemberLayout.getChildCount() == 0 && remoteMemberLayout.getChildCount() == 0) {
      if (localRenderer.getParent() != null) {
        ((ViewGroup)localRenderer.getParent()).removeView(localRenderer);
      }

      if (remoteRenderer.getParent() != null) {
        ((ViewGroup)remoteRenderer.getParent()).removeView(remoteRenderer);
      }

      localRenderer.setMirror(true);
      localRenderer.setZOrderMediaOverlay(true);
      localMemberLayout.addView(localRenderer);
      localMemberLayout.setVisibility(VISIBLE);
      remoteMemberLayout.addView(remoteRenderer);
    }
  }

  public interface HangupButtonListener {
    void onClick();
  }
}
