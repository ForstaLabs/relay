package io.forsta.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.whispersystems.libsignal.util.guava.Optional;

import io.forsta.securesms.R;
import io.forsta.securesms.components.emoji.EmojiDrawer;
import io.forsta.securesms.components.emoji.EmojiEditText;
import io.forsta.securesms.components.emoji.EmojiToggle;
import io.forsta.securesms.mms.QuoteModel;
import io.forsta.securesms.mms.SlideDeck;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.ViewUtil;
import io.forsta.securesms.util.concurrent.AssertedSuccessListener;
import io.forsta.securesms.util.concurrent.ListenableFuture;
import io.forsta.securesms.util.concurrent.SettableFuture;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class InputPanel extends LinearLayout
    implements MicrophoneRecorderView.Listener, KeyboardAwareLinearLayout.OnKeyboardShownListener, EmojiDrawer.EmojiEventListener {

  private static final String TAG = InputPanel.class.getSimpleName();

  private static final int FADE_TIME = 150;

  private QuoteView     quoteView;
  private EmojiToggle   emojiToggle;
  private EmojiEditText composeText;
  private View          quickCameraToggle;
  private View          quickAudioToggle;
  private View          buttonToggle;
  private View          recordingContainer;

  private static ComposeText inputText;

  private MicrophoneRecorderView microphoneRecorderView;
  private SlideToCancel          slideToCancel;
  private RecordTime             recordTime;

  private @Nullable Listener listener;
  private boolean emojiVisible;

  public InputPanel(Context context) {
    super(context);
  }

  public InputPanel(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public InputPanel(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.quoteView              = findViewById(R.id.quote_view);
    this.emojiToggle            = ViewUtil.findById(this, R.id.emoji_toggle);
    this.composeText            = ViewUtil.findById(this, R.id.embedded_text_editor);
    this.quickCameraToggle      = ViewUtil.findById(this, R.id.quick_camera_toggle);
    this.quickAudioToggle       = ViewUtil.findById(this, R.id.quick_audio_toggle);
    this.buttonToggle           = ViewUtil.findById(this, R.id.button_toggle);
    this.recordingContainer     = ViewUtil.findById(this, R.id.recording_container);
    this.recordTime             = new RecordTime((TextView) ViewUtil.findById(this, R.id.record_time));
    this.slideToCancel          = new SlideToCancel(ViewUtil.findById(this, R.id.slide_to_cancel));
    this.microphoneRecorderView = ViewUtil.findById(this, R.id.recorder_view);
    this.microphoneRecorderView.setListener(this);
    inputText = ViewUtil.findById(this, R.id.embedded_text_editor);

//    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      this.microphoneRecorderView.setVisibility(View.GONE);
      this.microphoneRecorderView.setClickable(false);
//    }

    if (TextSecurePreferences.isSystemEmojiPreferred(getContext())) {
      emojiToggle.setVisibility(View.GONE);
      emojiVisible = false;
    } else {
      emojiToggle.setVisibility(View.VISIBLE);
      emojiVisible = true;
    }
  }

  public void setListener(final @NonNull Listener listener, @NonNull EmojiDrawer emojiDrawer) {
    this.listener = listener;

    emojiToggle.attach(emojiDrawer);
    emojiToggle.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        listener.onEmojiToggle();
      }
    });
  }

  public void setQuote(long id, @NonNull Recipient author, @NonNull String body, @NonNull SlideDeck attachments) {
    this.quoteView.setQuote(id, author, body, attachments);
    this.quoteView.setVisibility(View.VISIBLE);
    inputText.setHint(getContext().getString(R.string.conversation_activity__type_reply_push), null);
  }

  public boolean hasQuoteVisible() {
    if(quoteView.getVisibility() == VISIBLE) {
      return true;
    }
    return false;
  }

  public static void returnInputHint() {
    inputText.setHint("Send Forsta message", null);
  }

  public void clearQuote() {
    this.quoteView.dismiss();
  }

  @Override
  public void onRecordPressed(float startPositionX) {
    if (listener != null) listener.onRecorderStarted();
    recordTime.display();
    slideToCancel.display(startPositionX);

    if (emojiVisible) ViewUtil.fadeOut(emojiToggle, FADE_TIME, View.INVISIBLE);
    ViewUtil.fadeOut(composeText, FADE_TIME, View.INVISIBLE);
    ViewUtil.fadeOut(quickCameraToggle, FADE_TIME, View.INVISIBLE);
    ViewUtil.fadeOut(quickAudioToggle, FADE_TIME, View.INVISIBLE);
    ViewUtil.fadeOut(buttonToggle, FADE_TIME, View.INVISIBLE);
  }

  @Override
  public void onRecordReleased(float x) {
    long elapsedTime = onRecordHideEvent(x);

    if (listener != null) {
      Log.w(TAG, "Elapsed time: " + elapsedTime);
      if (elapsedTime > 1000) {
        listener.onRecorderFinished();
      } else {
        Toast.makeText(getContext(), R.string.InputPanel_tap_and_hold_to_record_a_voice_message_release_to_send, Toast.LENGTH_LONG).show();
        listener.onRecorderCanceled();
      }
    }
  }

  @Override
  public void onRecordMoved(float x, float absoluteX) {
    slideToCancel.moveTo(x);

    if (absoluteX / recordingContainer.getWidth() <= 0.5) {
      this.microphoneRecorderView.cancelAction();
    }
  }

  @Override
  public void onRecordCanceled(float x) {
    onRecordHideEvent(x);
    if (listener != null) listener.onRecorderCanceled();
  }

  public void onPause() {
    this.microphoneRecorderView.cancelAction();
  }

  public void setEnabled(boolean enabled) {
    composeText.setEnabled(enabled);
    emojiToggle.setEnabled(enabled);
    quickAudioToggle.setEnabled(enabled);
    quickCameraToggle.setEnabled(enabled);
  }

  private long onRecordHideEvent(float x) {
    ListenableFuture<Void> future      = slideToCancel.hide(x);
    long                   elapsedTime = recordTime.hide();

    future.addListener(new AssertedSuccessListener<Void>() {
      @Override
      public void onSuccess(Void result) {
        if (emojiVisible) ViewUtil.fadeIn(emojiToggle, FADE_TIME);
        ViewUtil.fadeIn(composeText, FADE_TIME);
        ViewUtil.fadeIn(quickCameraToggle, FADE_TIME);
        ViewUtil.fadeIn(quickAudioToggle, FADE_TIME);
        ViewUtil.fadeIn(buttonToggle, FADE_TIME);
      }
    });

    return elapsedTime;
  }

  @Override
  public void onKeyboardShown() {
    emojiToggle.setToEmoji();
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
    composeText.dispatchKeyEvent(keyEvent);
  }

  @Override
  public void onEmojiSelected(String emoji) {
    composeText.insertEmoji(emoji);
  }

  public interface Listener {
    public void onRecorderStarted();
    public void onRecorderFinished();
    public void onRecorderCanceled();
    public void onEmojiToggle();
  }

  private static class SlideToCancel {

    private final View slideToCancelView;

    private float startPositionX;

    public SlideToCancel(View slideToCancelView) {
      this.slideToCancelView = slideToCancelView;
    }

    public void display(float startPositionX) {
      this.startPositionX = startPositionX;
      ViewUtil.fadeIn(this.slideToCancelView, FADE_TIME);
    }

    public ListenableFuture<Void> hide(float x) {
      final SettableFuture<Void> future = new SettableFuture<>();
      float offset = -Math.max(0, this.startPositionX - x);

      AnimationSet animation = new AnimationSet(true);
      animation.addAnimation(new TranslateAnimation(Animation.ABSOLUTE, offset,
                                                    Animation.ABSOLUTE, 0,
                                                    Animation.RELATIVE_TO_SELF, 0,
                                                    Animation.RELATIVE_TO_SELF, 0));
      animation.addAnimation(new AlphaAnimation(1, 0));

      animation.setDuration(MicrophoneRecorderView.ANIMATION_DURATION);
      animation.setFillBefore(true);
      animation.setFillAfter(false);
      animation.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}
        @Override
        public void onAnimationEnd(Animation animation) {
          future.set(null);
        }
        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      slideToCancelView.setVisibility(View.GONE);
      slideToCancelView.startAnimation(animation);

      return future;
    }

    public void moveTo(float x) {
      float     offset    = -Math.max(0, this.startPositionX - x);
      Animation animation = new TranslateAnimation(Animation.ABSOLUTE, offset,
                                                   Animation.ABSOLUTE, offset,
                                                   Animation.RELATIVE_TO_SELF, 0,
                                                   Animation.RELATIVE_TO_SELF, 0);

      animation.setDuration(0);
      animation.setFillAfter(true);
      animation.setFillBefore(true);

      slideToCancelView.startAnimation(animation);
    }

  }

  private static class RecordTime implements Runnable {

    private final TextView recordTimeView;

    private final AtomicLong startTime = new AtomicLong(0);
    private final Handler    handler   = new Handler();

    private RecordTime(TextView recordTimeView) {
      this.recordTimeView = recordTimeView;
    }

    public void display() {
      this.startTime.set(System.currentTimeMillis());
      this.recordTimeView.setText("00:00");
      ViewUtil.fadeIn(this.recordTimeView, FADE_TIME);
      handler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
    }

    public long hide() {
      long elapsedtime = System.currentTimeMillis() - startTime.get();
      this.startTime.set(0);
      ViewUtil.fadeOut(this.recordTimeView, FADE_TIME, View.INVISIBLE);
      return elapsedtime;
    }

    @Override
    public void run() {
      long localStartTime = startTime.get();
      if (localStartTime > 0) {
        long elapsedTime = System.currentTimeMillis() - localStartTime;
        recordTimeView.setText(String.format("%02d:%02d",
                                             TimeUnit.MILLISECONDS.toMinutes(elapsedTime),
                                             TimeUnit.MILLISECONDS.toSeconds(elapsedTime) -
                                             TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsedTime))));
        handler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
      }
    }
  }
}
