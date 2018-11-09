package io.forsta.ccsm.components.webrtc;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoRenderer;

import io.forsta.securesms.R;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.events.WebRtcViewModel;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.service.WebRtcCallService;
import io.forsta.securesms.util.Util;

public class CallMemberView extends LinearLayout implements Recipient.RecipientModifiedListener {
  private static final String TAG = CallMemberView.class.getSimpleName();

  private Recipient recipient;
  private TextView memberName;
  private TextView callMemberStatus;
  private AvatarImageView memberAvatar;
  public FrameLayout memberVideo;

  public CallMemberView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallMemberView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public CallMemberView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public CallMemberView(@NonNull Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.call_member_view, this, true);

    memberName = (TextView) findViewById(R.id.call_member_name);
    callMemberStatus = (TextView) findViewById(R.id.call_member_status);
    memberAvatar = (AvatarImageView) findViewById(R.id.call_member_avatar);
    memberVideo = (FrameLayout) findViewById(R.id.call_member_video);
  }

  public void setRecipient(Recipient recipient) {
    this.recipient = recipient;
    memberName.setText(recipient.getName());
    memberAvatar.setAvatar(recipient, false);
  }

  public void setActiveCall(SurfaceViewRenderer renderer) {
    memberVideo.addView(renderer);
    memberVideo.setVisibility(VISIBLE);
    memberAvatar.setVisibility(GONE);
  }

  public void setCallStatus(String status) {
    callMemberStatus.setText(status);
  }

  @Override
  public void onModified(Recipient recipient) {
    Log.w(TAG, "Recipient modified");
    Util.runOnMain(() -> {
        this.recipient = recipient;
        memberName.setText(recipient.getName());
        memberAvatar.setAvatar(recipient, false);
    });
  }
}
