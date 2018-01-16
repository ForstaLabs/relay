package io.forsta.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import io.forsta.securesms.TransportOption;
import io.forsta.securesms.TransportOptions;
import io.forsta.securesms.TransportOptions.OnTransportChangedListener;
import io.forsta.securesms.TransportOptionsPopup;
import io.forsta.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

public class SendButton extends ImageButton
{

  @SuppressWarnings("unused")
  public SendButton(Context context) {
    super(context);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    ViewUtil.mirrorIfRtl(this, getContext());
  }
}
