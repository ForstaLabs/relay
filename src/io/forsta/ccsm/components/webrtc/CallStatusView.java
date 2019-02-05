package io.forsta.ccsm.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import io.forsta.securesms.R;

public class CallStatusView extends LinearLayout {
  public CallStatusView(Context context) {
    super(context);
    initialize();
  }

  public CallStatusView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallStatusView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.call_status_view, this);
  }
}
