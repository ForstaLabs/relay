package io.forsta.ccsm.webrtc;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

import io.forsta.ccsm.components.webrtc.CallStateView;
import io.forsta.securesms.R;
import io.forsta.securesms.components.AvatarImageView;

public class CallMemberListAdapter extends RecyclerView.Adapter<CallMemberListAdapter.CallMemberViewHolder> {

  private Map<Integer, CallRecipient> callRecipients;

  public CallMemberListAdapter(Map<Integer, CallRecipient> callRecipients) {
    if (callRecipients == null) {
      throw new IllegalArgumentException(
          "recipients must not be null");
    }
    this.callRecipients = callRecipients;
  }

  @Override
  public CallMemberViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
        .inflate(R.layout.call_member_list_item, parent, false);
    return new CallMemberViewHolder(v);
  }

  @Override
  public void onBindViewHolder(CallMemberViewHolder holder, int position) {
    CallRecipient callRecipient = callRecipients.get(position + 1);
    holder.recipientName.setText(callRecipient.getRecipient().getName());
    holder.callState.setCallState(callRecipient.getCallState());
    holder.avatar.setAvatar(callRecipient.getRecipient(), false);
  }

  @Override
  public int getItemCount() {
    return callRecipients.size();
  }

  protected static class CallMemberViewHolder extends RecyclerView.ViewHolder {

    private TextView recipientName;
    private CallStateView callState;
    private AvatarImageView avatar;

    public CallMemberViewHolder(View itemView) {
      super(itemView);
      recipientName = (TextView) itemView.findViewById(R.id.call_member_list_name);
      callState = itemView.findViewById(R.id.call_member_call_state);
      avatar = itemView.findViewById(R.id.call_member_list_avatar);
    }
  }
}
