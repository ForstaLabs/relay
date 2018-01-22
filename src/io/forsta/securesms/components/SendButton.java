package io.forsta.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

import io.forsta.securesms.util.ViewUtil;

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
