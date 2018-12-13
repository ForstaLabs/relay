package io.forsta.ccsm.webrtc;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

public class CallMemberListAdapter extends RecyclerView.Adapter<CallMemberListAdapter.CallMemberViewHolder> {
  @Override
  public CallMemberViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    return null;
  }

  @Override
  public void onBindViewHolder(CallMemberViewHolder holder, int position) {

  }

  @Override
  public int getItemCount() {
    return 0;
  }

  protected static class CallMemberViewHolder extends RecyclerView.ViewHolder {

    public CallMemberViewHolder(View itemView) {
      super(itemView);
    }
  }
}
