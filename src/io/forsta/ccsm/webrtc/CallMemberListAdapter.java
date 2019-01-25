package io.forsta.ccsm.webrtc;

import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collection;
import java.util.Map;

import io.forsta.securesms.R;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;

public class CallMemberListAdapter extends RecyclerView.Adapter<CallMemberListAdapter.CallMemberViewHolder> {

  private Map<Integer, Recipient> callRecipients;

  public CallMemberListAdapter(Map<Integer, Recipient> callRecipients) {
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
    Recipient recipient = callRecipients.get(position);
    holder.recipientName.setText(recipient.getName());
    holder.callStatus.setText("Idle");
    holder.avatar.setAvatar(recipient, false);
  }

  @Override
  public int getItemCount() {
    return callRecipients.size();
  }

  protected static class CallMemberViewHolder extends RecyclerView.ViewHolder {

    private TextView recipientName;
    private TextView callStatus;
    private AvatarImageView avatar;

    public CallMemberViewHolder(View itemView) {
      super(itemView);
      recipientName = (TextView) itemView.findViewById(R.id.call_member_list_name);
      callStatus = (TextView) itemView.findViewById(R.id.call_member_list_status);
      avatar = itemView.findViewById(R.id.call_member_list_avatar);
    }
  }
}
