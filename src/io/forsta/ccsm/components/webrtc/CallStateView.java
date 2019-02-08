package io.forsta.ccsm.components.webrtc;

import android.content.Context;
import android.net.NetworkInfo;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;

import io.forsta.securesms.R;
import io.forsta.securesms.events.WebRtcViewModel;

public class CallStateView extends LinearLayout {
  private ImageView connectedIndicator;
  private ImageView disconnectedIndictor;
  public CallStateView(Context context) {
    super(context);
    initialize();
  }

  public CallStateView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallStateView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.call_status_view, this);
    connectedIndicator = findViewById(R.id.connected_indicator);
    disconnectedIndictor = findViewById(R.id.disconnected_indicator);
  }

  public void setCallState(WebRtcViewModel.State state) {
    switch (state) {
      case CALL_CONNECTED:
        connectedIndicator.setVisibility(VISIBLE);
        disconnectedIndictor.setVisibility(GONE);
        break;
      case CALL_MEMBER_JOINING:
        connectedIndicator.setVisibility(VISIBLE);
        disconnectedIndictor.setVisibility(GONE);
        break;
      case CALL_MEMBER_VIDEO:
        connectedIndicator.setVisibility(VISIBLE);
        disconnectedIndictor.setVisibility(GONE);
        break;
      default:
        connectedIndicator.setVisibility(GONE);
        disconnectedIndictor.setVisibility(VISIBLE);
    }
  }
}
