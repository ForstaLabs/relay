package io.forsta.securesms.video;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.VideoView;

import io.forsta.securesms.R;
import io.forsta.securesms.attachments.AttachmentServer;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.mms.VideoSlide;
import io.forsta.securesms.util.ViewUtil;

import java.io.IOException;

public class VideoPlayer extends FrameLayout {

  @NonNull  private final VideoView        videoView;
  @Nullable private       AttachmentServer attachmentServer;

  public VideoPlayer(Context context) {
    this(context, null);
  }

  public VideoPlayer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.video_player, this);

    this.videoView = ViewUtil.findById(this, R.id.video_view);

    initializeVideoViewControls(videoView);
  }

  public void setVideoSource(@NonNull MasterSecret masterSecret, @NonNull VideoSlide videoSource) throws IOException {
    if (this.attachmentServer != null) {
      this.attachmentServer.stop();
    }

    this.attachmentServer = new AttachmentServer(getContext(), masterSecret, videoSource.asAttachment());
    this.attachmentServer.start();

    this.videoView.setVideoURI(this.attachmentServer.getUri());
    this.videoView.start();
  }

  public void cleanup() {
    if (this.attachmentServer != null) {
      this.attachmentServer.stop();
    }
  }

  private void initializeVideoViewControls(@NonNull VideoView videoView) {
    MediaController mediaController = new MediaController(getContext());
    mediaController.setAnchorView(videoView);
    mediaController.setMediaPlayer(videoView);

    videoView.setMediaController(mediaController);
  }
}
